/**
 * useTelemetryStream – lưu giữ các mẫu đo gần nhất từ backend
 * Nạp dữ liệu ban đầu từ MySQL qua REST API, sau đó nhận realtime qua WebSocket
 */
import { useState, useEffect } from 'react';
import { getTelemetry } from '../api/client';
import { subscribe } from '../api/realtime';

const DEVICE_ID = import.meta.env.VITE_DEVICE_ID || '01';
const MAX_SAMPLES = 60;

export function useTelemetryStream() {
  const [samples, setSamples] = useState([]);
  const [latest, setLatest] = useState(null);

  const pushSample = (data) => {
    const formatted = {
      gas: data.gas ?? data.gasPpm,
      worn: data.worn ?? data.isWorn,
      fall: data.fall ?? data.fallState,
      lat: data.lat,
      lng: data.lng,
      ts: data.ts ?? (data.recordedAt ? Math.floor(new Date(data.recordedAt).getTime() / 1000) : Math.floor(Date.now() / 1000)),
    };
    setLatest(formatted);
    setSamples(prev => {
      const next = [...prev, formatted];
      return next.length > MAX_SAMPLES ? next.slice(-MAX_SAMPLES) : next;
    });
  };

  // Nạp lịch sử ban đầu từ MySQL DB qua REST API
  useEffect(() => {
    let cancelled = false;
    const now = new Date();
    const hourAgo = new Date(now.getTime() - 2 * 60 * 60 * 1000); // 2 giờ gần nhất

    getTelemetry(DEVICE_ID, hourAgo.toISOString(), now.toISOString())
      .then(data => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : (data.content || []);
        if (list.length > 0) {
          const mapped = list.map(t => ({
            gas: Number(t.gasPpm ?? t.gas ?? 0),
            worn: Boolean(t.isWorn ?? t.worn),
            fall: Boolean(t.fallState ?? t.fall),
            lat: t.lat,
            lng: t.lng,
            ts: t.recordedAt ? Math.floor(new Date(t.recordedAt).getTime() / 1000) : (t.ts ?? Math.floor(Date.now() / 1000)),
          }));
          setSamples(mapped.slice(-MAX_SAMPLES));
          setLatest(mapped[mapped.length - 1]);
        }
      })
      .catch(err => {
        console.warn('Chưa thể tải dữ liệu telemetry ban đầu từ backend:', err.message);
      });

    return () => { cancelled = true; };
  }, []);

  // Subscribe realtime qua STOMP WebSocket
  useEffect(() => {
    const unsub = subscribe(`/topic/devices/${DEVICE_ID}/telemetry`, (payload) => {
      pushSample(payload);
    });
    return unsub;
  }, []);

  return { samples, latest, deviceId: DEVICE_ID };
}
