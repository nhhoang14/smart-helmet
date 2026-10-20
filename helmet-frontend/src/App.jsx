/**
 * App – routing, nav, khởi tạo WebSocket realtime kết nối backend Spring Boot
 */
import { useEffect } from 'react';
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom';
import { ConnectionBadge } from './components/ConnectionBadge';
import { DashboardPage } from './pages/DashboardPage';
import { AlertsPage } from './pages/AlertsPage';
import { HistoryPage } from './pages/HistoryPage';
import { SettingsPage } from './pages/SettingsPage';
import { useDeviceState } from './hooks/useDeviceState';
import { initRealtime } from './api/realtime';
import { useAlerts } from './hooks/useAlerts';

function NavBar({ newCount }) {
  return (
    <nav className="nav">
      <div className="nav-inner">
        <a href="/" className="nav-logo">
          <div className="nav-logo-icon">⛑️</div>
          <span>SmartHelmet</span>
        </a>

        <div className="nav-links">
          <NavLink to="/" end className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span>📊</span> <span>Dashboard</span>
          </NavLink>
          <NavLink to="/alerts" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span>🚨</span> <span>Cảnh báo</span>
            {newCount > 0 && (
              <span className="badge badge-danger" style={{ padding: '2px 7px', fontSize: '0.7rem' }}>
                {newCount}
              </span>
            )}
          </NavLink>
          <NavLink to="/history" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span>📈</span> <span>Lịch sử</span>
          </NavLink>
          <NavLink to="/settings" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            <span>⚙️</span> <span>Cài đặt</span>
          </NavLink>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <ConnectionBadge />
        </div>
      </div>
    </nav>
  );
}

function AppContent() {
  const { state: deviceState } = useDeviceState();
  const { newCount } = useAlerts();

  // Khởi tạo WebSocket realtime kết nối Spring Boot
  useEffect(() => {
    initRealtime();
  }, []);

  return (
    <>
      <NavBar newCount={newCount} />
      <Routes>
        <Route path="/"         element={<DashboardPage deviceState={deviceState} />} />
        <Route path="/alerts"   element={<AlertsPage />} />
        <Route path="/history"  element={<HistoryPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Routes>

      {/* Footer */}
      <footer style={{
        borderTop: '1px solid var(--c-border)',
        padding: '16px 24px',
        fontSize: '0.75rem',
        color: 'var(--c-text-dim)',
        display: 'flex',
        justifyContent: 'space-between',
        maxWidth: 1400,
        margin: '0 auto',
        width: '100%',
      }}>
        <span>SmartHelmet Dashboard · ESP32 + MQ-135 + MPU6050 + GPS</span>
        <span>MQTT · Spring Boot 3 · MySQL (helmet_db) · React</span>
      </footer>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppContent />
    </BrowserRouter>
  );
}
