import { Router } from "express";
import { prisma } from "../db";
import { requireAuth } from "../middleware/auth";
import { badRequest, notFound } from "../utils/errors";
import { generateApiKey } from "../utils/apiKey";

export const adminRouter = Router();

adminRouter.use(requireAuth);

adminRouter.post("/tenants", async (req, res, next) => {
  try {
    const { name, tier } = req.body || {};
    if (!name || !tier) {
      throw badRequest("Name and tier are required");
    }

    const tenant = await prisma.tenant.create({ data: { name, tier } });
    return res.status(201).json(tenant);
  } catch (error) {
    return next(error);
  }
});

adminRouter.get("/tenants/:id", async (req, res, next) => {
  try {
    const tenant = await prisma.tenant.findUnique({ where: { id: req.params.id } });
    if (!tenant) {
      throw notFound("Tenant not found");
    }
    return res.json(tenant);
  } catch (error) {
    return next(error);
  }
});

adminRouter.get("/tenants", async (_req, res, next) => {
  try {
    const tenants = await prisma.tenant.findMany({ orderBy: { createdAt: "desc" } });
    return res.json(tenants);
  } catch (error) {
    return next(error);
  }
});

adminRouter.post("/apikeys", async (req, res, next) => {
  try {
    const { tenantId, expiresAt } = req.body || {};
    if (!tenantId) {
      throw badRequest("Tenant ID is required");
    }

    const apiKey = generateApiKey();
    const record = await prisma.apiKey.create({
      data: {
        tenantId,
        apiKey,
        expiresAt: expiresAt ? new Date(expiresAt) : null,
        isActive: true,
      },
    });

    return res.status(201).json(record);
  } catch (error) {
    return next(error);
  }
});

adminRouter.put("/apikeys/:id/rotate", async (req, res, next) => {
  try {
    const { id } = req.params;
    const existing = await prisma.apiKey.findUnique({ where: { id } });
    if (!existing) {
      throw notFound("API key not found");
    }

    const apiKey = generateApiKey();
    const updated = await prisma.apiKey.update({
      where: { id },
      data: { apiKey },
    });

    return res.json(updated);
  } catch (error) {
    return next(error);
  }
});

adminRouter.get("/tenants/:tenantId/apikeys", async (req, res, next) => {
  try {
    const { tenantId } = req.params;
    const apiKeys = await prisma.apiKey.findMany({
      where: { tenantId },
      orderBy: { createdAt: "desc" },
    });
    return res.json(apiKeys);
  } catch (error) {
    return next(error);
  }
});

adminRouter.post("/configs", async (req, res, next) => {
  try {
    const { tenantId, endpointPattern, httpMethod, maxRequests, windowSeconds, algorithm } = req.body || {};
    if (!tenantId || !endpointPattern || !maxRequests || !windowSeconds || !algorithm) {
      throw badRequest("Missing required fields");
    }

    const config = await prisma.rateLimitConfig.create({
      data: {
        tenantId,
        endpointPattern,
        httpMethod: httpMethod || "*",
        maxRequests: Number(maxRequests),
        windowSeconds: Number(windowSeconds),
        algorithm,
        limitBy: req.body.limitBy || "IP",
        customHeader: req.body.customHeader || null,
        refillRate: req.body.refillRate ? Number(req.body.refillRate) : null,
        isActive: true,
      },
    });

    return res.status(201).json(config);
  } catch (error) {
    return next(error);
  }
});

adminRouter.get("/tenants/:tenantId/configs", async (req, res, next) => {
  try {
    const configs = await prisma.rateLimitConfig.findMany({
      where: { tenantId: req.params.tenantId },
      orderBy: { createdAt: "desc" },
    });
    return res.json(configs);
  } catch (error) {
    return next(error);
  }
});

adminRouter.get("/configs/:id", async (req, res, next) => {
  try {
    const config = await prisma.rateLimitConfig.findUnique({ where: { id: req.params.id } });
    if (!config) {
      throw notFound("Config not found");
    }
    return res.json(config);
  } catch (error) {
    return next(error);
  }
});

adminRouter.delete("/configs/:id", async (req, res, next) => {
  try {
    const config = await prisma.rateLimitConfig.findUnique({ where: { id: req.params.id } });
    if (!config) {
      throw notFound("Config not found");
    }

    await prisma.rateLimitConfig.update({
      where: { id: req.params.id },
      data: { isActive: false },
    });

    return res.json({ message: "Configuration deleted successfully", configId: req.params.id });
  } catch (error) {
    return next(error);
  }
});

export default adminRouter;
