import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Save } from 'lucide-react';
import { api } from '../api/client';

export default function EndpointForm() {
  const navigate = useNavigate();
  const { id } = useParams();
  const isEdit = !!id;

  const [backendLinks, setBackendLinks] = useState([]);
  const [linksLoading, setLinksLoading] = useState(true);
  const [formData, setFormData] = useState({
    backendLinkId: '',
    path: '',
    httpMethod: 'GET',
    description: '',
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (isEdit) {
      loadEndpoint();
    }
    loadBackendLinks();
  }, [id]);

  const loadEndpoint = async () => {
    try {
      const response = await api.getEndpoint(id, false);
      const endpoint = response.data;
      setFormData({
        backendLinkId: endpoint.backendLinkId || '',
        path: endpoint.path,
        httpMethod: endpoint.httpMethod,
        description: endpoint.description || '',
      });
    } catch (err) {
      console.error('Failed to load endpoint:', err);
      setError('Failed to load endpoint');
    }
  };

  const loadBackendLinks = async () => {
    try {
      setLinksLoading(true);
      const response = await api.getBackendLinks();
      const verifiedLinks = response.data.filter((link) => link.isVerified);
      setBackendLinks(verifiedLinks);

      if (!isEdit && verifiedLinks.length > 0) {
        setFormData((prev) => ({
          ...prev,
          backendLinkId: verifiedLinks[0].id,
        }));
      }
    } catch (err) {
      console.error('Failed to load backend links:', err);
      setError('Failed to load verified backend links');
    } finally {
      setLinksLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (isEdit) {
        await api.updateEndpoint(id, formData);
      } else {
        await api.createEndpoint(formData);
      }
      navigate('/endpoints');
    } catch (err) {
      console.error('Failed to save endpoint:', err);
      setError(err.response?.data?.message || 'Failed to save endpoint');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  return (
    <div className="px-4 py-6 sm:px-0">
      {/* Header */}
      <div className="mb-6">
        <button
          onClick={() => navigate('/endpoints')}
          className="flex items-center text-gray-400 hover:text-white mb-4 transition"
        >
          <ArrowLeft className="h-5 w-5 mr-2" />
          Back to Endpoints
        </button>
        <h1 className="text-3xl font-bold text-white">
          {isEdit ? 'Edit Endpoint' : 'Add New Endpoint'}
        </h1>
        <p className="mt-2 text-gray-400">
          {isEdit
            ? 'Update your endpoint configuration'
            : 'Configure a new endpoint to rate limit'}
        </p>
      </div>

      {/* Error Message */}
      {error && (
        <div className="mb-6 bg-red-500/10 border border-red-500/20 rounded-lg p-4">
          <p className="text-red-300">{error}</p>
        </div>
      )}

      {/* Form */}
      <div className="neo-card p-6">
        <form onSubmit={handleSubmit} className="space-y-6">
          {/* Backend Link */}
          <div>
            <label className="block text-sm font-medium text-gray-300">
              Verified Backend *
            </label>
            {linksLoading ? (
              <div className="mt-2 text-sm text-gray-400">Loading verified backends...</div>
            ) : backendLinks.length === 0 ? (
              <div className="mt-2 text-sm text-yellow-400">
                No verified backends found. Verify one under Backend Links first.
              </div>
            ) : (
              <select
                id="backendLinkId"
                name="backendLinkId"
                required
                value={formData.backendLinkId}
                onChange={handleChange}
                className="mt-1 neo-input sm:text-sm px-3 py-2"
              >
                {backendLinks.map((link) => (
                  <option key={link.id} value={link.id}>
                    {link.nickname} — {link.backendUrl}
                  </option>
                ))}
              </select>
            )}
            <p className="mt-1 text-sm text-gray-400">
              Endpoints inherit the base URL from this verified backend.
            </p>
          </div>

          {/* Path */}
          <div>
            <label
              htmlFor="path"
              className="block text-sm font-medium text-gray-300"
            >
              Endpoint Path *
            </label>
            <input
              type="text"
              id="path"
              name="path"
              required
              value={formData.path}
              onChange={handleChange}
              placeholder="/api/users"
              className="mt-1 neo-input sm:text-sm px-3 py-2"
            />
            <p className="mt-1 text-sm text-gray-400">
              Supports wildcards, e.g., /api/users/*
            </p>
          </div>

          {/* HTTP Method */}
          <div>
            <label
              htmlFor="httpMethod"
              className="block text-sm font-medium text-gray-300"
            >
              HTTP Method *
            </label>
            <select
              id="httpMethod"
              name="httpMethod"
              required
              value={formData.httpMethod}
              onChange={handleChange}
              className="mt-1 neo-input sm:text-sm px-3 py-2"
            >
              <option value="*">All Methods (*)</option>
              <option value="GET">GET</option>
              <option value="POST">POST</option>
              <option value="PUT">PUT</option>
              <option value="DELETE">DELETE</option>
              <option value="PATCH">PATCH</option>
            </select>
          </div>

          {/* Description */}
          <div>
            <label
              htmlFor="description"
              className="block text-sm font-medium text-gray-300"
            >
              Description
            </label>
            <textarea
              id="description"
              name="description"
              rows={3}
              value={formData.description}
              onChange={handleChange}
              placeholder="What does this endpoint do?"
              className="mt-1 neo-input sm:text-sm px-3 py-2"
            />
          </div>

          {/* Actions */}
          <div className="flex justify-end space-x-3 pt-4 border-t border-slate-700">
            <button
              type="button"
              onClick={() => navigate('/endpoints')}
              className="neo-button-ghost"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || backendLinks.length === 0}
              className="neo-button disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Save className="h-5 w-5 mr-2" />
              {loading ? 'Saving...' : isEdit ? 'Update' : 'Create'}
            </button>
          </div>
        </form>
      </div>

      {/* Help Text */}
      <div className="mt-6 neo-panel p-4">
        <h3 className="text-sm font-medium text-[rgba(225,169,131,1)] mb-2">Next Steps</h3>
        <p className="text-sm text-[rgba(238,188,154,1)]">
          Create the endpoint entry first, then add rate limit configurations from the
          rate limit button in the endpoints list.
        </p>
      </div>
    </div>
  );
}
