import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Edit, Trash2, Power, AlertCircle, Gauge, Radiation } from 'lucide-react';
import { api } from '../api/client';

export default function Endpoints() {
  const [endpoints, setEndpoints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [nukeOpen, setNukeOpen] = useState(false);
  const [nukeEndpoint, setNukeEndpoint] = useState(null);
  const [nukeTotal, setNukeTotal] = useState(500);
  const [nukeConcurrency, setNukeConcurrency] = useState(25);
  const [nukeStatus, setNukeStatus] = useState(null);
  const [nukeLogs, setNukeLogs] = useState([]);
  const [nukeRunning, setNukeRunning] = useState(false);
  const [analyticsDelta, setAnalyticsDelta] = useState(null);

  useEffect(() => {
    loadEndpoints();
  }, []);

  const loadEndpoints = async () => {
    try {
      setLoading(true);
      const response = await api.getEndpoints(true);
      setEndpoints(response.data);
    } catch (err) {
      console.error('Failed to load endpoints:', err);
      setError('Failed to load endpoints');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (endpointId) => {
    try {
      await api.toggleEndpoint(endpointId);
      await loadEndpoints(); // Reload to get updated status
    } catch (err) {
      console.error('Failed to toggle endpoint:', err);
      alert('Failed to toggle endpoint status');
    }
  };

  const handleDelete = async (endpointId) => {
    if (!confirm('Are you sure you want to delete this endpoint?')) {
      return;
    }

    try {
      await api.deleteEndpoint(endpointId);
      await loadEndpoints();
    } catch (err) {
      console.error('Failed to delete endpoint:', err);
      alert('Failed to delete endpoint');
    }
  };

  const openNukeTest = (endpoint) => {
    setNukeEndpoint(endpoint);
    setNukeStatus(null);
    setNukeLogs([]);
    setAnalyticsDelta(null);
    setNukeOpen(true);
  };

  const closeNukeTest = () => {
    setNukeOpen(false);
    setNukeRunning(false);
  };

  const clampNukeInput = (value, max) => {
    const numeric = Number(value || 0);
    if (Number.isNaN(numeric)) {
      return 0;
    }
    return Math.min(Math.max(numeric, 1), max);
  };

  const runNukeTest = async () => {
    if (!nukeEndpoint) {
      return;
    }

    const hasActiveConfig = (nukeEndpoint.rateLimitConfigs || []).some(
      (config) => config.isActive
    );
    if (!hasActiveConfig) {
      alert('Cannot run nuke test: no active rate limit configuration for this endpoint');
      return;
    }

    const totalRequests = clampNukeInput(nukeTotal, 2000);
    const concurrency = clampNukeInput(nukeConcurrency, 200);

    const confirmed = confirm(
      `Run nuke test with ${totalRequests} requests at concurrency ${concurrency}?`
    );
    if (!confirmed) {
      return;
    }

    setNukeRunning(true);
    setNukeStatus(null);
    setNukeLogs([]);
    setAnalyticsDelta(null);

    let beforeAnalytics = null;
    try {
      const analyticsRes = await api.getAnalytics();
      beforeAnalytics = analyticsRes.data?.summary || null;
    } catch (err) {
      beforeAnalytics = null;
    }

    try {
      const response = await api.startNukeTest(nukeEndpoint.id, {
        totalRequests,
        concurrency,
      });
      const testId = response.data.testId;

      const poll = setInterval(async () => {
        try {
          const statusRes = await api.getNukeTestStatus(testId);
          const data = statusRes.data;
          setNukeStatus(data);
          setNukeLogs(data.logs || []);

          if (data.status === 'COMPLETED') {
            clearInterval(poll);
            setNukeRunning(false);
            await loadEndpoints();

            if (beforeAnalytics) {
              try {
                const afterAnalyticsRes = await api.getAnalytics();
                const after = afterAnalyticsRes.data?.summary || null;
                if (after) {
                  setAnalyticsDelta({
                    totalRequests: after.totalRequests - beforeAnalytics.totalRequests,
                    blockedRequests: after.blockedRequests - beforeAnalytics.blockedRequests,
                  });
                }
              } catch (err) {
                setAnalyticsDelta(null);
              }
            }
          }
        } catch (err) {
          clearInterval(poll);
          setNukeRunning(false);
          alert('Failed to fetch nuke test status');
        }
      }, 1000);
    } catch (err) {
      setNukeRunning(false);
      const backendMessage =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        'Failed to start nuke test';
      alert(backendMessage);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-400">Loading endpoints...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4">
        <div className="flex items-center">
          <AlertCircle className="h-5 w-5 text-red-400 mr-2" />
          <span className="text-red-300">{error}</span>
        </div>
      </div>
    );
  }

  return (
    <div className="px-4 py-6 sm:px-0">
      {/* Header */}
      <div className="sm:flex sm:items-center sm:justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-white">Endpoints</h1>
          <p className="mt-2 text-gray-400">
            Manage your API endpoints and rate limit configurations
          </p>
        </div>
        <div className="mt-4 sm:mt-0">
          <Link
            to="/endpoints/new"
            className="neo-button"
          >
            <Plus className="h-5 w-5 mr-2" />
            Add Endpoint
          </Link>
        </div>
      </div>

      {/* Endpoints List */}
      {endpoints.length === 0 ? (
        <div className="text-center neo-card p-12">
          <h3 className="mt-2 text-sm font-medium text-white">No endpoints</h3>
          <p className="mt-1 text-sm text-gray-400">
            Get started by adding your first endpoint.
          </p>
          <div className="mt-6">
            <Link
              to="/endpoints/new"
              className="neo-button"
            >
              <Plus className="h-5 w-5 mr-2" />
              Add Endpoint
            </Link>
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="neo-panel p-4 text-sm text-emerald-200">
            Create the endpoint entry first, then configure rate limits from the gauge icon in the list.
          </div>
          <div className="neo-card overflow-hidden sm:rounded-md">
          <ul className="divide-y divide-emerald-500/10">
            {endpoints.map((endpoint) => (
              <li key={endpoint.id}>
                <div className="px-6 py-4 hover:bg-emerald-500/5 transition">
                  <div className="flex items-center justify-between">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center space-x-3">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                            endpoint.httpMethod === 'GET'
                              ? 'bg-emerald-500/10 text-emerald-300'
                              : endpoint.httpMethod === 'POST'
                              ? 'bg-green-500/10 text-green-300'
                              : endpoint.httpMethod === 'PUT'
                              ? 'bg-yellow-500/10 text-yellow-300'
                              : endpoint.httpMethod === 'DELETE'
                              ? 'bg-red-500/10 text-red-300'
                              : 'bg-slate-700 text-gray-300'
                          }`}
                        >
                          {endpoint.httpMethod}
                        </span>
                        <p className="text-sm font-medium text-white truncate">
                          {endpoint.fullUrl}
                        </p>
                        {endpoint.backendLinkName && endpoint.backendLinkColor && (
                          <span
                            className="inline-flex items-center gap-2 px-2 py-0.5 rounded-full text-xs font-medium"
                            style={{ backgroundColor: `${endpoint.backendLinkColor}22`, color: endpoint.backendLinkColor }}
                          >
                            <span className="h-2 w-2 rounded-full" style={{ backgroundColor: endpoint.backendLinkColor }} />
                            {endpoint.backendLinkName}
                          </span>
                        )}
                      </div>
                      {endpoint.description && (
                        <p className="mt-1 text-sm text-gray-400">
                          {endpoint.description}
                        </p>
                      )}
                      <div className="mt-2 flex items-center text-sm text-gray-400">
                        <span>
                          {endpoint.rateLimitConfigs?.length || 0} rate limit
                          config(s)
                        </span>
                        {endpoint.stats && (
                          <span className="ml-4">
                            {endpoint.stats.totalRequests} requests (
                            {endpoint.stats.blockedRequests} blocked)
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="ml-4 flex-shrink-0 flex items-center space-x-2">
                      <button
                        onClick={() => handleToggleStatus(endpoint.id)}
                        className={`p-2 rounded-md ${
                          endpoint.isActive
                            ? 'text-green-400 hover:bg-green-500/10'
                            : 'text-gray-400 hover:bg-slate-700'
                        }`}
                        title={endpoint.isActive ? 'Disable' : 'Enable'}
                      >
                        <Power className="h-5 w-5" />
                      </button>
                      <Link
                        to={`/endpoints/${endpoint.id}/rate-limits`}
                        className="p-2 text-emerald-300 hover:bg-emerald-500/10 rounded-md"
                        title="Rate limits"
                      >
                        <Gauge className="h-5 w-5" />
                      </Link>
                      <button
                        onClick={() => openNukeTest(endpoint)}
                        className="flex items-center gap-1 px-2 py-1 rounded-md bg-red-500/10 text-red-300 hover:bg-red-500/20 border border-red-500/30"
                        title="Nuke test"
                      >
                        <Radiation className="h-4 w-4" />
                        <span className="text-xs font-semibold">Nuke</span>
                      </button>
                      <Link
                        to={`/endpoints/${endpoint.id}/edit`}
                        className="p-2 text-emerald-300 hover:bg-emerald-500/10 rounded-md"
                        title="Edit"
                      >
                        <Edit className="h-5 w-5" />
                      </Link>
                      <button
                        onClick={() => handleDelete(endpoint.id)}
                        className="p-2 text-red-400 hover:bg-red-500/10 rounded-md"
                        title="Delete"
                      >
                        <Trash2 className="h-5 w-5" />
                      </button>
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
          </div>
        </div>
      )}

      {nukeOpen && nukeEndpoint && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 px-4">
          <div className="neo-card w-full max-w-2xl p-6 space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-xl font-semibold text-white">Nuke test</h2>
                <p className="text-sm text-gray-400">{nukeEndpoint.fullUrl}</p>
              </div>
              <button
                onClick={closeNukeTest}
                className="text-gray-400 hover:text-white"
              >
                Close
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Total requests (max 2000)
                </label>
                <input
                  type="number"
                  min="1"
                  max="2000"
                  value={nukeTotal}
                  onChange={(event) => setNukeTotal(event.target.value)}
                  className="neo-input"
                  disabled={nukeRunning}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Concurrency (max 200)
                </label>
                <input
                  type="number"
                  min="1"
                  max="200"
                  value={nukeConcurrency}
                  onChange={(event) => setNukeConcurrency(event.target.value)}
                  className="neo-input"
                  disabled={nukeRunning}
                />
              </div>
            </div>

            {nukeEndpoint &&
              !(nukeEndpoint.rateLimitConfigs || []).some((config) => config.isActive) && (
                <div className="neo-panel p-3 text-sm text-red-300 border border-red-500/30 bg-red-500/10">
                  No active rate limit config found for this endpoint. Create one before running a nuke test.
                </div>
              )}

            <div className="flex items-center justify-between">
              <button
                onClick={runNukeTest}
                disabled={
                  nukeRunning ||
                  !(nukeEndpoint.rateLimitConfigs || []).some((config) => config.isActive)
                }
                className="inline-flex items-center gap-2 bg-red-500/80 text-white px-4 py-2 rounded-lg hover:bg-red-500 disabled:opacity-50"
              >
                <Radiation className="h-4 w-4" />
                {nukeRunning ? 'Running...' : 'Run nuke test'}
              </button>
              {nukeStatus && (
                <div className="text-sm text-gray-300">
                  {nukeStatus.completedRequests}/{nukeStatus.totalRequests} complete
                </div>
              )}
            </div>

            {nukeStatus && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                <div className="neo-panel p-3">
                  <div className="text-gray-400">Accepted</div>
                  <div className="text-white text-lg">{nukeStatus.acceptedRequests}</div>
                </div>
                <div className="neo-panel p-3">
                  <div className="text-gray-400">Rejected</div>
                  <div className="text-white text-lg">{nukeStatus.rejectedRequests}</div>
                </div>
                <div className="neo-panel p-3">
                  <div className="text-gray-400">Errors</div>
                  <div className="text-white text-lg">{nukeStatus.errorRequests}</div>
                </div>
                <div className="neo-panel p-3">
                  <div className="text-gray-400">Status</div>
                  <div className="text-white text-lg">{nukeStatus.status}</div>
                </div>
              </div>
            )}

            {analyticsDelta && (
              <div className="neo-panel p-3 text-sm text-gray-300">
                Analytics delta: {analyticsDelta.totalRequests} total · {analyticsDelta.blockedRequests} blocked
              </div>
            )}

            <div className="neo-panel p-3 h-40 overflow-y-auto text-xs text-gray-300">
              {nukeLogs.length === 0 ? (
                <div className="text-gray-500">Logs will appear here during the test.</div>
              ) : (
                nukeLogs.map((log, index) => (
                  <div key={`${log}-${index}`}>{log}</div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
