import axios from 'axios';

export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081/api/v1';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

const getTenantId = () => localStorage.getItem('tenantId');

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let isRefreshing = false;
let pendingRequests = [];

const processQueue = (error, token = null) => {
  pendingRequests.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(token);
    }
  });
  pendingRequests = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingRequests.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('refreshToken');
      if (!refreshToken) {
        isRefreshing = false;
        return Promise.reject(error);
      }

      try {
        const refreshResponse = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken,
        });

        const { token: newAccessToken, refreshToken: newRefreshToken, user } = refreshResponse.data;
        localStorage.setItem('token', newAccessToken);
        localStorage.setItem('refreshToken', newRefreshToken);
        if (user?.tenantId) {
          localStorage.setItem('tenantId', user.tenantId);
        }

        apiClient.defaults.headers.Authorization = `Bearer ${newAccessToken}`;
        processQueue(null, newAccessToken);

        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('tenantId');
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export const api = {
  // Endpoints
  getEndpoints: (includeStats = false) =>
    apiClient.get(`/manage/tenants/${getTenantId()}/endpoints`, {
      params: { includeStats },
    }),

  getEndpoint: (endpointId, includeStats = true) =>
    apiClient.get(`/manage/endpoints/${endpointId}`, {
      params: { includeStats },
    }),

  createEndpoint: (data) =>
    apiClient.post(`/manage/tenants/${getTenantId()}/endpoints`, data),

  updateEndpoint: (endpointId, data) =>
    apiClient.put(`/manage/endpoints/${endpointId}`, data),

  deleteEndpoint: (endpointId) =>
    apiClient.delete(`/manage/endpoints/${endpointId}`),

  toggleEndpoint: (endpointId) =>
    apiClient.post(`/manage/endpoints/${endpointId}/toggle`),

  // Backend Links
  getBackendLinks: () => apiClient.get('/auth/backend-links'),

  // API Keys
  getApiKeys: () =>
    apiClient.get(`/manage/tenants/${getTenantId()}/apikeys`),

  createApiKey: (data) =>
    apiClient.post(`/manage/tenants/${getTenantId()}/apikeys`, data),

  deleteApiKey: (apiKeyId) =>
    apiClient.delete(`/manage/tenants/${getTenantId()}/apikeys/${apiKeyId}`),

  // Tenant
  getTenantSummary: () => apiClient.get(`/manage/tenants/${getTenantId()}`),

  // Rate Limit Configs
  getEndpointConfigs: (endpointId) =>
    apiClient.get(`/manage/tenants/${getTenantId()}/endpoints/${endpointId}/configs`),

  createEndpointConfig: (endpointId, data) =>
    apiClient.post(`/manage/tenants/${getTenantId()}/endpoints/${endpointId}/configs`, data),

  deleteEndpointConfig: (configId) =>
    apiClient.delete(`/manage/tenants/${getTenantId()}/configs/${configId}`),

  // Nuke Test
  startNukeTest: (endpointId, data) =>
    apiClient.post(`/manage/tenants/${getTenantId()}/endpoints/${endpointId}/nuke-tests`, data),

  getNukeTestStatus: (testId) =>
    apiClient.get(`/manage/nuke-tests/${testId}`),

  // Analytics
  getAnalytics: (start, end) =>
    apiClient.get(`/manage/tenants/${getTenantId()}/analytics`, {
      params: { start, end },
    }),

  // Rate Limit Check (for testing)
  checkRateLimit: (data, apiKey) =>
    apiClient.post('/ratelimit/check', data, {
      headers: { 'X-API-Key': apiKey },
    }),
};

export default apiClient;
