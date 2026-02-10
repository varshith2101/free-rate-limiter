import { useState } from 'react';

export default function SetupTutorial() {
  const [stack, setStack] = useState('js');

  const stackOptions = [
    { id: 'js', label: 'JavaScript (Express)' },
    { id: 'ts', label: 'TypeScript (Express)' },
    { id: 'other', label: 'Other stack' },
  ];

  return (
    <div className="px-4 py-6 sm:px-0 space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-white">Setup Tutorial</h1>
        <p className="text-gray-400">
          Step-by-step setup for new developers. Follow in order and you will be verified and live.
        </p>
      </div>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">1. Host the rate limiter</h2>
        <p className="text-gray-300">
          Run the rate limiter backend on a server or your local machine.
          You will use its base URL as <span className="text-emerald-300">RATELIMITER_URL</span>.
        </p>
        <div className="neo-panel p-4 text-sm text-gray-300">
          Example: <span className="text-emerald-300">http://localhost</span> (if you are using the gateway)
        </div>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">2. Generate an API key</h2>
        <p className="text-gray-300">
          Open the <span className="text-emerald-300">API</span> page from your account menu.
          Create a key and copy it immediately (it is only shown once).
          You will use it as <span className="text-emerald-300">RATELIMITER_API_KEY</span>.
        </p>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">3. Configure your backend</h2>
        <p className="text-gray-300">
          In your backend environment variables (e.g., .env file), set:
        </p>
        <div className="neo-panel p-4 text-sm text-gray-300">
          <div>RATELIMITER_URL=&lt;your rate limiter base URL&gt;</div>
          <div>RATELIMITER_API_KEY=&lt;your generated key&gt;</div>
        </div>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">4. Add the verification route</h2>
        <p className="text-gray-300">
          Select your backend stack, add the verification route, and confirm it responds with the token.
          You will use this to prove ownership in <span className="text-emerald-300">Backend Links</span>.
        </p>
        <div className="flex flex-wrap gap-2">
          {stackOptions.map((option) => (
            <button
              key={option.id}
              type="button"
              onClick={() => setStack(option.id)}
              className={`px-4 py-2 rounded-lg text-sm border transition ${
                stack === option.id
                  ? 'bg-emerald-500/20 border-emerald-400 text-emerald-200'
                  : 'border-emerald-500/20 text-gray-300 hover:text-emerald-100 hover:border-emerald-400/60'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        {stack === 'js' && (
          <div className="space-y-4">
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">routes/rateLimiter.js</div>
              <pre className="whitespace-pre-wrap">{`const express = require('express');

const router = express.Router();

router.get('/.well-known/ratelimiter-verify', (req, res) => {
  const token = req.query.token || '';
  res.type('text/plain');
  res.send(token);
});

module.exports = router;`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">server.js</div>
              <pre className="whitespace-pre-wrap">{`const rateLimiterRoutes = require('./routes/rateLimiter');

app.use(rateLimiterRoutes);`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Verify locally</div>
              <pre className="whitespace-pre-wrap">{`curl -sS "https://your-backend/.well-known/ratelimiter-verify?token=YOUR_TOKEN"`}</pre>
            </div>
          </div>
        )}

        {stack === 'ts' && (
          <div className="space-y-4">
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">routes/rateLimiter.ts</div>
              <pre className="whitespace-pre-wrap">{`import { Router, Request, Response } from 'express';

const router = Router();

router.get('/.well-known/ratelimiter-verify', (req: Request, res: Response) => {
  const token = (req.query.token as string) || '';
  res.type('text/plain');
  res.send(token);
});

export default router;`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">server.ts</div>
              <pre className="whitespace-pre-wrap">{`import rateLimiterRoutes from './routes/rateLimiter';

app.use(rateLimiterRoutes);`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Verify locally</div>
              <pre className="whitespace-pre-wrap">{`curl -sS "https://your-backend/.well-known/ratelimiter-verify?token=YOUR_TOKEN"`}</pre>
            </div>
          </div>
        )}

        {stack === 'other' && (
          <div className="space-y-4">
            <ul className="text-gray-300 space-y-2 list-disc list-inside">
              <li>Add a GET route at <span className="text-emerald-300">/.well-known/ratelimiter-verify</span>.</li>
              <li>Read the <span className="text-emerald-300">token</span> query param and return it as plain text.</li>
              <li>Confirm it works with: <span className="text-emerald-300">curl -sS "https://your-backend/.well-known/ratelimiter-verify?token=YOUR_TOKEN"</span>.</li>
            </ul>
          </div>
        )}
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">5. Configure rate limits</h2>
        <p className="text-gray-300">
          Create endpoints under <span className="text-emerald-300">Endpoints</span>, then add a rate limit
          configuration for each endpoint. You can only have one configuration per endpoint.
        </p>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">6. Add the rate limit check</h2>
        <p className="text-gray-300">
          Add a middleware that calls the rate limiter before your routes.
          If the response is 429, return it to the client. Otherwise, continue.
        </p>
        <div className="flex flex-wrap gap-2">
          {stackOptions.map((option) => (
            <button
              key={`stack-${option.id}`}
              type="button"
              onClick={() => setStack(option.id)}
              className={`px-4 py-2 rounded-lg text-sm border transition ${
                stack === option.id
                  ? 'bg-emerald-500/20 border-emerald-400 text-emerald-200'
                  : 'border-emerald-500/20 text-gray-300 hover:text-emerald-100 hover:border-emerald-400/60'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
        {stack === 'js' && (
          <div className="space-y-4">
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Set environment variables</div>
              <pre className="whitespace-pre-wrap">{`RATELIMITER_URL=<URL FROM ENV FILE>
RATELIMITER_API_KEY=<API KEY FROM ENV FILE>`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Create rateLimit.js</div>
              <pre className="whitespace-pre-wrap">{`const checkRateLimit = async (req) => {
  const response = await fetch(
    \`<process.env.RATELIMITER_URL>/api/v1/ratelimit/check\`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': <process.env.RATELIMITER_API_KEY>,
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

module.exports = { rateLimitGuard };`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Where to add it</div>
              <pre className="whitespace-pre-wrap">{`// server.js
const { rateLimitGuard } = require('./rateLimit');
app.use(rateLimitGuard);
app.use('/health', healthRoutes);`}</pre>
            </div>
          </div>
        )}

        {stack === 'ts' && (
          <div className="space-y-4">
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Set environment variables</div>
              <pre className="whitespace-pre-wrap">{`RATELIMITER_URL=<URL FROM ENV FILE>
RATELIMITER_API_KEY=<API KEY FROM ENV FILE>`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Create rateLimit.ts</div>
              <pre className="whitespace-pre-wrap">{`import { Request, Response, NextFunction } from 'express';

const checkRateLimit = async (req: Request) => {
  const response = await fetch(
    \`<process.env.RATELIMITER_URL>/api/v1/ratelimit/check\`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': <process.env.RATELIMITER_API_KEY> || '',
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

export const rateLimitGuard = async (
  req: Request,
  res: Response,
  next: NextFunction
) => {
  const result = await checkRateLimit(req);
  if (!result.allowed) {
    return res.status(429).json(result.data || { message: 'Rate limit exceeded' });
  }
  return next();
};`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Where to add it</div>
              <pre className="whitespace-pre-wrap">{`// server.ts
import { rateLimitGuard } from './rateLimit';
app.use(rateLimitGuard);
app.use('/health', healthRoutes);`}</pre>
            </div>
          </div>
        )}

        {stack === 'other' && (
          <div className="space-y-4 text-gray-300">
            <p>
              Use <span className="text-emerald-300">RATELIMITER_URL</span> and
              <span className="text-emerald-300">RATELIMITER_API_KEY</span> from your env file to call
              <span className="text-emerald-300"> /api/v1/ratelimit/check</span> before serving each request.
            </p>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Before / After (route)</div>
              <pre className="whitespace-pre-wrap">{`// Before
GET /health -> return JSON

// After
Call rate limiter, if 429 return error, else return JSON`}</pre>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
