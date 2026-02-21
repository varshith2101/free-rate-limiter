const express = require("express");

const router = express.Router();

let users = [
  { id: 1, name: "Avery", role: "admin" },
  { id: 2, name: "Jordan", role: "editor" },
  { id: 3, name: "Kai", role: "viewer" }
];

router.get("/", (req, res) => {
  res.json({ data: users });
});

router.get("/:id", (req, res) => {
  const id = Number(req.params.id);
  const user = users.find((u) => u.id === id);
  if (!user) {
    return res.status(404).json({ error: "User not found" });
  }
  res.json({ data: user });
});

router.post("/", (req, res) => {
  const { name, role } = req.body || {};
  if (!name || !role) {
    return res.status(400).json({ error: "name and role are required" });
  }
  const id = users.length ? users[users.length - 1].id + 1 : 1;
  const user = { id, name, role };
  users.push(user);
  res.status(201).json({ data: user });
});

router.delete("/:id", (req, res) => {
  const id = Number(req.params.id);
  const before = users.length;
  users = users.filter((u) => u.id !== id);
  if (users.length === before) {
    return res.status(404).json({ error: "User not found" });
  }
  res.json({ deleted: id });
});

module.exports = router;
