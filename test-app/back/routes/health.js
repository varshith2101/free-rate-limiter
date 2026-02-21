const express = require("express");

const router = express.Router();

router.get("/", (req, res) => {
  res.json({
    status: "ok",
    time: new Date().toISOString(),
    requestId: req.requestId
  });
});

module.exports = router;
