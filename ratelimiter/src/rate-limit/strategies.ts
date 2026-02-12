import { redis } from "../redis";
import {
  fixedWindowScript,
  leakyBucketScript,
  slidingWindowScript,
  tokenBucketScript,
} from "./scripts";

const parseResult = (result: unknown) => {
  if (!Array.isArray(result) || result.length < 3) {
    throw new Error("Unexpected Redis script response");
  }
  const [allowed, remaining, resetAt] = result as Array<number | string>;
  return {
    allowed: Number(allowed) === 1,
    remaining: Number(remaining),
    resetAt: Number(resetAt),
  };
};

export const runFixedWindow = async (
  key: string,
  maxRequests: number,
  windowSeconds: number
) => {
  const now = Math.floor(Date.now() / 1000);
  const result = await redis.eval(
    fixedWindowScript,
    1,
    key,
    maxRequests,
    windowSeconds,
    now
  );
  return parseResult(result);
};

export const runTokenBucket = async (
  key: string,
  maxRequests: number,
  refillRate: number,
  windowSeconds: number
) => {
  const now = Math.floor(Date.now() / 1000);
  const ttl = windowSeconds * 2;
  const result = await redis.eval(
    tokenBucketScript,
    1,
    key,
    maxRequests,
    refillRate,
    now,
    ttl
  );
  return parseResult(result);
};

export const runSlidingWindow = async (
  key: string,
  maxRequests: number,
  windowSeconds: number
) => {
  const nowMs = Date.now();
  const requestId = `${nowMs}-${Math.random().toString(36).slice(2, 10)}`;
  const result = await redis.eval(
    slidingWindowScript,
    1,
    key,
    windowSeconds,
    maxRequests,
    nowMs,
    requestId
  );
  return parseResult(result);
};

export const runLeakyBucket = async (
  key: string,
  maxRequests: number,
  windowSeconds: number
) => {
  const now = Math.floor(Date.now() / 1000);
  const ttl = windowSeconds * 2;
  const leakRate = maxRequests / windowSeconds;
  const result = await redis.eval(
    leakyBucketScript,
    1,
    key,
    maxRequests,
    leakRate,
    now,
    ttl
  );
  return parseResult(result);
};
