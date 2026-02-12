import { NextFunction, Request, Response } from "express";
import { isTokenValid, verifyToken } from "../utils/jwt";
import { unauthorized } from "../utils/errors";

export const requireAuth = (req: Request, _res: Response, next: NextFunction) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    return next(unauthorized("Invalid authorization header"));
  }

  const token = authHeader.slice(7);
  if (!isTokenValid(token)) {
    return next(unauthorized("Invalid token"));
  }

  const payload = verifyToken(token);
  req.user = {
    id: payload.sub,
    email: payload.email,
  };

  return next();
};
