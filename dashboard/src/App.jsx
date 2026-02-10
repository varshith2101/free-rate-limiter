import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import Dashboard from './pages/Dashboard';
import Endpoints from './pages/Endpoints';
import Analytics from './pages/Analytics';
import EndpointForm from './pages/EndpointForm';
import SignUp from './pages/SignUp';
import Login from './pages/Login';
import BackendLinks from './pages/BackendLinks';
import ApiKeys from './pages/ApiKeys';
import SetupTutorial from './pages/SetupTutorial';
import RateLimitConfigs from './pages/RateLimitConfigs';
import './App.css';
import { API_BASE_URL } from './api/client';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      fetchUser(token);
    } else {
      setLoading(false);
    }
  }, []);

  const fetchUser = async (token) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (response.ok) {
        const userData = await response.json();
        setUser(userData);
        setIsAuthenticated(true);
        if (userData.tenantId) {
          localStorage.setItem('tenantId', userData.tenantId);
        }
      } else {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('tenantId');
      }
    } catch (error) {
      console.error('Failed to fetch user:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = (token, refreshToken, userData) => {
    localStorage.setItem('token', token);
    localStorage.setItem('refreshToken', refreshToken);
    if (userData.tenantId) {
      localStorage.setItem('tenantId', userData.tenantId);
    }
    setUser(userData);
    setIsAuthenticated(true);
  };

  const handleLogout = async () => {
    const refreshToken = localStorage.getItem('refreshToken');
    try {
      if (refreshToken) {
        await fetch(`${API_BASE_URL}/auth/logout`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
      }
    } catch (error) {
      console.error('Logout failed:', error);
    } finally {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('tenantId');
      setUser(null);
      setIsAuthenticated(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="w-12 h-12 border-4 border-emerald-500/30 border-t-emerald-300 rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-gray-300">Loading...</p>
        </div>
      </div>
    );
  }

  return (
    <Router>
      <div className="min-h-screen relative overflow-hidden">
        <div className="pointer-events-none fixed inset-0 z-0">
          <div className="animate-scanline absolute left-0 right-0 top-0 h-32 bg-gradient-to-b from-emerald-400/0 via-emerald-400/20 to-emerald-400/0" />
        </div>
        {isAuthenticated ? (
          <>
            <Navigation user={user} onLogout={handleLogout} />
            <main className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
              <Routes>
                <Route path="/" element={<Dashboard user={user} />} />
                <Route path="/endpoints" element={<Endpoints />} />
                <Route path="/endpoints/new" element={<EndpointForm />} />
                <Route path="/endpoints/:id/edit" element={<EndpointForm />} />
                <Route path="/endpoints/:id/rate-limits" element={<RateLimitConfigs />} />
                <Route path="/analytics" element={<Analytics />} />
                <Route path="/backend-links" element={<BackendLinks />} />
                <Route path="/api-keys" element={<ApiKeys />} />
                <Route path="/setup" element={<SetupTutorial />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </main>
          </>
        ) : (
          <Routes>
            <Route path="/signup" element={<SignUp onLogin={handleLogin} />} />
            <Route path="/login" element={<Login onLogin={handleLogin} />} />
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        )}
      </div>
    </Router>
  );
}

function Navigation({ user, onLogout }) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <nav className="bg-[#050806]/80 backdrop-blur border-b border-emerald-500/20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-gradient-to-br from-emerald-400 to-lime-300 rounded-lg flex items-center justify-center shadow-[0_0_12px_rgba(68,214,44,0.35)]">
              <span className="text-white font-bold text-sm">RL</span>
            </div>
            <h1 className="text-xl font-bold text-white">Rate Limiter</h1>
          </div>

          <div className="hidden md:flex items-center gap-8">
            <a href="/" className="text-gray-300 hover:text-emerald-100 transition">Dashboard</a>
            <a href="/endpoints" className="text-gray-300 hover:text-emerald-100 transition">Endpoints</a>
            <a href="/analytics" className="text-gray-300 hover:text-emerald-100 transition">Analytics</a>
            <a href="/backend-links" className="text-gray-300 hover:text-emerald-100 transition">Backend Links</a>
            <a href="/setup" className="text-gray-300 hover:text-emerald-100 transition">Setup Tutorial</a>
          </div>

          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-300 hidden sm:inline">{user?.name}</span>
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="w-10 h-10 rounded-full bg-gradient-to-br from-emerald-500 to-lime-400 flex items-center justify-center text-slate-900 font-semibold hover:shadow-lg hover:shadow-emerald-500/30 transition"
            >
              {user?.name?.charAt(0).toUpperCase()}
            </button>
            {menuOpen && (
              <div className="absolute right-4 top-16 bg-[#0b120e] border border-emerald-500/20 rounded-lg shadow-xl z-50">
                <a
                  href="/api-keys"
                  className="block w-full text-left px-4 py-2 text-gray-300 hover:text-emerald-100 hover:bg-emerald-500/10 rounded transition"
                  onClick={() => setMenuOpen(false)}
                >
                  API
                </a>
                <button
                  onClick={() => {
                    onLogout();
                    setMenuOpen(false);
                  }}
                  className="block w-full text-left px-4 py-2 text-gray-300 hover:text-emerald-100 hover:bg-emerald-500/10 rounded transition"
                >
                  Logout
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}

export default App;
