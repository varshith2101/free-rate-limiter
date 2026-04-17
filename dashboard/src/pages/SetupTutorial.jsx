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
          Follow these steps in order. Each step includes a quick "done check" so you know exactly what to verify before moving on.
        </p>
      </div>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Step 1. Start the platform</h2>
        <p className="text-gray-300">
          Run this project first. The gateway exposes the app at <span className="text-emerald-300">http://localhost</span>
          and the check endpoint is <span className="text-emerald-300">/api/v1/ratelimit/check</span>.
        </p>
        <div className="neo-panel p-4 text-sm text-gray-300 space-y-2">
          <div className="text-gray-400">From this repo root:</div>
          <pre className="whitespace-pre-wrap">{`./launch-services.sh up`}</pre>
          <div>
            Done check: open <span className="text-emerald-300">http://localhost/actuator/health</span> and make sure it responds.
          </div>
        </div>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Step 2. Create an API key</h2>
        <p className="text-gray-300">
          In the dashboard, open <span className="text-emerald-300">API</span> from the user menu and create a key.
          Copy it immediately, because the full key is shown only once.
        </p>
        <div className="neo-panel p-4 text-sm text-gray-300">
          Done check: you have a value saved as <span className="text-emerald-300">RATELIMITER_API_KEY</span>.
        </div>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Step 3. Add env vars to your backend</h2>
        <p className="text-gray-300">
          In your backend app environment (usually <span className="text-emerald-300">.env</span>), add:
        </p>
        <div className="neo-panel p-4 text-sm text-gray-300 space-y-2">
          <div>RATELIMITER_URL=&lt;your rate limiter base URL&gt;</div>
          <div>RATELIMITER_API_KEY=&lt;your generated key&gt;</div>
          <div className="text-gray-400">Local default:</div>
          <pre className="whitespace-pre-wrap">{`RATELIMITER_URL=http://localhost`}</pre>
          <div>
            Done check: your app process can read both env vars.
          </div>
        </div>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Step 4. Add backend ownership verification route</h2>
        <p className="text-gray-300">
          Add this route on your backend so the dashboard can verify you own that backend URL.
          It must return the <span className="text-emerald-300">token</span> query value as plain text.
        </p>
        <div className="flex flex-wrap gap-2">
          {stackOptions.map((option) => (
            <button
              key={option.id}
              type="button"
              onClick={() => setStack(option.id)}
              className={`px-4 py-2 rounded-lg text-sm border transition ${
                stack === option.id
                  ? 'bg-[rgba(201,136,97,0.18)] border-[rgba(224,167,124,0.72)] text-[rgba(242,201,172,1)]'
                  : 'border-[rgba(203,151,113,0.34)] text-gray-300 hover:text-[rgba(238,188,154,1)] hover:border-[rgba(224,167,124,0.62)]'
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
              <div className="mb-2 text-gray-400">Done check</div>
              <pre className="whitespace-pre-wrap">{`curl -sS "http://localhost:4000/.well-known/ratelimiter-verify?token=abc123"
# expected output: abc123`}</pre>
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
              <div className="mb-2 text-gray-400">Done check</div>
              <pre className="whitespace-pre-wrap">{`curl -sS "http://localhost:4000/.well-known/ratelimiter-verify?token=abc123"
# expected output: abc123`}</pre>
            </div>
          </div>
        )}

        {stack === 'other' && (
          <div className="space-y-4">
            <ul className="text-gray-300 space-y-2 list-disc list-inside">
              <li>Add a GET route at <span className="text-emerald-300">/.well-known/ratelimiter-verify</span>.</li>
              <li>Read the <span className="text-emerald-300">token</span> query param and return it as plain text.</li>
              <li>Confirm it works with: <span className="text-emerald-300">curl -sS "http://your-backend/.well-known/ratelimiter-verify?token=abc123"</span> and verify the output is <span className="text-emerald-300">abc123</span>.</li>
            </ul>
          </div>
        )}
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Step 5. Verify backend link in dashboard</h2>
        <p className="text-gray-300">
          In <span className="text-emerald-300">Backend Links</span>, add your backend base URL and click Verify.
          The platform will call your verification route with a token.
        </p>
        <div className="neo-panel p-4 text-sm text-gray-300">
          Done check: your backend link shows <span className="text-emerald-300">Verified</span>.
        </div>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Step 6. Create endpoints and configs</h2>
        <p className="text-gray-300">
          Create endpoints under <span className="text-emerald-300">Endpoints</span>, then add a rate limit
          configuration for each endpoint. You can only have one configuration per endpoint.
        </p>
        <div className="neo-panel p-4 text-sm text-gray-300">
          Done check: at least one endpoint has an active rate-limit config.
        </div>
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Step 7. Add rate-limit middleware in your backend</h2>
        <p className="text-gray-300">
          Add middleware that calls <span className="text-emerald-300">POST /api/v1/ratelimit/check</span>
          before your protected routes. If the response is denied, return 429 immediately.
        </p>
        <div className="flex flex-wrap gap-2">
          {stackOptions.map((option) => (
            <button
              key={`stack-${option.id}`}
              type="button"
              onClick={() => setStack(option.id)}
              className={`px-4 py-2 rounded-lg text-sm border transition ${
                stack === option.id
                  ? 'bg-[rgba(201,136,97,0.18)] border-[rgba(224,167,124,0.72)] text-[rgba(242,201,172,1)]'
                  : 'border-[rgba(203,151,113,0.34)] text-gray-300 hover:text-[rgba(238,188,154,1)] hover:border-[rgba(224,167,124,0.62)]'
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
    \`${process.env.RATELIMITER_URL}/api/v1/ratelimit/check\`,
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

  const data = await response.json().catch(() => ({}));
  if (data && data.allowed === false) {
    return { allowed: false, data };
  }

  return { allowed: true, data };
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
              <div className="mb-2 text-gray-400">Register middleware</div>
              <pre className="whitespace-pre-wrap">{`// server.js
const { rateLimitGuard } = require('./rateLimit');
app.use(rateLimitGuard);
app.use('/health', healthRoutes);`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Done check</div>
              <div>
                Send repeated requests to a configured endpoint and verify you start receiving 429 responses.
              </div>
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
    \`${process.env.RATELIMITER_URL}/api/v1/ratelimit/check\`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': process.env.RATELIMITER_API_KEY || '',
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

  const data = await response.json().catch(() => ({}));
  if (data && data.allowed === false) {
    return { allowed: false, data };
  }

  return { allowed: true, data };
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
              <div className="mb-2 text-gray-400">Register middleware</div>
              <pre className="whitespace-pre-wrap">{`// server.ts
import { rateLimitGuard } from './rateLimit';
app.use(rateLimitGuard);
app.use('/health', healthRoutes);`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Done check</div>
              <div>
                Send repeated requests to a configured endpoint and verify you start receiving 429 responses.
              </div>
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
              <div className="mb-2 text-gray-400">Rule</div>
              <pre className="whitespace-pre-wrap">{`// Before
GET /health -> return JSON

// After
Call rate limiter first.
If denied (allowed=false or status=429), return 429.
If allowed, continue to route handler.`}</pre>
            </div>
            <div className="neo-panel p-4 text-sm text-gray-300">
              <div className="mb-2 text-gray-400">Done check</div>
              <div>
                A low-limit endpoint is blocked after repeated calls, while normal traffic to other endpoints still works.
              </div>
            </div>
          </div>
        )}
      </section>

      <section className="neo-card p-6 space-y-4">
        <h2 className="text-xl font-semibold text-white">Quick troubleshooting</h2>
        <div className="neo-panel p-4 text-sm text-gray-300 space-y-2">
          <div>1) If all requests are allowed: check that endpoint path + HTTP method match your configured endpoint exactly.</div>
          <div>2) If all requests fail: verify <span className="text-emerald-300">RATELIMITER_URL</span> and <span className="text-emerald-300">RATELIMITER_API_KEY</span>.</div>
          <div>3) If backend link stays pending: verify your <span className="text-emerald-300">/.well-known/ratelimiter-verify</span> route returns token as plain text.</div>
          <div>4) If needed, check platform health at <span className="text-emerald-300">/actuator/health</span>.</div>
        </div>
      </section>
    </div>
  );
}
