import express from "express";
import cors from "cors";
import { config } from "./config";
import { errorHandler } from "./middleware/error";
import authRouter from "./routes/auth";
import manageRouter from "./routes/manage";
import rateLimitRouter from "./routes/ratelimit";
import adminRouter from "./routes/admin";

const app = express();

app.use(cors());
app.use(express.json({ limit: "1mb" }));

app.get("/api/v1/health", (_req, res) => res.send("OK"));

app.use("/api/v1/auth", authRouter);
app.use("/api/v1/manage", manageRouter);
app.use("/api/v1/ratelimit", rateLimitRouter);
app.use("/api/v1/admin", adminRouter);

app.use(errorHandler);

app.listen(config.port, () => {
  console.log(`Rate limiter API listening on ${config.port}`);
});
