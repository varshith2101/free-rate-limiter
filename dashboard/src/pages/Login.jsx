import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Mail, Lock, ArrowRight } from 'lucide-react';
import { API_BASE_URL } from '../api/client';

export default function Login({ onLogin }) {
  const navigate = useNavigate();
  const authMode = import.meta.env.VITE_AUTH_MODE || 'standard';
  const isSoloMode = authMode === 'solo';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) throw new Error('Invalid credentials');
      const data = await response.json();
      onLogin(data.token, data.refreshToken, data.user);
      navigate('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <div className="neo-card p-8">
          <div className="text-center mb-8">
            <div className="w-12 h-12 bg-gradient-to-br from-emerald-400 to-lime-300 rounded-xl flex items-center justify-center mx-auto mb-4 shadow-[0_0_16px_rgba(68,214,44,0.35)]">
              <span className="text-white font-bold">RL</span>
            </div>
            <h1 className="text-3xl font-bold text-white mb-2">
              {isSoloMode ? 'Admin Login' : 'Welcome Back'}
            </h1>
            <p className="text-gray-400">
              {isSoloMode ? 'Sign in as the admin user' : 'Sign in to your account'}
            </p>
          </div>

          {error && (
            <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg">
              <p className="text-red-400 text-sm">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">Email Address</label>
                <div className="relative">
                  <Mail className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@example.com"
                    className="neo-input pl-10 pr-4"
                    required
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">Password</label>
                <div className="relative">
                  <Lock className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    className="neo-input pl-10 pr-4"
                    required
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full mt-6 neo-button justify-center disabled:opacity-50"
              >
                {loading ? 'Signing in...' : isSoloMode ? 'Login as Admin' : 'Sign In'}
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          </form>

          {!isSoloMode && (
            <div className="mt-6 text-center">
              <p className="text-gray-400">Don't have an account?</p>
              <button
                onClick={() => navigate('/signup')}
                className="neo-link font-medium"
              >
                Create Account
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
