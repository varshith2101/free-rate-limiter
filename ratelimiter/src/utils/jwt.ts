import jwt from "jsonwebtoken";
import crypto from "crypto";
import { config } from "../config";

export type TokenPayload = {
  sub: string;
  email?: string;
};

export const signAccessToken = (userId: string, email: string) =>
  jwt.sign({ sub: userId, email }, config.jwtSecret, {
    expiresIn: Math.floor(config.jwtExpirationMs / 1000),
  });

export const signRefreshToken = (userId: string) =>
  jwt.sign({ sub: userId }, config.jwtSecret, {
    expiresIn: Math.floor(config.jwtRefreshExpirationMs / 1000),
  });

export const verifyToken = (token: string) =>
  jwt.verify(token, config.jwtSecret) as TokenPayload;

export const isTokenValid = (token: string) => {
  try {
    verifyToken(token);
    return true;
  } catch {
    return false;
  }
};

export const hashToken = (token: string) =>
  crypto.createHash("sha256").update(token).digest("hex");
