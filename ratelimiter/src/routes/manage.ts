import { Router } from "express";
import { prisma } from "../db";
import { requireAuth } from "../middleware/auth";
import {
  badRequest,
  notFound,
  unauthorized,
} from "../utils/errors";
import { generateApiKey, maskApiKey } from "../utils/apiKey";
import { formatIst } from "../utils/time";
import crypto from "crypto";

export const manageRouter = Router();

manageRouter.use(requireAuth);

const ensureTenantAccess = async (userId: string, tenantId: string) => {
  const user = await prisma.user.findUnique({ where: { id: userId } });
  if (!user?.tenantId || user.tenantId !== tenantId) {
    throw unauthorized("Unauthorized");
  }
  return user;
};

const ensureEndpointAccess = async (userId: string, endpointId: string) => {
  const endpoint = await prisma.endpoint.findUnique({
    where: { id: endpointId },
  });
  if (!endpoint) {
    throw notFound("Endpoint not found");
  }

  await ensureTenantAccess(userId, endpoint.tenantId);
  return endpoint;
};

const buildFullUrl = (basePath: string, path: string) => {
  const cleanBase = basePath.endsWith("/")
    ? basePath.slice(0, -1)
    : basePath;
  const cleanPath = path.startsWith("/") ? path : `/${path}`;
  return `${cleanBase}${cleanPath}`;
};

const toConfigResponse = (config: {
  id: string;
  endpointPattern: string;
  httpMethod: string;
  maxRequests: number;
  windowSeconds: number;
  algorithm: string;
  limitBy: string;
  customHeader: string | null;
  refillRate: number | null;
  isActive: boolean;
  createdAt: Date;
}) => ({
  id: config.id,
  endpointPattern: config.endpointPattern,
  httpMethod: config.httpMethod,
  maxRequests: config.maxRequests,
  windowSeconds: config.windowSeconds,
  algorithm: config.algorithm,
  limitBy: config.limitBy,
  customHeader: config.customHeader,
  refillRate: config.refillRate,
  isActive: config.isActive,
  createdAt: formatIst(config.createdAt),
});

const getEndpointStats = async (endpointId: string) => {
  const now = new Date();
  const last24h = new Date(now.getTime() - 24 * 60 * 60 * 1000);
  const [totalRequests, blockedRequests] = await Promise.all([
    prisma.rateLimitLog.count({
      where: { endpointId, timestamp: { gte: last24h, lte: now } },
    }),
    prisma.rateLimitLog.count({
      where: {
        endpointId,
        timestamp: { gte: last24h, lte: now },
        allowed: false,
      },
    }),
  ]);
  const blockRate = totalRequests > 0 ? (blockedRequests / totalRequests) * 100 : 0;

  return {
    totalRequests,
    blockedRequests,
    blockRate: Math.round(blockRate * 100) / 100,
    lastRequestAt: formatIst(now),
  };
};

const buildEndpointResponse = async (
  endpoint: {
    id: string;
    basePath: string;
    path: string;
    httpMethod: string;
    description: string | null;
    backendLinkId: string | null;
    isActive: boolean;
    createdAt: Date;
    updatedAt: Date;
  },
  includeStats: boolean
) => {
  const [configs, backendLink] = await Promise.all([
    prisma.rateLimitConfig.findMany({
      where: { endpointId: endpoint.id },
      orderBy: { createdAt: "desc" },
    }),
    endpoint.backendLinkId
      ? prisma.backendLink.findUnique({
          where: { id: endpoint.backendLinkId },
        })
      : Promise.resolve(null),
  ]);

  const response = {
    id: endpoint.id,
    basePath: endpoint.basePath,
    path: endpoint.path,
    httpMethod: endpoint.httpMethod,
    description: endpoint.description,
    backendLinkId: endpoint.backendLinkId,
    backendLinkName: backendLink?.nickname || null,
    backendLinkColor: backendLink?.accentColor || null,
    fullUrl: buildFullUrl(endpoint.basePath, endpoint.path),
    isActive: endpoint.isActive,
    createdAt: formatIst(endpoint.createdAt),
    updatedAt: formatIst(endpoint.updatedAt),
    rateLimitConfigs: configs.map(toConfigResponse),
    stats: includeStats ? await getEndpointStats(endpoint.id) : null,
  };

  return response;
};

manageRouter.post("/tenants/:tenantId/endpoints", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const { backendLinkId, path, httpMethod, description } = req.body || {};
    if (!backendLinkId) {
      throw badRequest("Backend link is required");
    }
    if (!path) {
      throw badRequest("Path is required");
    }

    const backendLink = await prisma.backendLink.findUnique({
      where: { id: backendLinkId },
    });
    if (!backendLink || backendLink.userId !== userId) {
      throw badRequest("Backend link not found");
    }
    if (!backendLink.isVerified) {
      throw badRequest("Backend link is not verified");
    }

    const endpoint = await prisma.endpoint.create({
      data: {
        tenantId,
        backendLinkId: backendLink.id,
        basePath: backendLink.backendUrl,
        path,
        httpMethod: httpMethod || "*",
        description: description || null,
        isActive: true,
      },
    });

    return res.json(await buildEndpointResponse(endpoint, true));
  } catch (error) {
    return next(error);
  }
});

manageRouter.get("/tenants/:tenantId/endpoints", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const includeStats = req.query.includeStats === "true";
    const endpoints = await prisma.endpoint.findMany({
      where: { tenantId },
      orderBy: { createdAt: "desc" },
    });

    const responses = await Promise.all(
      endpoints.map((endpoint) => buildEndpointResponse(endpoint, includeStats))
    );

    return res.json(responses);
  } catch (error) {
    return next(error);
  }
});

manageRouter.get("/endpoints/:endpointId", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { endpointId } = req.params;
    const includeStats = req.query.includeStats !== "false";
    const endpoint = await ensureEndpointAccess(userId, endpointId);

    return res.json(await buildEndpointResponse(endpoint, includeStats));
  } catch (error) {
    return next(error);
  }
});

manageRouter.put("/endpoints/:endpointId", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { endpointId } = req.params;
    const endpoint = await ensureEndpointAccess(userId, endpointId);

    const { backendLinkId, path, httpMethod, description } = req.body || {};
    if (!backendLinkId || !path || !httpMethod) {
      throw badRequest("backendLinkId, path, and httpMethod are required");
    }

    const backendLink = await prisma.backendLink.findUnique({
      where: { id: backendLinkId },
    });
    if (!backendLink || backendLink.userId !== userId) {
      throw badRequest("Backend link not found");
    }
    if (!backendLink.isVerified) {
      throw badRequest("Backend link is not verified");
    }

    const updated = await prisma.endpoint.update({
      where: { id: endpoint.id },
      data: {
        backendLinkId: backendLink.id,
        basePath: backendLink.backendUrl,
        path,
        httpMethod,
        description: description || null,
      },
    });

    return res.json(await buildEndpointResponse(updated, false));
  } catch (error) {
    return next(error);
  }
});

manageRouter.delete("/endpoints/:endpointId", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { endpointId } = req.params;
    const endpoint = await ensureEndpointAccess(userId, endpointId);

    await prisma.rateLimitConfig.deleteMany({
      where: { endpointId: endpoint.id },
    });
    await prisma.endpoint.delete({ where: { id: endpoint.id } });

    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
});

manageRouter.post("/endpoints/:endpointId/toggle", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { endpointId } = req.params;
    const endpoint = await ensureEndpointAccess(userId, endpointId);

    const updated = await prisma.endpoint.update({
      where: { id: endpoint.id },
      data: { isActive: !endpoint.isActive },
    });

    return res.json(await buildEndpointResponse(updated, false));
  } catch (error) {
    return next(error);
  }
});

manageRouter.get("/tenants/:tenantId/analytics", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const startParam = req.query.start as string | undefined;
    const endParam = req.query.end as string | undefined;
    const end = endParam ? new Date(endParam) : new Date();
    const start = startParam
      ? new Date(startParam)
      : new Date(end.getTime() - 24 * 60 * 60 * 1000);

    const totalRequests = await prisma.rateLimitLog.count({
      where: { tenantId, timestamp: { gte: start, lte: end } },
    });
    const blockedRequests = await prisma.rateLimitLog.count({
      where: {
        tenantId,
        timestamp: { gte: start, lte: end },
        allowed: false,
      },
    });
    const allowedRequests = totalRequests - blockedRequests;
    const blockRate =
      totalRequests > 0 ? (blockedRequests / totalRequests) * 100 : 0;

    const hourlyStats = await prisma.$queryRaw<
      Array<{ hour: Date; total_count: bigint; blocked_count: bigint }>
    >`
      SELECT
        DATE_TRUNC('hour', "timestamp") as hour,
        COUNT(*) as total_count,
        SUM(CASE WHEN "allowed" = false THEN 1 ELSE 0 END) as blocked_count
      FROM "RateLimitLog"
      WHERE "tenantId" = ${tenantId}
        AND "timestamp" BETWEEN ${start} AND ${end}
      GROUP BY DATE_TRUNC('hour', "timestamp")
      ORDER BY hour
    `;

    const timeSeries = hourlyStats.map((row) => ({
      timestamp: formatIst(new Date(row.hour)),
      totalRequests: Number(row.total_count),
      blockedRequests: Number(row.blocked_count || 0),
    }));

    const endpointStats = await prisma.rateLimitLog.groupBy({
      by: ["endpointId"],
      where: { tenantId, timestamp: { gte: start, lte: end } },
      _count: { endpointId: true },
    });

    const topEndpoints = await Promise.all(
      endpointStats.map(async (stat) => {
        if (!stat.endpointId) {
          return null;
        }
        const endpoint = await prisma.endpoint.findUnique({
          where: { id: stat.endpointId },
        });
        return {
          endpoint: endpoint?.path || stat.endpointId,
          method: endpoint?.httpMethod || "*",
          requestCount: stat._count.endpointId,
          blockedCount: 0,
          blockRate: 0,
        };
      })
    );

    const filteredTopEndpoints = topEndpoints
      .filter((item): item is NonNullable<typeof item> => item !== null)
      .sort((a, b) => b.requestCount - a.requestCount)
      .slice(0, 10);

    return res.json({
      summary: {
        totalRequests,
        allowedRequests,
        blockedRequests,
        blockRate: Math.round(blockRate * 100) / 100,
        periodStart: formatIst(start),
        periodEnd: formatIst(end),
      },
      timeSeries,
      topEndpoints: filteredTopEndpoints,
      geographicDistribution: [],
      recentLogs: [],
    });
  } catch (error) {
    return next(error);
  }
});

manageRouter.get("/tenants/:tenantId", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const tenant = await prisma.tenant.findUnique({ where: { id: tenantId } });
    if (!tenant) {
      throw notFound("Tenant not found");
    }

    return res.json({ id: tenant.id, name: tenant.name, tier: tenant.tier });
  } catch (error) {
    return next(error);
  }
});

manageRouter.post(
  "/tenants/:tenantId/endpoints/:endpointId/configs",
  async (req, res, next) => {
    try {
      const userId = req.user?.id;
      if (!userId) {
        throw unauthorized("Invalid token");
      }

      const { tenantId, endpointId } = req.params;
      await ensureTenantAccess(userId, tenantId);

      const endpoint = await prisma.endpoint.findUnique({
        where: { id: endpointId },
      });
      if (!endpoint || endpoint.tenantId !== tenantId) {
        throw notFound("Endpoint not found");
      }

      const existingConfig = await prisma.rateLimitConfig.findFirst({
        where: { endpointId, isActive: true },
      });
      if (existingConfig) {
        throw badRequest("Only one active configuration is allowed per endpoint");
      }

      const tenant = await prisma.tenant.findUnique({ where: { id: tenantId } });
      if (!tenant) {
        throw notFound("Tenant not found");
      }

      const currentConfigCount = await prisma.rateLimitConfig.count({
        where: { tenantId, isActive: true },
      });
      const maxConfigs =
        tenant.tier === "FREE"
          ? 3
          : tenant.tier === "PRO"
          ? 10
          : Number.MAX_SAFE_INTEGER;
      if (currentConfigCount >= maxConfigs) {
        throw badRequest(
          `Tier limit exceeded: ${currentConfigCount}/${maxConfigs}`
        );
      }

      const {
        endpointPattern,
        httpMethod,
        maxRequests,
        windowSeconds,
        algorithm,
        limitBy,
        customHeader,
        refillRate,
      } = req.body || {};

      const resolvedEndpointPattern = endpointPattern || endpoint.path;
      const resolvedMethod = httpMethod || endpoint.httpMethod;

      if (!resolvedEndpointPattern) {
        throw badRequest("Endpoint pattern is required");
      }
      if (!maxRequests || Number(maxRequests) <= 0) {
        throw badRequest("Max requests must be at least 1");
      }
      if (!windowSeconds || Number(windowSeconds) <= 0) {
        throw badRequest("Window must be at least 1 second");
      }
      if (!algorithm) {
        throw badRequest("Algorithm is required");
      }

      if (algorithm === "SLIDING_WINDOW" && tenant.tier === "FREE") {
        throw badRequest("Sliding Window is available on the PRO tier");
      }
      if (limitBy === "CUSTOM_HEADER" && !customHeader) {
        throw badRequest("Custom header is required when limitBy is CUSTOM_HEADER");
      }
      if (algorithm === "TOKEN_BUCKET" && refillRate && Number(refillRate) <= 0) {
        throw badRequest("Refill rate must be positive");
      }

      const config = await prisma.rateLimitConfig.create({
        data: {
          tenantId,
          endpointId,
          endpointPattern: resolvedEndpointPattern,
          httpMethod: resolvedMethod || "*",
          maxRequests: Number(maxRequests),
          windowSeconds: Number(windowSeconds),
          algorithm,
          limitBy: limitBy || "IP",
          customHeader: customHeader || null,
          refillRate: refillRate ? Number(refillRate) : null,
          isActive: true,
        },
      });

      return res.status(201).json({
        ...config,
        createdAt: formatIst(config.createdAt),
      });
    } catch (error) {
      return next(error);
    }
  }
);

manageRouter.get(
  "/tenants/:tenantId/endpoints/:endpointId/configs",
  async (req, res, next) => {
    try {
      const userId = req.user?.id;
      if (!userId) {
        throw unauthorized("Invalid token");
      }

      const { tenantId, endpointId } = req.params;
      await ensureTenantAccess(userId, tenantId);

      const configs = await prisma.rateLimitConfig.findMany({
        where: { endpointId, isActive: true },
        orderBy: { createdAt: "desc" },
      });

      return res.json(
        configs.map((config) => ({
          ...config,
          createdAt: formatIst(config.createdAt),
        }))
      );
    } catch (error) {
      return next(error);
    }
  }
);

manageRouter.delete("/tenants/:tenantId/configs/:configId", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId, configId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const config = await prisma.rateLimitConfig.findUnique({
      where: { id: configId },
    });
    if (!config || config.tenantId !== tenantId) {
      throw notFound("Config not found");
    }

    await prisma.rateLimitConfig.update({
      where: { id: configId },
      data: { isActive: false },
    });

    return res.json({ message: "Configuration deleted" });
  } catch (error) {
    return next(error);
  }
});

manageRouter.post("/tenants/:tenantId/apikeys", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const { name, expiresAt } = req.body || {};
    if (!name) {
      throw badRequest("API key name is required");
    }

    let apiKey = generateApiKey();
    let attempts = 0;
    while (attempts < 10) {
      const exists = await prisma.apiKey.findUnique({ where: { apiKey } });
      if (!exists) {
        break;
      }
      apiKey = generateApiKey();
      attempts += 1;
    }
    if (attempts >= 10) {
      throw badRequest("Failed to generate unique API key");
    }

    const record = await prisma.apiKey.create({
      data: {
        tenantId,
        apiKey,
        name,
        expiresAt: expiresAt ? new Date(expiresAt) : null,
        isActive: true,
      },
    });

    return res.status(201).json({
      summary: {
        id: record.id,
        name: record.name,
        prefix: maskApiKey(record.apiKey),
        createdAt: formatIst(record.createdAt),
        expiresAt: record.expiresAt ? formatIst(record.expiresAt) : null,
        isActive: record.isActive,
      },
      apiKey,
    });
  } catch (error) {
    return next(error);
  }
});

manageRouter.get("/tenants/:tenantId/apikeys", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const apiKeys = await prisma.apiKey.findMany({
      where: { tenantId },
      orderBy: { createdAt: "desc" },
    });

    return res.json(
      apiKeys.map((apiKey) => ({
        id: apiKey.id,
        name: apiKey.name,
        prefix: maskApiKey(apiKey.apiKey),
        createdAt: formatIst(apiKey.createdAt),
        expiresAt: apiKey.expiresAt ? formatIst(apiKey.expiresAt) : null,
        isActive: apiKey.isActive,
      }))
    );
  } catch (error) {
    return next(error);
  }
});

manageRouter.delete("/tenants/:tenantId/apikeys/:apiKeyId", async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { tenantId, apiKeyId } = req.params;
    await ensureTenantAccess(userId, tenantId);

    const apiKey = await prisma.apiKey.findUnique({
      where: { id: apiKeyId },
    });
    if (!apiKey || apiKey.tenantId !== tenantId) {
      throw notFound("API key not found");
    }

    await prisma.apiKey.update({
      where: { id: apiKeyId },
      data: { isActive: false },
    });

    return res.status(204).send();
  } catch (error) {
    return next(error);
  }
});

type NukeRun = {
  testId: string;
  endpointUrl: string;
  httpMethod: string;
  totalRequests: number;
  concurrency: number;
  completedRequests: number;
  acceptedRequests: number;
  rejectedRequests: number;
  errorRequests: number;
  status: "RUNNING" | "COMPLETED";
  startedAt?: Date;
  finishedAt?: Date;
  logs: string[];
};

const nukeRuns = new Map<string, NukeRun>();
const LOG_LIMIT = 200;

const appendLog = (run: NukeRun, entry: string) => {
  run.logs.push(entry);
  while (run.logs.length > LOG_LIMIT) {
    run.logs.shift();
  }
};

const runWithConcurrency = async (
  tasks: Array<() => Promise<void>>,
  concurrency: number
) => {
  let current = 0;
  const runNext = async (): Promise<void> => {
    if (current >= tasks.length) {
      return Promise.resolve();
    }
    const taskIndex = current;
    current += 1;
    await tasks[taskIndex]();
    return runNext();
  };

  const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, runNext);
  await Promise.all(workers);
};

const executeNuke = async (run: NukeRun) => {
  run.status = "RUNNING";
  run.startedAt = new Date();
  appendLog(run, `Started nuke test: ${run.totalRequests} requests with concurrency ${run.concurrency}`);

  const tasks = Array.from({ length: run.totalRequests }, () => async () => {
    try {
      const response = await fetch(run.endpointUrl, {
        method: run.httpMethod === "*" ? "GET" : run.httpMethod,
        headers: { "Content-Type": "application/json" },
        body: run.httpMethod === "GET" || run.httpMethod === "*" ? undefined : "{}",
      });
      const status = response.status;
      run.completedRequests += 1;
      if (status === 429) {
        run.rejectedRequests += 1;
      } else if (status >= 200 && status < 400) {
        run.acceptedRequests += 1;
      } else {
        run.errorRequests += 1;
      }
      appendLog(run, `Response ${status} from ${run.endpointUrl}`);
    } catch (error) {
      run.completedRequests += 1;
      run.errorRequests += 1;
      appendLog(run, `Error: ${(error as Error).message}`);
    }
  });

  await runWithConcurrency(tasks, run.concurrency);

  run.status = "COMPLETED";
  run.finishedAt = new Date();
  appendLog(run, "Completed nuke test");
};

manageRouter.post(
  "/tenants/:tenantId/endpoints/:endpointId/nuke-tests",
  async (req, res, next) => {
    try {
      const userId = req.user?.id;
      if (!userId) {
        throw unauthorized("Invalid token");
      }

      const { tenantId, endpointId } = req.params;
      await ensureTenantAccess(userId, tenantId);

      const endpoint = await prisma.endpoint.findUnique({
        where: { id: endpointId },
      });
      if (!endpoint || endpoint.tenantId !== tenantId) {
        throw notFound("Endpoint not found");
      }

      const { totalRequests, concurrency } = req.body || {};
      const total = Number(totalRequests);
      const conc = Number(concurrency);

      if (!total || total < 1 || total > 2000) {
        throw badRequest("Total requests must be between 1 and 2000");
      }
      if (!conc || conc < 1 || conc > 200) {
        throw badRequest("Concurrency must be between 1 and 200");
      }

      const testId = crypto.randomUUID();
      const run: NukeRun = {
        testId,
        endpointUrl: buildFullUrl(endpoint.basePath, endpoint.path),
        httpMethod: endpoint.httpMethod || "GET",
        totalRequests: total,
        concurrency: conc,
        completedRequests: 0,
        acceptedRequests: 0,
        rejectedRequests: 0,
        errorRequests: 0,
        status: "RUNNING",
        logs: [],
      };
      nukeRuns.set(testId, run);

      executeNuke(run).catch((error) => {
        appendLog(run, `Error: ${(error as Error).message}`);
        run.status = "COMPLETED";
        run.finishedAt = new Date();
      });

      return res.json({ testId });
    } catch (error) {
      return next(error);
    }
  }
);

manageRouter.get("/nuke-tests/:testId", async (req, res, next) => {
  try {
    const { testId } = req.params;
    const run = nukeRuns.get(testId);
    if (!run) {
      throw notFound("Nuke test not found");
    }

    return res.json({
      testId: run.testId,
      status: run.status,
      endpointUrl: run.endpointUrl,
      httpMethod: run.httpMethod,
      totalRequests: run.totalRequests,
      completedRequests: run.completedRequests,
      acceptedRequests: run.acceptedRequests,
      rejectedRequests: run.rejectedRequests,
      errorRequests: run.errorRequests,
      startedAt: run.startedAt ? formatIst(run.startedAt) : null,
      finishedAt: run.finishedAt ? formatIst(run.finishedAt) : null,
      logs: run.logs,
    });
  } catch (error) {
    return next(error);
  }
});

manageRouter.get("/health", (_req, res) => res.send("OK"));

export default manageRouter;
