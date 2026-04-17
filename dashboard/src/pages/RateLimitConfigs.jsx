import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Lock, Trash2 } from 'lucide-react';
import { api } from '../api/client';

const algorithms = [
  {
    value: 'RECOMMENDED',
    label: 'Use recommended default',
    description: 'Auto-pick algorithm and limits for this method.',
  },
  {
    value: 'TOKEN_BUCKET',
    label: 'Token Bucket',
    description: 'Smooth refill rate, good default.',
  },
  {
    value: 'SLIDING_WINDOW',
    label: 'Sliding Window',
    description: 'Most accurate, Pro only.',
    proOnly: true,
  },
  {
    value: 'FIXED_WINDOW',
    label: 'Fixed Window',
    description: 'Simple fixed window counter.',
  },
  {
    value: 'LEAKY_BUCKET',
    label: 'Leaky Bucket',
    description: 'Steady outflow rate for bursts.',
  },
];

const limitByOptions = [
  { value: 'IP', label: 'IP address' },
  { value: 'USER_ID', label: 'User ID (X-User-ID)' },
  { value: 'API_KEY', label: 'API key' },
  { value: 'CUSTOM_HEADER', label: 'Custom header' },
];

export default function RateLimitConfigs() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [endpoint, setEndpoint] = useState(null);
  const [configs, setConfigs] = useState([]);
  const [tenantTier, setTenantTier] = useState('FREE');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [formData, setFormData] = useState({
    maxRequests: 100,
    windowSeconds: 60,
    algorithm: '',
    refillRate: '',
    limitBy: 'IP',
    customHeader: '',
  });

  const isProTier = useMemo(() => tenantTier === 'PRO' || tenantTier === 'ENTERPRISE', [tenantTier]);

  useEffect(() => {
    loadData();
  }, [id]);

  const loadData = async () => {
    try {
      setLoading(true);
      const [endpointRes, configRes, tenantRes] = await Promise.all([
        api.getEndpoint(id, false),
        api.getEndpointConfigs(id),
        api.getTenantSummary(),
      ]);

      setEndpoint(endpointRes.data);
      setConfigs(configRes.data.filter((config) => config.isActive));
      setTenantTier(tenantRes.data.tier);

      setFormData((prev) => ({
        ...prev,
        maxRequests: prev.maxRequests || 100,
        windowSeconds: prev.windowSeconds || 60,
      }));
    } catch (err) {
      console.error('Failed to load rate limit configs:', err);
      setError('Failed to load rate limit configurations');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (event) => {
    const { name, value } = event.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    setError('');

    if (configs.length > 0) {
      const shouldOverwrite = confirm(
        'Only one configuration is allowed per endpoint. Overwrite the current configuration?'
      );
      if (!shouldOverwrite) {
        navigate(`/endpoints/${id}/rate-limits`);
        return;
      }

      try {
        await api.deleteEndpointConfig(configs[0].id);
      } catch (err) {
        setError('Failed to overwrite existing configuration');
        return;
      }
    }

    try {
      const recommended = getRecommendedDefaults(endpoint?.httpMethod || '*');
      const resolvedAlgorithm = formData.algorithm === 'RECOMMENDED'
        ? recommended.algorithm
        : formData.algorithm;
      const resolvedMaxRequests = formData.algorithm === 'RECOMMENDED'
        ? recommended.maxRequests
        : formData.maxRequests;
      const resolvedWindowSeconds = formData.algorithm === 'RECOMMENDED'
        ? recommended.windowSeconds
        : formData.windowSeconds;
      const resolvedLimitBy = formData.algorithm === 'RECOMMENDED'
        ? recommended.limitBy
        : formData.limitBy;
      const resolvedRefillRate = formData.algorithm === 'RECOMMENDED'
        ? recommended.refillRate
        : formData.refillRate;
      const resolvedCustomHeader = resolvedLimitBy === 'CUSTOM_HEADER'
        ? (formData.customHeader || recommended.customHeader || null)
        : null;

      const payload = {
        ...formData,
        endpointPattern: endpoint?.path,
        httpMethod: endpoint?.httpMethod || '*',
        algorithm: resolvedAlgorithm,
        maxRequests: Number(resolvedMaxRequests),
        windowSeconds: Number(resolvedWindowSeconds),
        limitBy: resolvedLimitBy,
        refillRate: resolvedRefillRate ? Number(resolvedRefillRate) : null,
        customHeader: resolvedCustomHeader,
      };

      await api.createEndpointConfig(id, payload);
      await loadData();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create configuration');
    }
  };

  const handleDelete = async (configId) => {
    if (!confirm('Delete this configuration?')) {
      return;
    }

    try {
      await api.deleteEndpointConfig(configId);
      await loadData();
    } catch (err) {
      setError('Failed to delete configuration');
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-400">Loading rate limits...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4">
        <div className="text-red-300">{error}</div>
      </div>
    );
  }

  return (
    <div className="px-4 py-6 sm:px-0 space-y-6">
      <div>
        <button
          onClick={() => navigate('/endpoints')}
          className="flex items-center text-gray-400 hover:text-white mb-4 transition"
        >
          <ArrowLeft className="h-5 w-5 mr-2" />
          Back to Endpoints
        </button>
        <h1 className="text-3xl font-bold text-white">Rate Limits</h1>
        <p className="mt-2 text-gray-400">
          {endpoint?.fullUrl} ({endpoint?.httpMethod})
        </p>
      </div>

      <form
        onSubmit={handleCreate}
        className="neo-card p-6 space-y-4"
      >
        <div>
          <label className="block text-sm font-medium text-gray-300 mb-2">
            Algorithm
          </label>
          <div className="space-y-2">
            {algorithms.map((algo) => {
              const locked = algo.proOnly && !isProTier;
              return (
                <label
                  key={algo.value}
                  className={`flex items-start gap-3 border rounded-lg p-3 transition ${
                    locked
                      ? 'border-[rgba(203,151,113,0.2)] text-gray-500'
                      : 'border-[rgba(203,151,113,0.34)] text-gray-200 hover:border-[rgba(224,167,124,0.62)]'
                  }`}
                >
                  <input
                    type="radio"
                    name="algorithm"
                    value={algo.value}
                    checked={formData.algorithm === algo.value}
                    onChange={handleChange}
                    disabled={locked}
                    className="mt-1"
                  />
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{algo.label}</span>
                      {locked && (
                        <span className="inline-flex items-center gap-1 text-xs text-yellow-400">
                          <Lock className="h-3 w-3" /> Pro
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-gray-400">{algo.description}</p>
                  </div>
                </label>
              );
            })}
          </div>
        </div>

        {formData.algorithm === 'RECOMMENDED' && (
          <div className="neo-panel p-4 text-sm text-gray-300">
            <div className="mb-2 text-gray-400">Recommended defaults</div>
            {renderRecommendedSummary(endpoint?.httpMethod || '*')}
          </div>
        )}

        {formData.algorithm && formData.algorithm !== 'RECOMMENDED' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Max requests
              </label>
              <input
                type="number"
                name="maxRequests"
                value={formData.maxRequests}
                onChange={handleChange}
                min="1"
                className="neo-input"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Window (seconds)
              </label>
              <input
                type="number"
                name="windowSeconds"
                value={formData.windowSeconds}
                onChange={handleChange}
                min="1"
                className="neo-input"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-300 mb-2">
                Limit by
              </label>
              <select
                name="limitBy"
                value={formData.limitBy}
                onChange={handleChange}
                className="neo-input"
              >
                {limitByOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {formData.limitBy === 'CUSTOM_HEADER' && (
                <input
                  type="text"
                  name="customHeader"
                  value={formData.customHeader}
                  onChange={handleChange}
                  placeholder="X-Client-ID"
                  className="mt-2 neo-input"
                  required
                />
              )}
            </div>
            {formData.algorithm === 'TOKEN_BUCKET' && (
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Refill rate (tokens/sec, optional)
                </label>
                <input
                  type="number"
                  name="refillRate"
                  value={formData.refillRate}
                  onChange={handleChange}
                  min="0"
                  step="0.1"
                  className="neo-input"
                />
              </div>
            )}
          </div>
        )}

        <button
          type="submit"
          disabled={!formData.algorithm}
          className="neo-button disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Create configuration
        </button>
      </form>

      <div className="neo-card p-6 space-y-4">
        <h2 className="text-lg font-semibold text-white">Existing configurations</h2>
        {configs.length === 0 ? (
          <p className="text-gray-400">No rate limit configs yet.</p>
        ) : (
          <div className="space-y-3">
            {configs.map((config) => (
              <div
                key={config.id}
                className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 neo-panel p-4"
              >
                <div>
                  <p className="text-white text-sm font-medium">
                    {config.endpointPattern} · {config.httpMethod}
                  </p>
                  <p className="text-gray-400 text-xs">
                    {config.maxRequests} requests / {config.windowSeconds}s · {config.algorithm}
                  </p>
                  <p className="text-gray-500 text-xs">
                    Limit by: {config.limitBy}
                  </p>
                </div>
                <button
                  onClick={() => handleDelete(config.id)}
                  className="text-red-400 hover:text-red-300 transition"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function getRecommendedDefaults(method) {
  switch (method?.toUpperCase()) {
    case 'GET':
      return {
        algorithm: 'TOKEN_BUCKET',
        maxRequests: 300,
        windowSeconds: 60,
        limitBy: 'IP',
        refillRate: 5,
      };
    case 'DELETE':
      return {
        algorithm: 'FIXED_WINDOW',
        maxRequests: 30,
        windowSeconds: 60,
        limitBy: 'IP',
      };
    case 'POST':
    case 'PUT':
    case 'PATCH':
      return {
        algorithm: 'LEAKY_BUCKET',
        maxRequests: 120,
        windowSeconds: 60,
        limitBy: 'IP',
      };
    default:
      return {
        algorithm: 'TOKEN_BUCKET',
        maxRequests: 150,
        windowSeconds: 60,
        limitBy: 'IP',
      };
  }
}

function renderRecommendedSummary(method) {
  const defaults = getRecommendedDefaults(method);
  const items = [
    { label: 'Algorithm', value: defaults.algorithm },
    { label: 'Max requests', value: defaults.maxRequests },
    { label: 'Window (s)', value: defaults.windowSeconds },
    { label: 'Limit by', value: defaults.limitBy },
  ];

  if (defaults.refillRate) {
    items.push({ label: 'Refill rate', value: defaults.refillRate });
  }

  return (
    <div className="grid gap-2">
      {items.map((item) => (
        <div key={item.label} className="flex justify-between gap-4">
          <span className="text-gray-400">{item.label}</span>
          <span className="text-white">{item.value}</span>
        </div>
      ))}
    </div>
  );
}
