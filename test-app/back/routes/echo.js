const express = require("express");

const router = express.Router();

router.all("/", (req, res) => {
  res.json({
    method: req.method,
    query: req.query,
    body: req.body,
    headers: {
      "content-type": req.headers["content-type"],
      "user-agent": req.headers["user-agent"]
    },
    requestId: req.requestId
  });
});

module.exports = router;
