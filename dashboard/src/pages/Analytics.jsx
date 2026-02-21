import { useEffect, useState } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { Calendar, TrendingUp, AlertCircle } from 'lucide-react';
import { api } from '../api/client';
import { format } from 'date-fns';

export default function Analytics() {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [timeRange, setTimeRange] = useState('24h');

  useEffect(() => {
    loadAnalytics();
  }, [timeRange]);

  const loadAnalytics = async () => {
    try {
      setLoading(true);
      const end = new Date();
      const start = new Date();

      switch (timeRange) {
        case '1h':
          start.setHours(start.getHours() - 1);
          break;
        case '24h':
          start.setHours(start.getHours() - 24);
          break;
        case '7d':
          start.setDate(start.getDate() - 7);
          break;
        case '30d':
          start.setDate(start.getDate() - 30);
          break;
      }

      const response = await api.getAnalytics(
        start.toISOString(),
        end.toISOString()
      );
      setAnalytics(response.data);
    } catch (err) {
      console.error('Failed to load analytics:', err);
      setError('Failed to load analytics data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-400">Loading analytics...</div>
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

  const summary = analytics?.summary || {};
  const timeSeries = analytics?.timeSeries || [];
  const topEndpoints = analytics?.topEndpoints || [];

  const toIstDate = (timestamp) => {
    if (!timestamp) {
      return null;
    }
    const hasZone = /[zZ]|[+-]\d{2}:?\d{2}$/.test(timestamp);
    const normalized = hasZone ? timestamp : `${timestamp}+05:30`;
    return new Date(normalized);
  };

  // Format time series data for charts
  const chartData = timeSeries.map((point) => {
    const istDate = toIstDate(point.timestamp);
    return {
      time: istDate ? format(istDate, 'MMM dd HH:mm') : 'Unknown',
      total: point.totalRequests,
      blocked: point.blockedRequests,
      allowed: point.totalRequests - point.blockedRequests,
    };
  });

  return (
    <div className="px-4 py-6 sm:px-0">
      {/* Header */}
      <div className="sm:flex sm:items-center sm:justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold text-white">Analytics</h1>
          <p className="mt-2 text-gray-400">
            Monitor your rate limiting activity and performance
          </p>
        </div>
        <div className="mt-4 sm:mt-0">
          <select
            value={timeRange}
            onChange={(e) => setTimeRange(e.target.value)}
            className="neo-input sm:text-sm px-3 py-2"
          >
            <option value="1h">Last Hour</option>
            <option value="24h">Last 24 Hours</option>
            <option value="7d">Last 7 Days</option>
            <option value="30d">Last 30 Days</option>
          </select>
        </div>
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4 mb-8">
        <StatCard
          label="Total Requests"
          value={summary.totalRequests?.toLocaleString() || '0'}
          icon={TrendingUp}
          color="emerald"
        />
        <StatCard
          label="Allowed Requests"
          value={summary.allowedRequests?.toLocaleString() || '0'}
          icon={TrendingUp}
          color="green"
        />
        <StatCard
          label="Blocked Requests"
          value={summary.blockedRequests?.toLocaleString() || '0'}
          icon={AlertCircle}
          color="red"
        />
        <StatCard
          label="Block Rate"
          value={`${summary.blockRate || 0}%`}
          icon={Calendar}
          color="yellow"
        />
      </div>

      {/* Request Timeline */}
      <div className="neo-card p-6 mb-8">
        <h2 className="text-lg font-medium text-white mb-4">
          Request Timeline
        </h2>
        {chartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1b2a1f" />
              <XAxis dataKey="time" stroke="#9aa3a0" />
              <YAxis stroke="#9aa3a0" />
              <Tooltip contentStyle={{ backgroundColor: '#0a0f0c', borderColor: '#1a2a1f', color: '#e2fbe6' }} />
              <Legend />
              <Line
                type="monotone"
                dataKey="total"
                stroke="#22c55e"
                name="Total Requests"
              />
              <Line
                type="monotone"
                dataKey="blocked"
                stroke="#ef4444"
                name="Blocked"
              />
              <Line
                type="monotone"
                dataKey="allowed"
                stroke="#4ade80"
                name="Allowed"
              />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <p className="text-center text-gray-400 py-8">No data available</p>
        )}
      </div>

      {/* Top Endpoints */}
      <div className="neo-card p-6">
        <h2 className="text-lg font-medium text-white mb-4">Top Endpoints</h2>
        {topEndpoints.length > 0 ? (
          <div className="space-y-3">
            {topEndpoints.map((endpoint, index) => (
              <div key={index} className="flex items-center justify-between">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-white truncate">
                    {endpoint.endpoint}
                  </p>
                  <p className="text-xs text-gray-400">{endpoint.method}</p>
                </div>
                <div className="ml-4 text-right">
                  <p className="text-sm font-medium text-white">
                    {endpoint.requestCount.toLocaleString()}
                  </p>
                  <p className="text-xs text-gray-400">requests</p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-center text-gray-400 py-8">No data available</p>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value, icon: Icon, color }) {
  const colorClasses = {
    emerald: 'bg-emerald-500/10 text-emerald-400',
    green: 'bg-green-500/10 text-green-400',
    red: 'bg-red-500/10 text-red-400',
    yellow: 'bg-yellow-500/10 text-yellow-400',
  };

  return (
    <div className="neo-card">
      <div className="p-5">
        <div className="flex items-center">
          <div className={`flex-shrink-0 rounded-md p-3 ${colorClasses[color]}`}>
            <Icon className="h-6 w-6" />
          </div>
          <div className="ml-5 w-0 flex-1">
            <dl>
              <dt className="text-sm font-medium text-gray-400 truncate">
                {label}
              </dt>
              <dd className="text-2xl font-semibold text-white">{value}</dd>
            </dl>
          </div>
        </div>
      </div>
    </div>
  );
}
