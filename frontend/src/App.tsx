import { Navigate, NavLink, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import Login from './pages/Login';
import NewTracker from './pages/NewTracker';
import Settings from './pages/Settings';
import TodayPage from './pages/Today';
import TrackerPage from './pages/TrackerPage';
import Trackers from './pages/Trackers';

function TabBar() {
  return (
    <div className="tabbar">
      <nav aria-label="Main">
        <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M3 17h18" />
            <path d="M7 17a5 5 0 0 1 10 0" />
            <path d="M12 4v3M4.9 9.9l1.4 1.4M19.1 9.9l-1.4 1.4" />
          </svg>
          Today
        </NavLink>
        <NavLink to="/trackers" className={({ isActive }) => (isActive ? 'active' : '')}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <rect x="4" y="4" width="16" height="16" rx="3" />
            <path d="M8 9h8M8 13h8M8 17h5" />
          </svg>
          Trackers
        </NavLink>
        <NavLink to="/settings" className={({ isActive }) => (isActive ? 'active' : '')}>
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="3" />
            <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4" />
          </svg>
          Settings
        </NavLink>
      </nav>
    </div>
  );
}

export default function App() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <main className="page">
        <p className="muted">Loading your day…</p>
      </main>
    );
  }

  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<Login />} />
      </Routes>
    );
  }

  return (
    <>
      <Routes>
        <Route path="/" element={<TodayPage />} />
        <Route path="/trackers" element={<Trackers />} />
        <Route path="/trackers/new" element={<NewTracker />} />
        <Route path="/trackers/:id" element={<TrackerPage />} />
        <Route path="/settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      <TabBar />
    </>
  );
}
