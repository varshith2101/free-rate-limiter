const checkRateLimit = async (req) => {
  const response = await fetch(
    `${process.env.RATELIMITER_URL}/api/v1/ratelimit/check`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': process.env.RATELIMITER_API_KEY,
      },
      body: JSON.stringify({
        endpoint: req.path,
        method: req.method,
      }),
    }
  );

  const data = await response.json().catch(() => ({}));

  if (response.status === 429 || data?.allowed === false) {
    return { allowed: false, status: 429, data };
  }

  // Optional strict mode: block requests when no matching config exists.
  const strictNoMatch = String(process.env.RATELIMITER_BLOCK_ON_UNMATCHED || 'false') === 'true';
  if (strictNoMatch && response.ok && data?.algorithm === 'NONE') {
    return {
      allowed: false,
      status: 429,
      data: {
        message: 'No matching rate limit config for this endpoint',
        endpoint: req.path,
        method: req.method,
      },
    };
  }

  if (!response.ok) {
    return {
      allowed: false,
      status: response.status || 503,
      data: {
        message: data?.message || 'Rate limiter check failed',
      },
    };
  }

  return { allowed: true, status: 200, data };
};

const rateLimitGuard = async (req, res, next) => {
  // Keep these utility paths reachable during setup/verification.
  if (req.path === '/api/health' || req.path.startsWith('/.well-known/')) {
    return next();
  }

  const result = await checkRateLimit(req);
  if (!result.allowed) {
    return res
      .status(result.status || 429)
      .json(result.data || { message: 'Rate limit exceeded' });
  }
  return next();
};

module.exports = { rateLimitGuard };