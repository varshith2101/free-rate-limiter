import dotenv from "dotenv";

dotenv.config();

const toNumber = (value: string | undefined, fallback: number) => {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isNaN(parsed) ? fallback : parsed;
};

export const config = {
  env: process.env.NODE_ENV || "development",
  port: toNumber(process.env.PORT, 8081),
  databaseUrl: process.env.DATABASE_URL || "",
  redisHost: process.env.REDIS_HOST || "redis",
  redisPort: toNumber(process.env.REDIS_PORT, 6379),
  jwtSecret:
    process.env.JWT_SECRET ||
    "your-super-secret-key-that-should-be-changed-in-production-environment-variables",
  jwtExpirationMs: toNumber(process.env.JWT_EXPIRATION_MS, 86_400_000),
  jwtRefreshExpirationMs: toNumber(process.env.JWT_REFRESH_EXPIRATION_MS, 604_800_000),
  authMode: (process.env.AUTH_MODE || "standard").toLowerCase(),
  adminEmail: process.env.ADMIN_EMAIL || "",
  adminPassword: process.env.ADMIN_PASSWORD || "",
  mail: {
    host: process.env.MAIL_HOST || "",
    port: toNumber(process.env.MAIL_PORT, 587),
    username: process.env.MAIL_USERNAME || "",
    password: process.env.MAIL_PASSWORD || "",
    from: process.env.MAIL_FROM || "noreply@rate-limiter.com",
    secure: (process.env.MAIL_SSL_ENABLE || "false") === "true",
  },
};
