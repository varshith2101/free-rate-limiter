require("dotenv").config();
const express = require("express");
const cors = require("cors");
const morgan = require("morgan");

const healthRoutes = require("./routes/health");
const usersRoutes = require("./routes/users");
const itemsRoutes = require("./routes/items");
const echoRoutes = require("./routes/echo");
const rateRoutes = require("./routes/rate");

const app = express();
const PORT = process.env.PORT || 4000;

// Core middleware
app.use(cors());
app.use(morgan("dev"));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Simple request-id middleware
app.use((req, res, next) => {
  req.requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  res.setHeader("x-request-id", req.requestId);
  next();
});


const { rateLimitGuard } = require('./middleware/rateLimit');
app.use(rateLimitGuard);


// Routes
app.use("/api/health", healthRoutes);
app.use("/api/users", usersRoutes);
app.use("/api/items", itemsRoutes);
app.use("/api/echo", echoRoutes);
app.use("/api/rate", rateRoutes);

const rateLimiterRoutes = require("./routes/rateLimiter");

app.use("/.well-known", rateLimiterRoutes);

app.get("/", (req, res) => {
  res.json({
    name: "test-app-back",
    status: "ok",
    endpoints: [
      "/api/health",
      "/api/users",
      "/api/items",
      "/api/echo",
      "/api/rate",
      "/.well-known/ratelimiter-verify"
    ]
  });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({
    error: "Not Found",
    path: req.originalUrl,
    requestId: req.requestId
  });
});

// Error handler
app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({
    error: "Server Error",
    message: err.message,
    requestId: req.requestId
  });
});

app.listen(PORT, () => {
  console.log(`test-app-back listening on http://localhost:${PORT}`);
});
