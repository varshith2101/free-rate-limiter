import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Mail, Lock, User, ArrowRight } from 'lucide-react';
import { API_BASE_URL } from '../api/client';

export default function SignUp({ onLogin }) {
  const navigate = useNavigate();
  const authMode = import.meta.env.VITE_AUTH_MODE || 'standard';
  const isSoloMode = authMode === 'solo';
  const [step, setStep] = useState(1); // 1: Email, 2: OTP, 3: Password
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSendOtp = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/send-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, name }),
      });

      if (!response.ok) throw new Error('Failed to send OTP');
      setStep(2);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = (e) => {
    e.preventDefault();
    setStep(3);
  };

  const handleSignUp = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${API_BASE_URL}/auth/verify-otp`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, otp, password }),
      });

      if (!response.ok) throw new Error('Sign up failed');
      const data = await response.json();
      onLogin(data.token, data.refreshToken, data.user);
      navigate('/');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  if (isSoloMode) {
    return (
      <div className="min-h-screen flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">
          <div className="neo-card p-8 text-center">
            <div className="w-12 h-12 bg-gradient-to-br from-[rgba(201,136,97,1)] to-[rgba(225,169,131,1)] rounded-xl flex items-center justify-center mx-auto mb-4 shadow-[0_0_16px_rgba(201,136,97,0.35)]">
              <span className="text-white font-bold">RL</span>
            </div>
            <h1 className="text-2xl font-bold text-white mb-2">Not Found</h1>
            <p className="text-gray-400">Sign up is disabled in solo mode.</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <div className="neo-card p-8">
          <div className="text-center mb-8">
            <div className="w-12 h-12 bg-gradient-to-br from-[rgba(201,136,97,1)] to-[rgba(225,169,131,1)] rounded-xl flex items-center justify-center mx-auto mb-4 shadow-[0_0_16px_rgba(201,136,97,0.35)]">
              <span className="text-white font-bold">RL</span>
            </div>
            <h1 className="text-3xl font-bold text-white mb-2">Create Account</h1>
            <p className="text-gray-400">Join Rate Limiter today</p>
          </div>

          {error && (
            <div className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-lg">
              <p className="text-red-400 text-sm">{error}</p>
            </div>
          )}

          {step === 1 && (
            <form onSubmit={handleSendOtp}>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-300 mb-2">Full Name</label>
                  <div className="relative">
                    <User className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                    <input
                      type="text"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="John Doe"
                      className="neo-input pl-10 pr-4"
                      required
                    />
                  </div>
                </div>

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

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full mt-6 neo-button justify-center disabled:opacity-50"
                >
                  {loading ? 'Sending OTP...' : 'Send OTP'} <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </form>
          )}

          {step === 2 && (
            <form onSubmit={handleVerifyOtp}>
              <div className="space-y-4">
                <div className="text-center mb-6">
                  <p className="text-gray-400">Enter the OTP sent to</p>
                  <p className="text-white font-medium">{email}</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-300 mb-2">Enter OTP</label>
                  <input
                    type="text"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.slice(0, 6))}
                    placeholder="000000"
                    maxLength="6"
                    className="neo-input text-center text-2xl tracking-widest"
                    required
                  />
                </div>

                <button
                  type="submit"
                  disabled={otp.length !== 6 || loading}
                  className="w-full mt-6 neo-button justify-center disabled:opacity-50"
                >
                  Verify OTP <ArrowRight className="w-4 h-4" />
                </button>

                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="w-full text-gray-400 hover:text-white transition py-2"
                >
                  Change Email
                </button>
              </div>
            </form>
          )}

          {step === 3 && (
            <form onSubmit={handleSignUp}>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-300 mb-2">Create Password</label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                    <input
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      className="neo-input pl-10 pr-4"
                      required
                      minLength="8"
                    />
                  </div>
                  <p className="text-xs text-gray-400 mt-2">Minimum 8 characters</p>
                </div>

                <button
                  type="submit"
                  disabled={password.length < 8 || loading}
                  className="w-full mt-6 neo-button justify-center disabled:opacity-50"
                >
                  {loading ? 'Creating Account...' : 'Create Account'} <ArrowRight className="w-4 h-4" />
                </button>
              </div>
            </form>
          )}

          <div className="mt-6 text-center">
            <p className="text-gray-400">Already have an account?</p>
            <button
              onClick={() => navigate('/login')}
              className="neo-link font-medium"
            >
              Sign In
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
