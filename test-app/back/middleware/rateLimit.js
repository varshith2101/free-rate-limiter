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

  if (response.status === 429) {
    const data = await response.json().catch(() => ({}));
    return { allowed: false, data };
  }

  return { allowed: true };
};

const rateLimitGuard = async (req, res, next) => {
  const result = await checkRateLimit(req);
  if (!result.allowed) {
    return res.status(429).json(result.data || { message: 'Rate limit exceeded' });
  }
  return next();
};

module.exports = { rateLimitGuard };