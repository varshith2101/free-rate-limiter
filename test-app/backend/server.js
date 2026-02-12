const path = require('path');
const express = require('express');
const dotenv = require('dotenv');
const {
  resolveDatabasePath,
  initializeDatabase,
} = require('./db/database');
const { createArticlesRouter } = require('./routes/articles');

dotenv.config({ path: path.join(__dirname, '..', '.env') });

const app = express();
const port = Number(process.env.PORT || 8090);
const ownerName = process.env.OWNER_NAME || 'owner';
const ownerPassword = process.env.OWNER_PASSWORD || 'owner-password';
const databasePath = resolveDatabasePath(process.env.DATABASE_URL);

app.use(express.json());

// Normalize duplicate slashes in incoming paths so verification works
// even if upstream callers build URLs with a trailing slash base URL.
app.use((req, _res, next) => {
  const [pathPart, queryPart] = req.url.split('?');
  const normalizedPath = pathPart.replace(/\/{2,}/g, '/');
  req.url = queryPart ? `${normalizedPath}?${queryPart}` : normalizedPath;
  next();
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

app.use(
  '/api/articles',
  createArticlesRouter({
    databasePath,
    ownerName,
    ownerPassword,
  })
);


const rateLimiterRoutes = require('./routes/rateLimiter');

app.use(rateLimiterRoutes);


app.use('/test-app', express.static(path.join(__dirname, '..', 'frontend')));
app.use('/', express.static(path.join(__dirname, '..', 'frontend')));

app.get(['/test-app/*', '*'], (req, res, next) => {
  if (req.path.startsWith('/api/') || req.path === '/health') {
    return next();
  }

  return res.sendFile(path.join(__dirname, '..', 'frontend', 'index.html'));
});

app.use((error, _req, res, _next) => {
  console.error('Test app error:', error);
  res.status(500).json({ message: 'Internal server error' });
});

initializeDatabase(databasePath, ownerName)
  .then(() => {
    app.listen(port, () => {
      console.log(`Test app running on http://localhost:${port}`);
    });
  })
  .catch((error) => {
    console.error('Failed to initialize database', error);
    process.exit(1);
  });
