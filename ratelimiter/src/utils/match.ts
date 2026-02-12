export const matchesPattern = (pattern: string, endpoint: string) => {
  if (pattern === "*") {
    return true;
  }
  if (pattern === endpoint) {
    return true;
  }
  if (pattern.endsWith("*")) {
    const prefix = pattern.slice(0, -1);
    return endpoint.startsWith(prefix);
  }
  return false;
};

export const methodWeight = (method: string, candidate: string) =>
  candidate === method ? 1 : 0;
