import bcrypt from "bcryptjs";
import crypto from "crypto";

export const hashPassword = async (password: string) => {
  const salt = await bcrypt.genSalt(10);
  return bcrypt.hash(password, salt);
};

export const verifyPassword = async (password: string, hash: string) =>
  bcrypt.compare(password, hash);

export const generateOtp = () =>
  String(crypto.randomInt(0, 1_000_000)).padStart(6, "0");

export const generateVerificationToken = () => crypto.randomUUID();
