import { useState, useEffect } from 'react';
import { Plus, Check, X, Copy, RefreshCw } from 'lucide-react';
import { API_BASE_URL } from '../api/client';

export default function BackendLinks() {
  const [backendLinks, setBackendLinks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [backendUrl, setBackendUrl] = useState('');
  const [nickname, setNickname] = useState('');
  const [accentColor, setAccentColor] = useState('#22C55E');
  const [error, setError] = useState('');

  const colorOptions = [
    { value: '#22C55E', label: 'Green' },
    { value: '#4ADE80', label: 'Mint' },
    { value: '#16A34A', label: 'Forest' },
    { value: '#84CC16', label: 'Lime' },
    { value: '#65A30D', label: 'Olive' },
    { value: '#A3E635', label: 'Neon' },
  ];

  useEffect(() => {
    fetchBackendLinks();
  }, []);

  const fetchBackendLinks = async () => {
    const token = localStorage.getItem('token');
    try {
      const response = await fetch(`${API_BASE_URL}/auth/backend-links`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (response.ok) {
        const data = await response.json();
        setBackendLinks(data);
      }
    } catch (err) {
      setError('Failed to fetch backend links');
    } finally {
      setLoading(false);
    }
  };

  const handleAddBackendLink = async (e) => {
    e.preventDefault();
    const token = localStorage.getItem('token');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/backend-links`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ backendUrl, nickname, accentColor }),
      });

      if (!response.ok) throw new Error('Failed to create backend link');
      const data = await response.json();
      alert(`Verification Setup:\n1) Serve the token at:\n${backendUrl}${data.verificationPath}\n2) Response must include the token\n3) Click "Verify" here.`);
      
      setBackendUrl('');
      setNickname('');
      setAccentColor('#22C55E');
      setShowForm(false);
      fetchBackendLinks();
    } catch (err) {
      setError(err.message);
    }
  };

  const handleDeleteLink = async (linkId) => {
    const token = localStorage.getItem('token');
    try {
      const response = await fetch(`${API_BASE_URL}/auth/backend-links/${linkId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });

      if (response.ok) {
        fetchBackendLinks();
      }
    } catch (err) {
      setError('Failed to delete link');
    }
  };

  const handleVerifyLink = async (linkId) => {
    const token = localStorage.getItem('token');
    try {
      const response = await fetch(`${API_BASE_URL}/auth/backend-links/${linkId}/verify`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });

      if (!response.ok) {
        let message = 'Verification failed';
        try {
          const data = await response.json();
          if (data?.message) {
            message = data.message;
          }
        } catch (_) {
          // Keep default message when response is not JSON.
        }
        throw new Error(message);
      }
      fetchBackendLinks();
    } catch (err) {
      setError(err.message);
    }
  };

  const handleCopy = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
    } catch (err) {
      setError('Failed to copy');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-3xl font-bold text-white">Backend Links</h1>
        <button
          onClick={() => setShowForm(!showForm)}
          className="neo-button"
        >
          <Plus className="w-4 h-4" /> Add Backend
        </button>
      </div>

      {error && (
        <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-lg">
          <p className="text-red-400">{error}</p>
        </div>
      )}

      {showForm && (
        <div className="neo-card p-6">
          <h2 className="text-xl font-bold text-white mb-4">Add Backend Link</h2>
          <form onSubmit={handleAddBackendLink}>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">Backend URL</label>
                <input
                  type="url"
                  value={backendUrl}
                  onChange={(e) => setBackendUrl(e.target.value)}
                  placeholder="https://api.example.com"
                  className="neo-input"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">Project nickname</label>
                <input
                  type="text"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  placeholder="Acme API"
                  className="neo-input"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">Accent color</label>
                <div className="flex flex-wrap gap-2">
                  {colorOptions.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      onClick={() => setAccentColor(option.value)}
                      className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-sm transition ${
                        accentColor === option.value
                          ? 'border-white text-white'
                          : 'border-slate-600 text-gray-300 hover:border-slate-400'
                      }`}
                    >
                      <span
                        className="h-3 w-3 rounded-full"
                        style={{ backgroundColor: option.value }}
                      />
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex gap-2">
                <button
                  type="submit"
                  className="neo-button"
                >
                  Create Link
                </button>
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="neo-button-ghost"
                >
                  Cancel
                </button>
              </div>
            </div>
          </form>
        </div>
      )}

      {loading ? (
        <div className="text-center py-12">
          <div className="w-12 h-12 border-4 border-emerald-500/30 border-t-emerald-300 rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-400">Loading backend links...</p>
        </div>
      ) : backendLinks.length === 0 ? (
        <div className="text-center py-12 neo-panel">
          <p className="text-gray-400">No backend links yet. Add one to get started!</p>
        </div>
      ) : (
        <div className="grid gap-4">
          {backendLinks.map((link) => (
            <div
              key={link.id}
              className="neo-card p-6 hover:border-emerald-400/40 transition"
            >
              <div className="flex justify-between items-start mb-4">
                <div>
                  <p className="text-gray-300 text-sm">URL</p>
                  <p className="text-white font-mono text-sm mt-1">{link.backendUrl}</p>
                  <div className="mt-2 inline-flex items-center gap-2 px-2 py-1 rounded-full text-xs font-medium"
                    style={{ backgroundColor: `${link.accentColor}22`, color: link.accentColor }}
                  >
                    <span className="h-2 w-2 rounded-full" style={{ backgroundColor: link.accentColor }} />
                    {link.nickname}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  {link.isVerified ? (
                    <div className="flex items-center gap-1 bg-green-500/10 text-green-400 px-3 py-1 rounded-full">
                      <Check className="w-4 h-4" /> Verified
                    </div>
                  ) : (
                    <div className="flex items-center gap-1 bg-yellow-500/10 text-yellow-400 px-3 py-1 rounded-full">
                      <X className="w-4 h-4" /> Pending
                    </div>
                  )}
                </div>
              </div>
              <div className="mt-4 grid gap-2">
                <div className="neo-panel px-3 py-2">
                  <div>
                    <p className="text-xs text-gray-400">Verification URL</p>
                    <p className="text-xs text-emerald-300 break-all">
                      {link.backendUrl}/.well-known/ratelimiter-verify?token={link.verificationToken}
                    </p>
                  </div>
                  <button
                    onClick={() => handleCopy(`${link.backendUrl}/.well-known/ratelimiter-verify?token=${link.verificationToken}`)}
                    className="text-gray-400 hover:text-white transition"
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                </div>

                <div className="flex gap-3">
                  <button
                    onClick={() => handleVerifyLink(link.id)}
                    className="text-emerald-300 hover:text-emerald-200 transition text-sm flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" /> Verify
                  </button>
                  <button
                    onClick={() => handleDeleteLink(link.id)}
                    className="text-red-400 hover:text-red-300 transition text-sm"
                  >
                    Delete
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="neo-card p-6 mt-8">
        <h3 className="text-lg font-bold text-white mb-3">How Backend Verification Works</h3>
        <ol className="text-gray-300 space-y-2 text-sm list-decimal list-inside">
          <li>Create a backend link with your backend URL</li>
          <li>Expose a verification endpoint on your backend</li>
          <li>Endpoint must return the token in the response</li>
          <li>Click Verify in this UI to complete ownership check</li>
        </ol>
      </div>
    </div>
  );
}
