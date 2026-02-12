import { Router } from "express";
import { prisma } from "../db";
import { config } from "../config";
import {
  badRequest,
  notFound,
  unauthorized,
} from "../utils/errors";
import {
  generateOtp,
  generateVerificationToken,
  hashPassword,
  verifyPassword,
} from "../utils/password";
import {
  hashToken,
  isTokenValid,
  signAccessToken,
  signRefreshToken,
  verifyToken,
} from "../utils/jwt";
import { sendOtpEmail } from "../utils/email";
import { requireAuth } from "../middleware/auth";

export const authRouter = Router();

const toUserDto = (user: {
  id: string;
  email: string;
  name: string;
  isEmailVerified: boolean;
  tenantId: string | null;
}) => ({
  id: user.id,
  email: user.email,
  name: user.name,
  isEmailVerified: user.isEmailVerified,
  tenantId: user.tenantId,
});

const createTenantForUser = async (userId: string, name: string) => {
  const tenant = await prisma.tenant.create({
    data: {
      name: `${name} (FREE)`,
      tier: "FREE",
      userId,
    },
  });
  await prisma.user.update({
    where: { id: userId },
    data: { tenantId: tenant.id },
  });
  return tenant;
};

const createRefreshToken = async (userId: string, token: string) => {
  const expiresAt = new Date(Date.now() + config.jwtRefreshExpirationMs);
  await prisma.refreshToken.create({
    data: {
      userId,
      tokenHash: hashToken(token),
      expiresAt,
      revoked: false,
    },
  });
};

const validateUrl = (url: string) => {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
      throw badRequest("URL must start with http:// or https://");
    }
  } catch {
    throw badRequest("Invalid URL format");
  }
};

const validateAccentColor = (accentColor: string) => {
  if (!accentColor || accentColor.trim().length === 0) {
    throw badRequest("Accent color is required");
  }
  const isHex = /^#([0-9a-fA-F]{6})$/.test(accentColor);
  if (!isHex) {
    throw badRequest("Accent color must be a hex value like #22C55E");
  }
};

const shouldRateLimitAttempt = (lastAttempt: Date | null) => {
  if (!lastAttempt) {
    return false;
  }
  const secondsAgo = (Date.now() - lastAttempt.getTime()) / 1000;
  return secondsAgo < 5;
};

authRouter.post("/send-otp", async (req, res, next) => {
  try {
    if (config.authMode === "solo") {
      throw badRequest("Solo auth mode is enabled. OTP sign-up is disabled.");
    }

    const { email, name } = req.body || {};
    if (!email || !name) {
      throw badRequest("Email and name are required");
    }

    const existing = await prisma.user.findUnique({ where: { email } });
    if (existing?.isEmailVerified) {
      throw badRequest("Email already registered");
    }

    const otp = generateOtp();
    const otpExpiry = new Date(Date.now() + 10 * 60 * 1000);

    const user = existing
      ? await prisma.user.update({
          where: { email },
          data: {
            name,
            otpCode: otp,
            otpExpiry,
          },
        })
      : await prisma.user.create({
          data: {
            email,
            name,
            passwordHash: await hashPassword(generateVerificationToken()),
            isEmailVerified: false,
            otpCode: otp,
            otpExpiry,
          },
        });

    await sendOtpEmail(email, otp, user.name);

    return res.json({ message: `OTP sent to ${email}` });
  } catch (error) {
    return next(error);
  }
});

authRouter.post("/verify-otp", async (req, res, next) => {
  try {
    if (config.authMode === "solo") {
      throw badRequest("Solo auth mode is enabled. OTP sign-up is disabled.");
    }

    const { email, otp, password } = req.body || {};
    if (!email || !otp || !password) {
      throw badRequest("Email, otp, and password are required");
    }

    const user = await prisma.user.findUnique({ where: { email } });
    if (!user) {
      throw notFound("User not found");
    }

    if (!user.otpCode || user.otpCode !== otp) {
      throw badRequest("Invalid OTP");
    }

    if (user.otpExpiry && user.otpExpiry < new Date()) {
      throw badRequest("OTP expired");
    }

    const updated = await prisma.user.update({
      where: { id: user.id },
      data: {
        passwordHash: await hashPassword(password),
        isEmailVerified: true,
        otpCode: null,
        otpExpiry: null,
      },
    });

    if (!updated.tenantId) {
      await createTenantForUser(updated.id, updated.name);
    }

    const refreshed = await prisma.user.findUnique({ where: { id: updated.id } });
    if (!refreshed) {
      throw notFound("User not found");
    }

    const token = signAccessToken(refreshed.id, refreshed.email);
    const refreshToken = signRefreshToken(refreshed.id);
    await createRefreshToken(refreshed.id, refreshToken);

    return res.json({
      token,
      refreshToken,
      user: toUserDto({
        id: refreshed.id,
        email: refreshed.email,
        name: refreshed.name,
        isEmailVerified: refreshed.isEmailVerified,
        tenantId: refreshed.tenantId,
      }),
    });
  } catch (error) {
    return next(error);
  }
});

authRouter.post("/login", async (req, res, next) => {
  try {
    const { email, password } = req.body || {};
    if (!email || !password) {
      throw badRequest("Email and password are required");
    }

    if (config.authMode === "solo") {
      if (!config.adminEmail || !config.adminPassword) {
        throw badRequest("Solo auth mode requires ADMIN_EMAIL and ADMIN_PASSWORD");
      }
      if (email.toLowerCase() !== config.adminEmail.toLowerCase()) {
        throw unauthorized("Solo auth mode only allows the admin email to sign in");
      }
      if (password !== config.adminPassword) {
        throw unauthorized("Invalid email or password");
      }

      const admin = await prisma.user.upsert({
        where: { email: config.adminEmail },
        update: {
          name: "Admin",
          isEmailVerified: true,
          passwordHash: await hashPassword(config.adminPassword),
        },
        create: {
          email: config.adminEmail,
          name: "Admin",
          isEmailVerified: true,
          passwordHash: await hashPassword(config.adminPassword),
        },
      });

      if (!admin.tenantId) {
        await createTenantForUser(admin.id, "Admin");
      }

      const refreshed = await prisma.user.findUnique({ where: { id: admin.id } });
      if (!refreshed) {
        throw notFound("User not found");
      }

      const token = signAccessToken(refreshed.id, refreshed.email);
      const refreshToken = signRefreshToken(refreshed.id);
      await createRefreshToken(refreshed.id, refreshToken);

      return res.json({
        token,
        refreshToken,
        user: toUserDto({
          id: refreshed.id,
          email: refreshed.email,
          name: refreshed.name,
          isEmailVerified: refreshed.isEmailVerified,
          tenantId: refreshed.tenantId,
        }),
      });
    }

    const user = await prisma.user.findUnique({ where: { email } });
    if (!user || !user.isEmailVerified) {
      throw unauthorized("Invalid email or password");
    }

    const valid = await verifyPassword(password, user.passwordHash);
    if (!valid) {
      throw unauthorized("Invalid email or password");
    }

    const token = signAccessToken(user.id, user.email);
    const refreshToken = signRefreshToken(user.id);
    await createRefreshToken(user.id, refreshToken);

    return res.json({
      token,
      refreshToken,
      user: toUserDto({
        id: user.id,
        email: user.email,
        name: user.name,
        isEmailVerified: user.isEmailVerified,
        tenantId: user.tenantId,
      }),
    });
  } catch (error) {
    return next(error);
  }
});

authRouter.post("/refresh", async (req, res, next) => {
  try {
    const { refreshToken } = req.body || {};
    if (!refreshToken) {
      throw badRequest("Refresh token is required");
    }

    if (!isTokenValid(refreshToken)) {
      throw unauthorized("Invalid refresh token");
    }

    const payload = verifyToken(refreshToken);
    const existing = await prisma.refreshToken.findUnique({
      where: { tokenHash: hashToken(refreshToken) },
    });

    if (!existing || existing.revoked || existing.expiresAt < new Date()) {
      throw unauthorized("Invalid refresh token");
    }

    await prisma.refreshToken.update({
      where: { id: existing.id },
      data: { revoked: true },
    });

    const user = await prisma.user.findUnique({ where: { id: payload.sub } });
    if (!user) {
      throw unauthorized("User not found");
    }

    const newAccessToken = signAccessToken(user.id, user.email);
    const newRefreshToken = signRefreshToken(user.id);
    await createRefreshToken(user.id, newRefreshToken);

    return res.json({
      token: newAccessToken,
      refreshToken: newRefreshToken,
      user: toUserDto({
        id: user.id,
        email: user.email,
        name: user.name,
        isEmailVerified: user.isEmailVerified,
        tenantId: user.tenantId,
      }),
    });
  } catch (error) {
    return next(error);
  }
});

authRouter.post("/logout", async (req, res, next) => {
  try {
    const { refreshToken } = req.body || {};
    if (!refreshToken) {
      throw badRequest("Refresh token is required");
    }

    const tokenHash = hashToken(refreshToken);
    const existing = await prisma.refreshToken.findUnique({ where: { tokenHash } });
    if (existing) {
      await prisma.refreshToken.update({
        where: { id: existing.id },
        data: { revoked: true },
      });
    }

    return res.json({ message: "Logged out" });
  } catch (error) {
    return next(error);
  }
});

authRouter.get("/me", requireAuth, async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user) {
      throw notFound("User not found");
    }

    return res.json(toUserDto(user));
  } catch (error) {
    return next(error);
  }
});

authRouter.post("/backend-links", requireAuth, async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const { backendUrl, nickname, accentColor } = req.body || {};
    if (!backendUrl || !nickname || !accentColor) {
      throw badRequest("backendUrl, nickname, and accentColor are required");
    }

    validateUrl(backendUrl);
    validateAccentColor(accentColor);

    const existing = await prisma.backendLink.findFirst({
      where: { backendUrl, userId },
    });
    if (existing) {
      throw badRequest("Backend link already exists");
    }

    const verificationToken = generateVerificationToken();
    const backendLink = await prisma.backendLink.create({
      data: {
        userId,
        backendUrl,
        nickname,
        accentColor,
        verificationToken,
        isVerified: false,
        verificationAttempts: 0,
      },
    });

    return res.json({
      id: backendLink.id,
      backendUrl: backendLink.backendUrl,
      nickname: backendLink.nickname,
      accentColor: backendLink.accentColor,
      verificationToken: backendLink.verificationToken,
      verificationPath: `/.well-known/ratelimiter-verify?token=${backendLink.verificationToken}`,
      message:
        "Place the token on your backend at the verification path, then click verify.",
    });
  } catch (error) {
    return next(error);
  }
});

authRouter.post(
  "/backend-links/:linkId/verify",
  requireAuth,
  async (req, res, next) => {
    try {
      const userId = req.user?.id;
      if (!userId) {
        throw unauthorized("Invalid token");
      }

      const { linkId } = req.params;
      const backendLink = await prisma.backendLink.findUnique({
        where: { id: linkId },
      });
      if (!backendLink) {
        throw notFound("Backend link not found");
      }
      if (backendLink.userId !== userId) {
        throw unauthorized("Unauthorized");
      }
      if (shouldRateLimitAttempt(backendLink.lastVerificationAttempt)) {
        throw badRequest("Please wait before trying again");
      }

      await prisma.backendLink.update({
        where: { id: backendLink.id },
        data: {
          verificationAttempts: (backendLink.verificationAttempts || 0) + 1,
          lastVerificationAttempt: new Date(),
        },
      });

      const normalizedBackendUrl = backendLink.backendUrl.replace(/\/+$/, "");
      const verifyUrl = `${normalizedBackendUrl}/.well-known/ratelimiter-verify?token=${backendLink.verificationToken}`;
      const response = await fetch(verifyUrl);
      const body = await response.text();

      if (!response.ok || !body.includes(backendLink.verificationToken)) {
        throw badRequest(
          "Failed to verify backend. Ensure the endpoint is accessible and returns the token."
        );
      }

      await prisma.backendLink.update({
        where: { id: backendLink.id },
        data: { isVerified: true },
      });

      return res.json({ message: "Backend link verified successfully" });
    } catch (error) {
      return next(error);
    }
  }
);

authRouter.get("/backend-links", requireAuth, async (req, res, next) => {
  try {
    const userId = req.user?.id;
    if (!userId) {
      throw unauthorized("Invalid token");
    }

    const backendLinks = await prisma.backendLink.findMany({
      where: { userId },
      orderBy: { createdAt: "desc" },
    });

    return res.json(backendLinks);
  } catch (error) {
    return next(error);
  }
});

authRouter.delete(
  "/backend-links/:linkId",
  requireAuth,
  async (req, res, next) => {
    try {
      const userId = req.user?.id;
      if (!userId) {
        throw unauthorized("Invalid token");
      }

      const { linkId } = req.params;
      const backendLink = await prisma.backendLink.findUnique({
        where: { id: linkId },
      });

      if (!backendLink) {
        throw notFound("Backend link not found");
      }
      if (backendLink.userId !== userId) {
        throw unauthorized("Unauthorized");
      }

      await prisma.backendLink.delete({ where: { id: backendLink.id } });
      return res.json({ message: "Backend link deleted" });
    } catch (error) {
      return next(error);
    }
  }
);

export default authRouter;
