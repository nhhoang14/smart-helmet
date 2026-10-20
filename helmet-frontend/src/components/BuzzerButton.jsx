/**
 * BuzzerButton – nút bật/tắt còi tìm mũ qua REST API và MQTT
 */
import { useState } from 'react';
import { setBuzzer } from '../api/client';

export function BuzzerButton({ deviceId = '01', buzzerOn = false, onStateChange }) {
  const [pending, setPending] = useState(false);
  const [localOn, setLocalOn] = useState(buzzerOn);

  // Sync với prop khi state thay đổi từ bên ngoài
  if (!pending && buzzerOn !== localOn) setLocalOn(buzzerOn);

  const handleClick = async () => {
    if (pending) return;
    const newState = !localOn;
    setPending(true);
    try {
      await setBuzzer(deviceId, newState);
      setLocalOn(newState);
      onStateChange?.(newState);
    } catch (e) {
      alert(`Lỗi gửi lệnh điều khiển còi: ${e.message}`);
    } finally {
      setPending(false);
    }
  };

  return (
    <div>
      <div className="card-title" style={{ marginBottom: 12 }}>
        🔔 Điều khiển còi
      </div>
      <button
        className={`buzzer-btn ${localOn ? 'on' : 'off'}`}
        onClick={handleClick}
        disabled={pending}
        id="buzzer-toggle-btn"
      >
        {pending ? (
          <><div className="spinner" style={{ width: 18, height: 18 }} /> Đang gửi lệnh...</>
        ) : localOn ? (
          <><span style={{ fontSize: '1.2rem' }}>🔔</span> Tắt còi</>
        ) : (
          <><span style={{ fontSize: '1.2rem' }}>🔕</span> Bật còi tìm mũ</>
        )}
      </button>
      <p style={{
        fontSize: '0.75rem', color: 'var(--c-text-muted)',
        marginTop: 8, textAlign: 'center',
      }}>
        {localOn ? '⚡ Đang bật còi (lệnh gửi xuống thiết bị qua MQTT)' : '⚡ Bấm để phát còi cảnh báo định vị mũ'}
      </p>
    </div>
  );
}
