/**
 * SettingsPage – cấu hình ngưỡng cảnh báo và thông số thiết bị lưu trong MySQL
 */
import { useState, useEffect } from 'react';
import { getSettings, updateSettings } from '../api/client';

const DEVICE_ID = import.meta.env.VITE_DEVICE_ID || '01';

const DEFAULT_SETTINGS = {
  gasWarnPpm:        400,
  gasDangerFactor:   1.5,
  offlineTimeoutSec: 10,
  telemetrySampleSec: 5,
};

export function SettingsPage() {
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [original, setOriginal] = useState(DEFAULT_SETTINGS);
  const [loading, setLoading]   = useState(true);
  const [saving, setSaving]     = useState(false);
  const [saved, setSaved]       = useState(false);

  useEffect(() => {
    setLoading(true);
    getSettings(DEVICE_ID)
      .then(data => {
        setSettings(data);
        setOriginal(data);
        setLoading(false);
      })
      .catch(err => {
        console.error('Lỗi tải cài đặt:', err);
        setLoading(false);
      });
  }, []);

  const handleChange = (key, value) => {
    setSettings(prev => ({ ...prev, [key]: value }));
    setSaved(false);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const updated = await updateSettings(DEVICE_ID, settings);
      setSettings(updated);
      setOriginal(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (e) {
      alert(`Lỗi lưu cài đặt vào cơ sở dữ liệu: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  const handleReset = () => {
    setSettings(original);
    setSaved(false);
  };

  const dangerThreshold = Math.round(settings.gasWarnPpm * settings.gasDangerFactor);
  const isDirty = JSON.stringify(settings) !== JSON.stringify(original);

  if (loading) return (
    <div className="page" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 300 }}>
      <div className="spinner" style={{ width: 40, height: 40 }} />
    </div>
  );

  return (
    <div className="page animate-in">
      <div className="page-header">
        <div>
          <h1 className="page-title">Cài đặt</h1>
          <div className="page-sub">Cấu hình ngưỡng cảnh báo và thông số hệ thống cho Mũ #{DEVICE_ID}</div>
        </div>
        {saved && (
          <div className="badge badge-safe" style={{ fontSize: '0.85rem', padding: '8px 16px' }}>
            ✓ Đã lưu thành công vào MySQL
          </div>
        )}
      </div>

      <div className="grid-2">
        {/* Gas thresholds */}
        <div className="card animate-in">
          <div className="card-title">☣️ Ngưỡng nồng độ khí</div>

          <div className="form-group">
            <label className="form-label">Ngưỡng cảnh báo (ppm)</label>
            <input
              type="number"
              className="form-input"
              id="gas-warn-input"
              value={settings.gasWarnPpm}
              min={100}
              max={1000}
              onChange={e => handleChange('gasWarnPpm', Number(e.target.value))}
            />
            <span className="form-hint">Khi vượt ngưỡng này sẽ tạo cảnh báo GAS_WARNING (100 – 1000 ppm).</span>
          </div>

          <div className="form-group">
            <label className="form-label">Hệ số nguy hiểm</label>
            <input
              type="number"
              className="form-input"
              id="gas-danger-factor-input"
              value={settings.gasDangerFactor}
              min={1.1}
              max={5}
              step={0.1}
              onChange={e => handleChange('gasDangerFactor', Number(e.target.value))}
            />
            <span className="form-hint">
              Ngưỡng nguy hiểm = {settings.gasWarnPpm} × {settings.gasDangerFactor} ={' '}
              <strong style={{ color: 'var(--c-danger)' }}>{dangerThreshold} ppm</strong>
            </span>
          </div>

          {/* Preview */}
          <div style={{
            background: 'var(--c-bg)',
            borderRadius: 'var(--radius-sm)',
            padding: '12px 16px',
            border: '1px solid var(--c-border)',
            marginTop: 8,
          }}>
            <div style={{ fontSize: '0.78rem', color: 'var(--c-text-muted)', marginBottom: 8 }}>Xem trước phân vùng</div>
            <div style={{ display: 'flex', gap: 16 }}>
              <span className="badge badge-safe">AN TOÀN &lt; {settings.gasWarnPpm}</span>
              <span className="badge badge-warn">CẢNH BÁO ≥ {settings.gasWarnPpm}</span>
              <span className="badge badge-danger">NGUY HIỂM ≥ {dangerThreshold}</span>
            </div>
          </div>
        </div>

        {/* Connection & sampling */}
        <div className="card animate-in">
          <div className="card-title">⏱️ Thời gian & lấy mẫu</div>

          <div className="form-group">
            <label className="form-label">Timeout mất kết nối (giây)</label>
            <input
              type="number"
              className="form-input"
              id="offline-timeout-input"
              value={settings.offlineTimeoutSec}
              min={5}
              max={120}
              onChange={e => handleChange('offlineTimeoutSec', Number(e.target.value))}
            />
            <span className="form-hint">Sau thời gian này không nhận bản tin thì coi là mất kết nối (offline).</span>
          </div>

          <div className="form-group">
            <label className="form-label">Chu kỳ lưu telemetry (giây)</label>
            <input
              type="number"
              className="form-input"
              id="telemetry-sample-input"
              value={settings.telemetrySampleSec}
              min={1}
              max={60}
              onChange={e => handleChange('telemetrySampleSec', Number(e.target.value))}
            />
            <span className="form-hint">
              Ước tính: {Math.round(86400 / (settings.telemetrySampleSec || 5)).toLocaleString()} dòng/ngày
            </span>
          </div>
        </div>

        {/* Info card */}
        <div className="card animate-in">
          <div className="card-title">ℹ️ Thông tin thiết bị & cơ sở dữ liệu</div>
          <div className="status-row">
            <span className="status-row-label">🆔 Device ID</span>
            <span className="status-row-value">{DEVICE_ID}</span>
          </div>
          <div className="status-row">
            <span className="status-row-label">🗄️ Database</span>
            <span className="status-row-value">MySQL (helmet_db)</span>
          </div>
          <div className="status-row">
            <span className="status-row-label">📡 MQTT Topic</span>
            <span className="status-row-value" style={{ fontSize: '0.8rem' }}>helmet/{DEVICE_ID}/+</span>
          </div>
          <div className="status-row">
            <span className="status-row-label">🔄 QoS Telemetry / Event</span>
            <span className="status-row-value">QoS 0 / QoS 1</span>
          </div>
          <div className="status-row">
            <span className="status-row-label">🏷️ Backend</span>
            <span className="status-row-value">Spring Boot 3 + JPA</span>
          </div>
        </div>
      </div>

      {/* Actions */}
      <div className="form-actions">
        <button
          className="btn btn-ghost"
          onClick={handleReset}
          disabled={!isDirty || saving}
          id="settings-reset-btn"
        >
          ↺ Hoàn tác
        </button>
        <button
          className="btn btn-primary"
          onClick={handleSave}
          disabled={!isDirty || saving}
          id="settings-save-btn"
        >
          {saving
            ? <><div className="spinner" style={{ width: 16, height: 16 }} /> Đang lưu...</>
            : '💾 Lưu cài đặt vào MySQL'}
        </button>
      </div>
    </div>
  );
}
