const express = require("express");

const router = express.Router();

router.get("/", (req, res) => {
  res.json({
    status: "ok",
    limit: 100,
    windowSeconds: 60,
    remaining: 87
  });
});

router.post("/", (req, res) => {
  const { key, cost } = req.body || {};
  if (!key) {
    return res.status(400).json({ error: "key is required" });
  }
  res.json({
    key,
    cost: typeof cost === "number" ? cost : 1,
    allowed: true,
    remaining: 42
  });
});

module.exports = router;
