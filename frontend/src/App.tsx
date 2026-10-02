import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ProblemListPage } from './pages/ProblemListPage';
import { ProblemDetailPage } from './pages/ProblemDetailPage';

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return <p>Loading...</p>;
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function Header() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <header className="app-header">
      <Link to="/problems" className="app-header__brand">
        CodingJudge
      </Link>
      <nav>
        {user ? (
          <>
            <span>{user.username}</span>
            <button type="button" onClick={handleLogout}>
              Logout
            </button>
          </>
        ) : (
          <>
            <Link to="/login">Login</Link>
            <Link to="/register">Register</Link>
          </>
        )}
      </nav>
    </header>
  );
}

function App() {
  return (
    <AuthProvider>
      <div className="app">
        <Header />
        <main>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              path="/problems"
              element={
                <ProtectedRoute>
                  <ProblemListPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/problems/:slug"
              element={
                <ProtectedRoute>
                  <ProblemDetailPage />
                </ProtectedRoute>
              }
            />
            <Route path="/" element={<Navigate to="/problems" replace />} />
            <Route path="*" element={<p>Page not found.</p>} />
          </Routes>
        </main>
      </div>
    </AuthProvider>
  );
}

export default App;
