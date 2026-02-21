export type User = { id: number; name: string; role: string };
export type Item = { id: string; name: string; price: number };

export async function fetchHealth() {
  const res = await fetch("/api/health");
  if (!res.ok) throw new Error("Health check failed");
  return res.json();
}

export async function fetchUsers() {
  const res = await fetch("/api/users");
  if (!res.ok) throw new Error("Users fetch failed");
  return res.json() as Promise<{ data: User[] }>;
}

export async function fetchItems() {
  const res = await fetch("/api/items");
  if (!res.ok) throw new Error("Items fetch failed");
  return res.json() as Promise<{ data: Item[] }>;
}

export async function postEcho(payload: unknown) {
  const res = await fetch("/api/echo", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error("Echo failed");
  return res.json();
}

export async function checkRate(key: string, cost = 1) {
  const res = await fetch("/api/rate", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ key, cost })
  });
  if (!res.ok) throw new Error("Rate check failed");
  return res.json();
}
