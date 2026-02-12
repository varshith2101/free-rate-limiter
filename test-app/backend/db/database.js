const fs = require('fs/promises');
const path = require('path');

const DEFAULT_DB_PATH = path.join(__dirname, '..', '..', 'data', 'articles-db.json');

function resolveDatabasePath(rawDatabaseUrl) {
  if (!rawDatabaseUrl || rawDatabaseUrl.trim() === '') {
    return DEFAULT_DB_PATH;
  }

  if (rawDatabaseUrl.startsWith('file://')) {
    const fileUrl = new URL(rawDatabaseUrl);
    return fileUrl.pathname;
  }

  if (path.isAbsolute(rawDatabaseUrl)) {
    return rawDatabaseUrl;
  }

  return path.join(__dirname, '..', '..', rawDatabaseUrl);
}

function buildMockArticles(ownerName) {
  const author = ownerName || 'Test Owner';
  const now = new Date().toISOString();
  return [
    {
      id: 'art_1',
      title: 'Welcome to the Test App',
      content:
        'This seeded article exists so you can immediately test fetch and render behavior through the rate limiter.',
      author,
      createdAt: now,
      updatedAt: now,
    },
    {
      id: 'art_2',
      title: 'How to Use This Mock API',
      content:
        'Call GET /api/articles to fetch all records, then POST /api/articles to store new data and verify limits.',
      author,
      createdAt: now,
      updatedAt: now,
    },
    {
      id: 'art_3',
      title: 'Rate Limiter Validation Idea',
      content:
        'Run repeated read/write requests from this frontend while observing counters and blocking thresholds in your dashboard.',
      author,
      createdAt: now,
      updatedAt: now,
    },
  ];
}

async function ensureDirectory(filePath) {
  const dir = path.dirname(filePath);
  await fs.mkdir(dir, { recursive: true });
}

async function readDatabase(filePath) {
  const raw = await fs.readFile(filePath, 'utf8');
  return JSON.parse(raw);
}

async function writeDatabase(filePath, db) {
  await ensureDirectory(filePath);
  await fs.writeFile(filePath, JSON.stringify(db, null, 2), 'utf8');
}

async function initializeDatabase(filePath, ownerName) {
  try {
    const db = await readDatabase(filePath);
    if (!Array.isArray(db.articles) || db.articles.length === 0) {
      const seeded = {
        articles: buildMockArticles(ownerName),
      };
      await writeDatabase(filePath, seeded);
      return seeded;
    }
    return db;
  } catch (error) {
    const seeded = {
      articles: buildMockArticles(ownerName),
    };
    await writeDatabase(filePath, seeded);
    return seeded;
  }
}

module.exports = {
  resolveDatabasePath,
  initializeDatabase,
  readDatabase,
  writeDatabase,
};
