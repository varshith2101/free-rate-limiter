import { useEffect, useState } from "react";
import {
  fetchHealth,
  fetchItems,
  fetchUsers,
  postEcho,
  checkRate,
  type Item,
  type User
} from "./api";

export default function App() {
  const [health, setHealth] = useState<string>("loading...");
  const [users, setUsers] = useState<User[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [echo, setEcho] = useState<string>("");
  const [rate, setRate] = useState<string>("");
  const [error, setError] = useState<string>("");

  useEffect(() => {
    Promise.all([fetchHealth(), fetchUsers(), fetchItems()])
      .then(([healthData, usersData, itemsData]) => {
        setHealth(`${healthData.status} @ ${healthData.time}`);
        setUsers(usersData.data);
        setItems(itemsData.data);
      })
      .catch((err) => setError(err.message));
  }, []);

  async function handleEcho() {
    setError("");
    try {
      const data = await postEcho({ message: "hello", time: Date.now() });
      setEcho(JSON.stringify(data, null, 2));
    } catch (err) {
      setError((err as Error).message);
    }
  }

  async function handleRate() {
    setError("");
    try {
      const data = await checkRate("frontend-demo", 2);
      setRate(JSON.stringify(data, null, 2));
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <div className="app">
      <header className="header">
        <h1>Test App Front</h1>
        <p>React + TypeScript client for the test backend</p>
      </header>

      <section className="card">
        <h2>Health</h2>
        <p>{health}</p>
      </section>

      <section className="grid">
        <div className="card">
          <h2>Users</h2>
          <ul>
            {users.map((u) => (
              <li key={u.id}>
                <strong>{u.name}</strong> — {u.role}
              </li>
            ))}
          </ul>
        </div>

        <div className="card">
          <h2>Items</h2>
          <ul>
            {items.map((i) => (
              <li key={i.id}>
                <strong>{i.name}</strong> — ${i.price.toFixed(2)}
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="card">
        <h2>Echo</h2>
        <button onClick={handleEcho}>Send Echo</button>
        {echo && <pre>{echo}</pre>}
      </section>

      <section className="card">
        <h2>Rate Check</h2>
        <button onClick={handleRate}>Check Rate</button>
        {rate && <pre>{rate}</pre>}
      </section>

      {error && (
        <section className="card error">
          <h2>Error</h2>
          <pre>{error}</pre>
        </section>
      )}
    </div>
  );
}
