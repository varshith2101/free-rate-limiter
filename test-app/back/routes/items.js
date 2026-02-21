const express = require("express");

const router = express.Router();

let items = [
  { id: "a1", name: "Token Bucket", price: 12.5 },
  { id: "b2", name: "Leaky Bucket", price: 9.75 },
  { id: "c3", name: "Rate Limit", price: 15.0 }
];

router.get("/", (req, res) => {
  res.json({ data: items });
});

router.get("/:id", (req, res) => {
  const item = items.find((i) => i.id === req.params.id);
  if (!item) {
    return res.status(404).json({ error: "Item not found" });
  }
  res.json({ data: item });
});

router.post("/", (req, res) => {
  const { id, name, price } = req.body || {};
  if (!id || !name || typeof price !== "number") {
    return res.status(400).json({ error: "id, name, and numeric price are required" });
  }
  if (items.find((i) => i.id === id)) {
    return res.status(409).json({ error: "Item already exists" });
  }
  const item = { id, name, price };
  items.push(item);
  res.status(201).json({ data: item });
});

router.patch("/:id", (req, res) => {
  const item = items.find((i) => i.id === req.params.id);
  if (!item) {
    return res.status(404).json({ error: "Item not found" });
  }
  const { name, price } = req.body || {};
  if (name) item.name = name;
  if (typeof price === "number") item.price = price;
  res.json({ data: item });
});

module.exports = router;
