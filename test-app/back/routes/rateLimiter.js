const express = require('express');

const router = express.Router();

router.get('/ratelimiter-verify', (req, res) => {
  const token = req.query.token || '';
  res.type('text/plain');
  res.send(token);
});

module.exports = router;
