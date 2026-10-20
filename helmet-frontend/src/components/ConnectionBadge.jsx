/**
 * ConnectionBadge – hiển thị trạng thái kết nối WebSocket
 */
import { useState, useEffect } from 'react';
import { onConnectionChange } from '../api/realtime';

export function ConnectionBadge() {
  const [status, setStatus] = useState('connecting');

  useEffect(() => {
    return onConnectionChange(setStatus);
  }, []);

  const config = {
    connected:    { icon: '●', label: 'Realtime', cls: 'connected' },
    disconnected: { icon: '●', label: 'Mất kết nối', cls: 'disconnected' },
    connecting:   { icon: '◌', label: 'Đang kết nối', cls: 'connecting' },
  };
  const { icon, label, cls } = config[status] || config.connecting;

  return (
    <div className={`connection-badge ${cls}`} title={`WebSocket: ${status}`}>
      <span style={{ fontSize: '0.6rem', animation: status === 'connecting' ? 'spin 1.2s linear infinite' : 'none' }}>
        {icon}
      </span>
      {label}
    </div>
  );
}
