/**
 * SiteMap – bản đồ Leaflet với OpenStreetMap, marker vị trí mũ
 */
import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Fix icon path cho Vite
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
});

function makeHelmetIcon(color = '#58a6ff') {
  return L.divIcon({
    className: '',
    html: `
      <div style="
        width: 36px; height: 36px;
        background: ${color};
        border: 3px solid white;
        border-radius: 50%;
        display: flex; align-items: center; justify-content: center;
        font-size: 18px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.4);
        animation: mapPulse 2s ease-in-out infinite;
      ">⛑️</div>
      <style>
        @keyframes mapPulse {
          0%,100% { box-shadow: 0 2px 8px rgba(0,0,0,0.4), 0 0 0 0 ${color}66; }
          50%      { box-shadow: 0 2px 8px rgba(0,0,0,0.4), 0 0 0 10px ${color}00; }
        }
      </style>
    `,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
    popupAnchor: [0, -20],
  });
}

const DEFAULT_LAT = 21.0285;
const DEFAULT_LNG = 105.8542;

export function SiteMap({ lat, lng, gasPpm = 0, warnThreshold = 400, dangerThreshold = 600, isOnline = true }) {
  const mapRef   = useRef(null);
  const mapObj   = useRef(null);
  const markerRef = useRef(null);

  const markerColor =
    !isOnline          ? '#8b949e' :
    gasPpm >= dangerThreshold ? '#f85149' :
    gasPpm >= warnThreshold   ? '#d29922' : '#3fb950';

  useEffect(() => {
    if (mapObj.current) return;
    const m = L.map(mapRef.current, {
      center: [lat || DEFAULT_LAT, lng || DEFAULT_LNG],
      zoom: 16,
      zoomControl: true,
      attributionControl: false,
    });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
    }).addTo(m);
    // Attribution nhỏ
    L.control.attribution({ prefix: '' }).addTo(m);
    mapObj.current = m;

    const marker = L.marker([lat || DEFAULT_LAT, lng || DEFAULT_LNG], {
      icon: makeHelmetIcon(markerColor),
    })
      .addTo(m)
      .bindPopup('<b>⛑️ Mũ bảo hộ #01</b><br/>Đang hoạt động');
    markerRef.current = marker;
  }, []);

  // Cập nhật vị trí marker
  useEffect(() => {
    if (!markerRef.current || !mapObj.current) return;
    const newLat = lat || DEFAULT_LAT;
    const newLng = lng || DEFAULT_LNG;
    markerRef.current.setLatLng([newLat, newLng]);
    markerRef.current.setIcon(makeHelmetIcon(markerColor));
    // Không pan liên tục, chỉ pan nếu ra khỏi view
    const bounds = mapObj.current.getBounds();
    if (!bounds.contains([newLat, newLng])) {
      mapObj.current.panTo([newLat, newLng]);
    }
  }, [lat, lng, markerColor]);

  return (
    <div>
      <div className="map-container" ref={mapRef} />
      <div style={{
        display: 'flex', justifyContent: 'space-between',
        marginTop: 8, fontSize: '0.75rem', color: 'var(--c-text-muted)',
        fontFamily: 'var(--font-mono)',
      }}>
        <span>📍 {lat ? `${lat.toFixed(5)}°N` : DEFAULT_LAT + '°N'}, {lng ? `${lng.toFixed(5)}°E` : DEFAULT_LNG + '°E'}</span>
        <span>OpenStreetMap</span>
      </div>
    </div>
  );
}
