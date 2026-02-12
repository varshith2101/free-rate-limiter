const express = require('express');

const router = express.Router();

router.get('/.well-known/ratelimiter-verify', (req, res) => {
  const token = req.query.token || '';
  res.type('text/plain');
  res.send(token);
});

module.exports = router;