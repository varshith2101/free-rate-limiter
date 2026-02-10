import { useEffect, useMemo, useState } from 'react';
import { Copy, Plus, Trash2 } from 'lucide-react';
import { api } from '../api/client';

const sessionKey = 'apiKeySessionCache';

const readSessionCache = () => {
  try {
    const raw = sessionStorage.getItem(sessionKey);
    return raw ? JSON.parse(raw) : {};
  } catch (error) {
    return {};
  }
};

const writeSessionCache = (cache) => {
  sessionStorage.setItem(sessionKey, JSON.stringify(cache));
};

export default function ApiKeys() {
  const [apiKeys, setApiKeys] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [name, setName] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [neverExpire, setNeverExpire] = useState(false);
  const [sessionCache, setSessionCache] = useState(readSessionCache());

  const cachedKeys = useMemo(() => sessionCache || {}, [sessionCache]);

  useEffect(() => {
    fetchApiKeys();
  }, []);

  const fetchApiKeys = async () => {
    try {
      setLoading(true);
      const response = await api.getApiKeys();
      setApiKeys(response.data);
    } catch (err) {
      setError('Failed to load API keys');
    } finally {
      setLoading(false);
    }
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    setError('');

    try {
      const response = await api.createApiKey({
        name,
        expiresAt: neverExpire ? null : expiresAt || null,
      });

      const { apiKey, summary } = response.data;
      const nextCache = {
        ...cachedKeys,
        [summary.id]: apiKey,
      };

      setSessionCache(nextCache);
      writeSessionCache(nextCache);
      setName('');
      setExpiresAt('');
      setNeverExpire(false);
      await fetchApiKeys();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create API key');
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Deactivate this API key?')) {
      return;
    }

    try {
      await api.deleteApiKey(id);
      const nextCache = { ...cachedKeys };
      delete nextCache[id];
      setSessionCache(nextCache);
      writeSessionCache(nextCache);
      await fetchApiKeys();
    } catch (err) {
      setError('Failed to delete API key');
    }
  };

  const handleCopy = async (value) => {
    try {
      await navigator.clipboard.writeText(value);
    } catch (err) {
      setError('Failed to copy');
    }
  };

  return (
    <div className="px-4 py-6 sm:px-0 space-y-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold text-white">API Keys</h1>
        <p className="text-gray-400">
          Create and manage API keys for your tenant. Keys are only shown once.
        </p>
      </div>

      {error && (
        <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-lg">
          <p className="text-red-300">{error}</p>
        </div>
      )}

      <form
        onSubmit={handleCreate}
        className="neo-card p-6 space-y-4"
      >
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">
            API key name
          </label>
          <input
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Production backend"
            className="neo-input"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">
            Expiration (optional)
          </label>
          <input
            type="datetime-local"
            value={expiresAt}
            onChange={(event) => setExpiresAt(event.target.value)}
            disabled={neverExpire}
            className="neo-input"
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-300">
          <input
            type="checkbox"
            checked={neverExpire}
            onChange={(event) => setNeverExpire(event.target.checked)}
            className="h-4 w-4 rounded border-emerald-500/30 bg-[#0a0f0c] text-emerald-500 focus:ring-emerald-500"
          />
          Never expire
        </label>
        <button
          type="submit"
          className="neo-button"
        >
          <Plus className="w-4 h-4" /> Create API Key
        </button>
      </form>

      {loading ? (
        <div className="text-center py-12">
          <div className="w-12 h-12 border-4 border-emerald-500/30 border-t-emerald-300 rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-400">Loading API keys...</p>
        </div>
      ) : apiKeys.length === 0 ? (
        <div className="text-center py-12 neo-panel">
          <p className="text-gray-400">No API keys yet. Create one to start.</p>
        </div>
      ) : (
        <div className="grid gap-4">
          {apiKeys.map((key) => {
            const keyValue = cachedKeys[key.id];
            return (
              <div
                key={key.id}
                className="neo-card p-6 space-y-4"
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-gray-300 text-sm">Name</p>
                    <p className="text-white font-semibold">{key.name}</p>
                    <p className="text-gray-400 text-xs mt-1">Prefix: {key.prefix}</p>
                  </div>
                  <button
                    onClick={() => handleDelete(key.id)}
                    className="text-red-400 hover:text-red-300 transition"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>

                <div className="neo-panel px-3 py-2">
                  <p className="text-xs text-gray-400">API key</p>
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-xs text-emerald-300 break-all">
                      {keyValue || 'Key hidden after initial create'}
                    </p>
                    {keyValue && (
                      <button
                        onClick={() => handleCopy(keyValue)}
                        className="text-gray-400 hover:text-white transition"
                      >
                        <Copy className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>

                <div className="text-xs text-gray-400">
                  Created: {key.createdAt ? new Date(key.createdAt).toLocaleString() : '—'}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
