import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/auth';
import { ApiError } from '../api/client';

export function VerifyEmailPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState(params.get('email') ?? '');
  const [otp, setOtp] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setInfo(null);
    setSubmitting(true);
    try {
      await authApi.verifyOtp(email, otp);
      setInfo('Email verified! Redirecting to login...');
      setTimeout(() => navigate('/login'), 1200);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Verification failed');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setError(null);
    setInfo(null);
    try {
      await authApi.sendVerificationOtp(email);
      setInfo('A new OTP has been sent to your email.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not resend OTP');
    }
  }

  return (
    <div className="auth-page">
      <h2>Verify your email</h2>
      <form onSubmit={handleSubmit}>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label>
          6-digit OTP
          <input
            type="text"
            value={otp}
            onChange={(e) => setOtp(e.target.value)}
            required
            pattern="\d{6}"
            maxLength={6}
          />
        </label>
        {error && <p className="error">{error}</p>}
        {info && <p className="info">{info}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Verifying...' : 'Verify email'}
        </button>
      </form>
      <button type="button" className="link-button" onClick={handleResend}>
        Resend OTP
      </button>
      <p>
        <Link to="/login">Back to login</Link>
      </p>
    </div>
  );
}
