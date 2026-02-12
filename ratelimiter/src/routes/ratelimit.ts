import { Router } from "express";
import { prisma } from "../db";
import { redis } from "../redis";
import { badRequest, unauthorized } from "../utils/errors";
import { maskApiKey } from "../utils/apiKey";
import { matchesPattern, methodWeight } from "../utils/match";
import {
  runFixedWindow,
  runLeakyBucket,
  runSlidingWindow,
  runTokenBucket,
} from "../rate-limit/strategies";

export const rateLimitRouter = Router();

const validateApiKey = async (apiKey: string) => {
  if (!apiKey) {
    throw unauthorized("Invalid API key");
  }

  const now = new Date();
  const record = await prisma.apiKey.findFirst({
    where: {
      apiKey,
      isActive: true,
      OR: [{ expiresAt: null }, { expiresAt: { gt: now } }],
    },
    include: { tenant: true },
  });

  if (!record) {
    throw unauthorized("API key not found or expired");
  }

  return record;
};

const findMatchingConfig = async (
  tenantId: string,
  endpoint: string,
  method: string
) => {
  const exact = await prisma.rateLimitConfig.findFirst({
    where: {
      tenantId,
      endpointPattern: endpoint,
      isActive: true,
      httpMethod: method,
    },
    orderBy: { httpMethod: "desc" },
  });
  if (exact) {
    return exact;
  }

  const exactWildcard = await prisma.rateLimitConfig.findFirst({
    where: {
      tenantId,
      endpointPattern: endpoint,
      isActive: true,
      httpMethod: "*",
    },
  });
  if (exactWildcard) {
    return exactWildcard;
  }

  const configs = await prisma.rateLimitConfig.findMany({
    where: {
      tenantId,
      isActive: true,
      httpMethod: { in: [method, "*"] },
    },
  });

  const matching = configs
    .filter((config) => matchesPattern(config.endpointPattern, endpoint))
    .sort((a, b) => {
      const lengthDiff = b.endpointPattern.length - a.endpointPattern.length;
      if (lengthDiff !== 0) {
        return lengthDiff;
      }
      const methodDiff = methodWeight(method, b.httpMethod) - methodWeight(method, a.httpMethod);
      if (methodDiff !== 0) {
        return methodDiff;
      }
      return a.endpointPattern.localeCompare(b.endpointPattern);
    });

  return matching[0] || null;
};

const buildRateLimitKey = (apiKey: string, endpoint: string, method: string) =>
  `${apiKey}:${endpoint}:${method}`;

rateLimitRouter.post("/check", async (req, res, next) => {
  try {
    const apiKey = req.header("X-API-Key") || "";
    const { endpoint, method } = req.body || {};
    if (!endpoint) {
      throw badRequest("Endpoint is required");
    }

    const record = await validateApiKey(apiKey);
    const tenantId = record.tenantId;

    const config = await findMatchingConfig(tenantId, endpoint, method || "GET");
    if (!config) {
      return res.json({
        allowed: true,
        remaining: 0,
        limit: 0,
        resetAt: 0,
        message: "No rate limit config matched",
        algorithm: "NONE",
        matchedPattern: null,
      });
    }

    const key = buildRateLimitKey(apiKey, endpoint, method || "GET");
    const redisKey = `ratelimit:${config.algorithm.toLowerCase()}:${key}`;

    let result;
    try {
      switch (config.algorithm) {
        case "FIXED_WINDOW":
          result = await runFixedWindow(redisKey, config.maxRequests, config.windowSeconds);
          break;
        case "SLIDING_WINDOW":
          result = await runSlidingWindow(redisKey, config.maxRequests, config.windowSeconds);
          break;
        case "LEAKY_BUCKET":
          result = await runLeakyBucket(redisKey, config.maxRequests, config.windowSeconds);
          break;
        case "TOKEN_BUCKET":
        default:
          result = await runTokenBucket(
            redisKey,
            config.maxRequests,
            config.refillRate || config.maxRequests / config.windowSeconds,
            config.windowSeconds
          );
          break;
      }
    } catch (error) {
      result = {
        allowed: true,
        remaining: config.maxRequests,
        resetAt: Math.floor(Date.now() / 1000) + config.windowSeconds,
      };
    }

    const response = {
      allowed: result.allowed,
      remaining: result.remaining,
      limit: config.maxRequests,
      resetAt: result.resetAt,
      message: result.allowed
        ? "Request allowed"
        : `Rate limit exceeded. Try again in ${Math.max(1, result.resetAt - Math.floor(Date.now() / 1000))} seconds.`,
      algorithm: config.algorithm,
      matchedPattern: config.endpointPattern,
    };

    await prisma.rateLimitLog.create({
      data: {
        tenantId,
        endpointId: config.endpointId,
        configId: config.id,
        timestamp: new Date(),
        identifierType: config.limitBy,
        clientIdentifier: config.limitBy === "API_KEY" ? record.apiKey : "unknown",
        allowed: response.allowed,
        maxRequests: config.maxRequests,
        currentCount: Math.max(0, config.maxRequests - response.remaining),
        algorithm: config.algorithm,
        httpMethod: method || "GET",
        requestPath: endpoint,
      },
    });

    if (response.allowed) {
      return res
        .status(200)
        .set({
          "X-RateLimit-Limit": String(response.limit),
          "X-RateLimit-Remaining": String(response.remaining),
          "X-RateLimit-Reset": String(response.resetAt),
          "X-RateLimit-Algorithm": response.algorithm,
        })
        .json(response);
    }

    return res
      .status(429)
      .set({
        "X-RateLimit-Limit": String(response.limit),
        "X-RateLimit-Remaining": String(response.remaining),
        "X-RateLimit-Reset": String(response.resetAt),
        "X-RateLimit-Algorithm": response.algorithm,
      })
      .json(response);
  } catch (error) {
    return next(error);
  }
});

rateLimitRouter.get("/status", async (req, res, next) => {
  try {
    const apiKey = req.header("X-API-Key") || "";
    const record = await validateApiKey(apiKey);

    const configs = await prisma.rateLimitConfig.findMany({
      where: { tenantId: record.tenantId, isActive: true },
    });

    return res.json({
      apiKey: maskApiKey(apiKey),
      tenant: record.tenant.name,
      tier: record.tenant.tier,
      configCount: configs.length,
      configs: configs.map((config) => ({
        pattern: config.endpointPattern,
        method: config.httpMethod,
        limit: config.maxRequests,
        windowSeconds: config.windowSeconds,
        algorithm: config.algorithm,
      })),
    });
  } catch (error) {
    return next(error);
  }
});

rateLimitRouter.post("/reset", async (req, res, next) => {
  try {
    const apiKey = req.header("X-API-Key") || "";
    const { endpoint, method } = req.body || {};
    if (!endpoint) {
      throw badRequest("Endpoint is required");
    }

    const record = await validateApiKey(apiKey);
    const config = await findMatchingConfig(record.tenantId, endpoint, method || "GET");
    if (config) {
      const key = buildRateLimitKey(apiKey, endpoint, method || "GET");
      const redisKey = `ratelimit:${config.algorithm.toLowerCase()}:${key}`;
      try {
        await redis.del(redisKey);
      } catch {
        // Ignore reset errors
      }
    }

    return res.json({
      message: "Rate limit reset successfully",
      endpoint,
      method: method || "GET",
    });
  } catch (error) {
    return next(error);
  }
});

export default rateLimitRouter;
