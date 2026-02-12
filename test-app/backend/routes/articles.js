const express = require('express');
const { readDatabase, writeDatabase } = require('../db/database');

function createArticlesRouter(options) {
  const router = express.Router();
  const { databasePath, ownerName, ownerPassword } = options;

  const requireOwnerAuth = (req, res, next) => {
    const requestOwner = req.header('x-owner-name');
    const requestPassword = req.header('x-owner-password');

    if (requestOwner !== ownerName || requestPassword !== ownerPassword) {
      return res.status(401).json({
        message: 'Invalid owner credentials',
      });
    }

    return next();
  };

  router.get('/', async (_req, res, next) => {
    try {
      const db = await readDatabase(databasePath);
      return res.json(db.articles || []);
    } catch (error) {
      return next(error);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const db = await readDatabase(databasePath);
      const article = (db.articles || []).find((entry) => entry.id === req.params.id);

      if (!article) {
        return res.status(404).json({ message: 'Article not found' });
      }

      return res.json(article);
    } catch (error) {
      return next(error);
    }
  });

  router.post('/', requireOwnerAuth, async (req, res, next) => {
    try {
      const { title, content } = req.body || {};
      if (!title || !content) {
        return res.status(400).json({ message: 'title and content are required' });
      }

      const db = await readDatabase(databasePath);
      const articles = db.articles || [];
      const now = new Date().toISOString();
      const article = {
        id: `art_${Date.now()}`,
        title: String(title).trim(),
        content: String(content).trim(),
        author: ownerName,
        createdAt: now,
        updatedAt: now,
      };

      articles.unshift(article);
      await writeDatabase(databasePath, { articles });

      return res.status(201).json(article);
    } catch (error) {
      return next(error);
    }
  });

  return router;
}

module.exports = {
  createArticlesRouter,
};
