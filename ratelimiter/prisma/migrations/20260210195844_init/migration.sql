-- CreateEnum
CREATE TYPE "TenantTier" AS ENUM ('FREE', 'PRO', 'ENTERPRISE');

-- CreateEnum
CREATE TYPE "Algorithm" AS ENUM ('TOKEN_BUCKET', 'SLIDING_WINDOW', 'FIXED_WINDOW', 'LEAKY_BUCKET');

-- CreateEnum
CREATE TYPE "LimitBy" AS ENUM ('IP', 'USER_ID', 'API_KEY', 'CUSTOM_HEADER');

-- CreateTable
CREATE TABLE "User" (
    "id" TEXT NOT NULL,
    "email" TEXT NOT NULL,
    "passwordHash" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "isEmailVerified" BOOLEAN NOT NULL DEFAULT false,
    "tenantId" TEXT,
    "otpCode" TEXT,
    "otpExpiry" TIMESTAMP(3),
    "verificationToken" TEXT,
    "verificationTokenExpiry" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "User_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Tenant" (
    "id" TEXT NOT NULL,
    "name" TEXT NOT NULL,
    "userId" TEXT,
    "tier" "TenantTier" NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Tenant_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "ApiKey" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "apiKey" TEXT NOT NULL,
    "name" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3),
    "isActive" BOOLEAN NOT NULL DEFAULT true,

    CONSTRAINT "ApiKey_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "BackendLink" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "backendUrl" TEXT NOT NULL,
    "nickname" TEXT NOT NULL,
    "accentColor" TEXT NOT NULL,
    "verificationToken" TEXT NOT NULL,
    "isVerified" BOOLEAN NOT NULL DEFAULT false,
    "verificationAttempts" INTEGER NOT NULL DEFAULT 0,
    "lastVerificationAttempt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "BackendLink_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Endpoint" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "backendLinkId" TEXT,
    "basePath" TEXT NOT NULL,
    "path" TEXT NOT NULL,
    "httpMethod" TEXT NOT NULL DEFAULT '*',
    "description" TEXT,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Endpoint_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RateLimitConfig" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "endpointId" TEXT,
    "endpointPattern" TEXT NOT NULL,
    "httpMethod" TEXT NOT NULL DEFAULT '*',
    "maxRequests" INTEGER NOT NULL,
    "windowSeconds" INTEGER NOT NULL,
    "algorithm" "Algorithm" NOT NULL DEFAULT 'TOKEN_BUCKET',
    "refillRate" DOUBLE PRECISION,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "limitBy" "LimitBy" NOT NULL DEFAULT 'IP',
    "customHeader" TEXT,

    CONSTRAINT "RateLimitConfig_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RateLimitLog" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "endpointId" TEXT,
    "configId" TEXT,
    "timestamp" TIMESTAMP(3) NOT NULL,
    "clientIdentifier" TEXT NOT NULL,
    "identifierType" "LimitBy" NOT NULL,
    "allowed" BOOLEAN NOT NULL,
    "currentCount" INTEGER,
    "maxRequests" INTEGER,
    "algorithm" "Algorithm",
    "httpMethod" TEXT,
    "requestPath" TEXT,
    "countryCode" TEXT,
    "responseTimeMs" INTEGER,

    CONSTRAINT "RateLimitLog_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "RefreshToken" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "tokenHash" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "revoked" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "RefreshToken_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "User_email_key" ON "User"("email");

-- CreateIndex
CREATE INDEX "Tenant_userId_idx" ON "Tenant"("userId");

-- CreateIndex
CREATE UNIQUE INDEX "ApiKey_apiKey_key" ON "ApiKey"("apiKey");

-- CreateIndex
CREATE INDEX "ApiKey_tenantId_idx" ON "ApiKey"("tenantId");

-- CreateIndex
CREATE INDEX "BackendLink_userId_idx" ON "BackendLink"("userId");

-- CreateIndex
CREATE INDEX "Endpoint_tenantId_idx" ON "Endpoint"("tenantId");

-- CreateIndex
CREATE INDEX "Endpoint_basePath_path_idx" ON "Endpoint"("basePath", "path");

-- CreateIndex
CREATE INDEX "RateLimitConfig_tenantId_endpointPattern_idx" ON "RateLimitConfig"("tenantId", "endpointPattern");

-- CreateIndex
CREATE INDEX "RateLimitConfig_endpointId_idx" ON "RateLimitConfig"("endpointId");

-- CreateIndex
CREATE INDEX "RateLimitLog_tenantId_timestamp_idx" ON "RateLimitLog"("tenantId", "timestamp");

-- CreateIndex
CREATE INDEX "RateLimitLog_endpointId_timestamp_idx" ON "RateLimitLog"("endpointId", "timestamp");

-- CreateIndex
CREATE INDEX "RateLimitLog_timestamp_idx" ON "RateLimitLog"("timestamp");

-- CreateIndex
CREATE UNIQUE INDEX "RefreshToken_tokenHash_key" ON "RefreshToken"("tokenHash");

-- CreateIndex
CREATE INDEX "RefreshToken_userId_idx" ON "RefreshToken"("userId");

-- AddForeignKey
ALTER TABLE "ApiKey" ADD CONSTRAINT "ApiKey_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "Tenant"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Endpoint" ADD CONSTRAINT "Endpoint_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "Tenant"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RateLimitConfig" ADD CONSTRAINT "RateLimitConfig_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "Tenant"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RateLimitConfig" ADD CONSTRAINT "RateLimitConfig_endpointId_fkey" FOREIGN KEY ("endpointId") REFERENCES "Endpoint"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RateLimitLog" ADD CONSTRAINT "RateLimitLog_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "Tenant"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RateLimitLog" ADD CONSTRAINT "RateLimitLog_endpointId_fkey" FOREIGN KEY ("endpointId") REFERENCES "Endpoint"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "RateLimitLog" ADD CONSTRAINT "RateLimitLog_configId_fkey" FOREIGN KEY ("configId") REFERENCES "RateLimitConfig"("id") ON DELETE SET NULL ON UPDATE CASCADE;
