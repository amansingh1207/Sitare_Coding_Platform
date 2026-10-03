import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../api/auth';
import { ApiError } from '../api/client';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [step, setStep] = useState<'request' | 'reset'>('request');
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSendOtp(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await authApi.sendPasswordResetOtp(email);
      setInfo('If the account exists, a reset OTP has been sent.');
      setStep('reset');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send OTP');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReset(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setInfo(null);
    setSubmitting(true);
    try {
      await authApi.resetPassword(email, otp, newPassword);
      setInfo('Password reset successfully. You can now log in.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reset failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <h2>Reset password</h2>
      {step === 'request' ? (
        <form onSubmit={handleSendOtp}>
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? 'Sending...' : 'Send reset OTP'}
          </button>
        </form>
      ) : (
        <form onSubmit={handleReset}>
          <label>
            Email
            <input type="email" value={email} disabled />
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
          <label>
            New password
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              minLength={8}
            />
          </label>
          {error && <p className="error">{error}</p>}
          {info && <p className="info">{info}</p>}
          <button type="submit" disabled={submitting}>
            {submitting ? 'Resetting...' : 'Reset password'}
          </button>
        </form>
      )}
      {info && step === 'request' && <p className="info">{info}</p>}
      <p>
        <Link to="/login">Back to login</Link>
      </p>
    </div>
  );
}
