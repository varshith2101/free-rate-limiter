import crypto from "crypto";

const PREFIX = "rlim_";
const KEY_LENGTH = 32;
const BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

export const generateApiKey = () => {
  let value = "";
  for (let i = 0; i < KEY_LENGTH; i += 1) {
    value += BASE62[crypto.randomInt(0, BASE62.length)];
  }
  return `${PREFIX}${value}`;
};

export const maskApiKey = (apiKey: string | null | undefined) => {
  if (!apiKey || apiKey.length <= 12) {
    return "***";
  }
  return `${apiKey.slice(0, 12)}***`;
};
