import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Activity, TrendingUp, Shield, AlertCircle, Plus } from 'lucide-react';
import { api } from '../api/client';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadDashboardData();
  }, []);

  const loadDashboardData = async () => {
    try {
      setLoading(true);
      const [endpointsRes, analyticsRes] = await Promise.all([
        api.getEndpoints(true),
        api.getAnalytics(),
      ]);

      const endpoints = endpointsRes.data;
      const analytics = analyticsRes.data;

      setStats({
        totalEndpoints: endpoints.length,
        activeEndpoints: endpoints.filter((e) => e.isActive).length,
        totalRequests: analytics.summary?.totalRequests || 0,
        blockedRequests: analytics.summary?.blockedRequests || 0,
        blockRate: analytics.summary?.blockRate || 0,
        recentEndpoints: endpoints.slice(0, 5),
      });
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
      setError('Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="text-gray-400">Loading...</div>
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
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-white">Dashboard</h1>
        <p className="mt-2 text-gray-400">
          Overview of your rate limiting configuration and usage
        </p>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4 mb-8">
        <StatsCard
          icon={Activity}
          label="Total Endpoints"
          value={stats.totalEndpoints}
          iconColor="text-emerald-400"
          bgColor="bg-emerald-500/10"
        />
        <StatsCard
          icon={Shield}
          label="Active Endpoints"
          value={stats.activeEndpoints}
          iconColor="text-green-400"
          bgColor="bg-green-500/10"
        />
        <StatsCard
          icon={TrendingUp}
          label="Total Requests (24h)"
          value={stats.totalRequests.toLocaleString()}
          iconColor="text-purple-400"
          bgColor="bg-purple-500/10"
        />
        <StatsCard
          icon={AlertCircle}
          label="Blocked Requests"
          value={`${stats.blockedRequests} (${stats.blockRate}%)`}
          iconColor="text-red-400"
          bgColor="bg-red-500/10"
        />
      </div>

      {/* Quick Actions */}
      <div className="neo-card p-6 mb-8">
        <h2 className="text-lg font-medium text-white mb-4">Quick Actions</h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Link
            to="/endpoints/new"
            className="neo-button justify-center"
          >
            <Plus className="h-5 w-5 mr-2" />
            Add New Endpoint
          </Link>
          <Link
            to="/endpoints"
            className="neo-button-ghost justify-center"
          >
            View All Endpoints
          </Link>
          <Link
            to="/analytics"
            className="neo-button-ghost justify-center"
          >
            View Analytics
          </Link>
        </div>
      </div>

      {/* Recent Endpoints */}
      <div className="neo-card overflow-hidden">
        <div className="px-6 py-4 border-b border-emerald-500/15">
          <h2 className="text-lg font-medium text-white">Recent Endpoints</h2>
        </div>
        <ul className="divide-y divide-emerald-500/10">
          {stats.recentEndpoints.map((endpoint) => (
            <li key={endpoint.id} className="px-6 py-4 hover:bg-[rgba(201,136,97,0.12)] transition">
              <div className="flex items-center justify-between">
                <div className="flex-1">
                  <p className="text-sm font-medium text-white">
                    {endpoint.path}
                  </p>
                  <p className="text-sm text-gray-400">{endpoint.basePath}</p>
                </div>
                <div className="ml-4 flex items-center">
                  <span
                    className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${
                      endpoint.isActive
                        ? 'bg-[rgba(201,136,97,0.14)] text-[rgba(225,169,131,1)]'
                        : 'bg-[rgba(201,136,97,0.08)] text-gray-300'
                    }`}
                  >
                    {endpoint.isActive ? 'Active' : 'Inactive'}
                  </span>
                  <Link
                    to={`/endpoints/${endpoint.id}/rate-limits`}
                    className="ml-3 text-sm text-[rgba(225,169,131,1)] hover:text-[rgba(242,201,172,1)]"
                  >
                    Rate limits
                  </Link>
                  <Link
                    to={`/endpoints/${endpoint.id}/edit`}
                    className="ml-3 text-sm text-[rgba(225,169,131,1)] hover:text-[rgba(242,201,172,1)]"
                  >
                    Edit
                  </Link>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function StatsCard({ icon: Icon, label, value, iconColor, bgColor }) {
  return (
    <div className="neo-card">
      <div className="p-5">
        <div className="flex items-center">
          <div className={`flex-shrink-0 ${bgColor} rounded-md p-3`}>
            <Icon className={`h-6 w-6 ${iconColor}`} />
          </div>
          <div className="ml-5 w-0 flex-1">
            <dl>
              <dt className="text-sm font-medium text-gray-400 truncate">{label}</dt>
              <dd className="text-2xl font-semibold text-white">{value}</dd>
            </dl>
          </div>
        </div>
      </div>
    </div>
  );
}
