import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';

/**
 * Renders a component that depends on react-router without booting a real app.
 *
 * Pages use `useParams`, `useNavigate` and `Link`, so tests that mount them need
 * a router in scope. Pass `path` when the component reads route params.
 */
export function renderWithRouter(
  ui: ReactElement,
  { route = '/', path }: { route?: string; path?: string } = {},
) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      {path ? (
        <Routes>
          <Route path={path} element={ui} />
        </Routes>
      ) : (
        ui
      )}
    </MemoryRouter>,
  );
}

/**
 * As above, but also supplies `AuthProvider` for pages that read auth state.
 *
 * `AuthProvider` only calls `/auth/me` when a token is already stored, so tests
 * that start with a clean `localStorage` make no extra requests.
 */
export function renderAuthenticated(
  ui: ReactElement,
  { route = '/', path }: { route?: string; path?: string } = {},
) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        {path ? (
          <Routes>
            <Route path={path} element={ui} />
          </Routes>
        ) : (
          ui
        )}
      </AuthProvider>
    </MemoryRouter>,
  );
}

/** A successful Response stand-in carrying the backend's ApiResponse envelope. */
export function ok<T>(data: T) {
  return {
    ok: true,
    status: 200,
    json: async () => ({ success: true, data, error: null }),
  };
}

/**
 * A failed Response stand-in, so `parseEnvelope` exercises its real error path
 * and the page sees the same ApiError a student would.
 */
export function fail(code: string, message: string, status = 400) {
  return {
    ok: false,
    status,
    json: async () => ({ success: false, data: null, error: { code, message } }),
  };
}