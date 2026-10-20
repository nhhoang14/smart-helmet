/**
 * STOMP / WebSocket realtime client
 * Kết nối tới /ws của Spring Boot backend, tự động reconnect khi rớt
 */
import { Client } from '@stomp/stompjs';

function getWsUrl() {
  const envUrl = import.meta.env.VITE_WS_URL;
  if (envUrl) {
    return envUrl.endsWith('/ws') ? envUrl : `${envUrl}/ws`;
  }
  // Tự động sử dụng host hiện tại và proxy qua Vite
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${window.location.host}/ws`;
}

let client = null;
const subscribers = new Map(); // topic → Set<callback>
let connectionListeners = [];
let _connected = false;
let _reconnectDelay = 2500;

function notifyConnection(state) {
  _connected = state === 'connected';
  connectionListeners.forEach(fn => fn(state));
}

function createClient() {
  const wsUrl = getWsUrl();
  console.log('[realtime] Connecting to WebSocket STOMP at:', wsUrl);

  const c = new Client({
    brokerURL: wsUrl,
    reconnectDelay: _reconnectDelay,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      console.log('[realtime] Connected successfully to Spring Boot STOMP broker');
      notifyConnection('connected');
      // Re-subscribe tất cả sau khi connect / reconnect
      subscribers.forEach((cbs, topic) => {
        if (cbs.size > 0) {
          c.subscribe(topic, (msg) => {
            try {
              const payload = JSON.parse(msg.body);
              cbs.forEach(cb => cb(payload));
            } catch (err) {
              console.error('[realtime] Parse error on topic ' + topic, err);
            }
          });
        }
      });
    },
    onDisconnect: () => {
      console.log('[realtime] Disconnected from STOMP broker');
      notifyConnection('disconnected');
    },
    onStompError: (frame) => {
      console.error('[realtime] STOMP error frame:', frame);
      notifyConnection('disconnected');
    },
    onWebSocketError: (err) => {
      console.warn('[realtime] WebSocket transport warning/error:', err);
      notifyConnection('disconnected');
    },
  });
  return c;
}

/**
 * Khởi tạo kết nối (gọi một lần từ App)
 */
export function initRealtime() {
  if (client) return;
  client = createClient();
  notifyConnection('connecting');
  client.activate();
}

/**
 * Subscribe một topic STOMP
 * @returns unsubscribe function
 */
export function subscribe(topic, callback) {
  if (!subscribers.has(topic)) {
    subscribers.set(topic, new Set());
  }
  subscribers.get(topic).add(callback);

  // Nếu đã kết nối thì subscribe ngay
  if (client && _connected) {
    const sub = client.subscribe(topic, (msg) => {
      try {
        const payload = JSON.parse(msg.body);
        subscribers.get(topic)?.forEach(cb => cb(payload));
      } catch (err) {
        console.error('[realtime] Parse error on topic ' + topic, err);
      }
    });
    return () => {
      subscribers.get(topic)?.delete(callback);
      try { sub.unsubscribe(); } catch {}
    };
  }

  return () => {
    subscribers.get(topic)?.delete(callback);
  };
}

/**
 * Lắng nghe trạng thái kết nối ('connecting' | 'connected' | 'disconnected')
 * @returns unsubscribe function
 */
export function onConnectionChange(fn) {
  connectionListeners.push(fn);
  // Gửi trạng thái hiện tại ngay
  fn(_connected ? 'connected' : 'disconnected');
  return () => {
    connectionListeners = connectionListeners.filter(l => l !== fn);
  };
}

export function isConnected() { return _connected; }
