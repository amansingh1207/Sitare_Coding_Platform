import { useEffect } from 'react';
import { authApi } from '../api/auth';
import { useAuth } from '../context/AuthContext';

const HEARTBEAT_INTERVAL_MS = 60_000;

/**
 * Presence ping backing the admin traffic view. Fires immediately on login
 * and then once a minute while the tab is visible; failures are swallowed
 * so a hiccup here can never break the app for students.
 */
export function useHeartbeat(): void {
  const { user } = useAuth();

  useEffect(() => {
    if (!user) {
      return;
    }
    let stopped = false;
    const beat = () => {
      if (!stopped && document.visibilityState === 'visible') {
        void authApi.heartbeat().catch(() => undefined);
      }
    };
    beat();
    const timer = window.setInterval(beat, HEARTBEAT_INTERVAL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [user]);
}
