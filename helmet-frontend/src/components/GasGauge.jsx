/**
 * GasGauge – đồng hồ bán nguyệt SVG hiển thị nồng độ khí (ppm)
 */

const SIZE = 200;
const CX = SIZE / 2;
const CY = SIZE / 2 + 10;
const R = 78;

function polarToXY(angleDeg, r = R) {
  const rad = (angleDeg - 180) * (Math.PI / 180);
  return {
    x: CX + r * Math.cos(rad),
    y: CY + r * Math.sin(rad),
  };
}

function describeArc(startDeg, endDeg, r = R) {
  const start = polarToXY(startDeg, r);
  const end   = polarToXY(endDeg, r);
  const large = endDeg - startDeg > 180 ? 1 : 0;
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${large} 1 ${end.x} ${end.y}`;
}

// Góc: 0° = trái, 180° = phải (bán nguyệt từ dưới lên)
// Dải: 0→180°
const MIN_ANGLE = 0;
const MAX_ANGLE = 180;

function ppmToAngle(ppm, maxPpm = 1000) {
  const clamped = Math.max(0, Math.min(ppm, maxPpm));
  return MIN_ANGLE + (clamped / maxPpm) * (MAX_ANGLE - MIN_ANGLE);
}

export function GasGauge({ ppm = 0, warnThreshold = 400, dangerThreshold = 600, maxPpm = 1000 }) {
  const warnAngle   = ppmToAngle(warnThreshold, maxPpm);
  const dangerAngle = ppmToAngle(dangerThreshold, maxPpm);
  const valueAngle  = ppmToAngle(ppm, maxPpm);

  const level =
    ppm >= dangerThreshold ? 'danger' :
    ppm >= warnThreshold   ? 'warn'   : 'safe';

  const colors = {
    safe:   'var(--c-safe)',
    warn:   'var(--c-warn)',
    danger: 'var(--c-danger)',
  };
  const activeColor = colors[level];

  // Tia kim chỉ
  const needleEnd = polarToXY(valueAngle);
  const needleBase1 = polarToXY(valueAngle - 90, 8);
  const needleBase2 = polarToXY(valueAngle + 90, 8);

  return (
    <div className="gauge-wrapper">
      <svg width={SIZE} height={SIZE / 2 + 60} className="gauge-svg" viewBox={`0 0 ${SIZE} ${SIZE / 2 + 70}`}>
        <defs>
          <linearGradient id="gauge-grad-safe" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="var(--c-safe)" stopOpacity="0.6" />
            <stop offset="100%" stopColor="var(--c-safe)" />
          </linearGradient>
          <linearGradient id="gauge-grad-warn" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="var(--c-warn)" stopOpacity="0.6" />
            <stop offset="100%" stopColor="var(--c-warn)" />
          </linearGradient>
          <linearGradient id="gauge-grad-danger" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="var(--c-danger)" stopOpacity="0.6" />
            <stop offset="100%" stopColor="var(--c-danger)" />
          </linearGradient>
          <filter id="gauge-glow">
            <feGaussianBlur stdDeviation="2.5" result="blur" />
            <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
        </defs>

        {/* Track nền */}
        <path
          d={describeArc(MIN_ANGLE, MAX_ANGLE)}
          fill="none"
          stroke="var(--c-surface-2)"
          strokeWidth="14"
          strokeLinecap="round"
        />

        {/* Đoạn safe */}
        <path
          d={describeArc(MIN_ANGLE, Math.min(valueAngle, warnAngle))}
          fill="none"
          stroke="var(--c-safe)"
          strokeWidth="14"
          strokeLinecap="round"
          style={{ transition: 'all 0.4s ease' }}
        />

        {/* Đoạn warn */}
        {valueAngle > warnAngle && (
          <path
            d={describeArc(warnAngle, Math.min(valueAngle, dangerAngle))}
            fill="none"
            stroke="var(--c-warn)"
            strokeWidth="14"
            strokeLinecap="round"
            style={{ transition: 'all 0.4s ease' }}
          />
        )}

        {/* Đoạn danger */}
        {valueAngle > dangerAngle && (
          <path
            d={describeArc(dangerAngle, valueAngle)}
            fill="none"
            stroke="var(--c-danger)"
            strokeWidth="14"
            strokeLinecap="round"
            style={{ transition: 'all 0.4s ease' }}
          />
        )}

        {/* Tick ngưỡng cảnh báo */}
        {(() => {
          const p1 = polarToXY(warnAngle, R - 20);
          const p2 = polarToXY(warnAngle, R + 4);
          return <line x1={p1.x} y1={p1.y} x2={p2.x} y2={p2.y} stroke="var(--c-warn)" strokeWidth="2" />;
        })()}
        {/* Tick ngưỡng nguy hiểm */}
        {(() => {
          const p1 = polarToXY(dangerAngle, R - 20);
          const p2 = polarToXY(dangerAngle, R + 4);
          return <line x1={p1.x} y1={p1.y} x2={p2.x} y2={p2.y} stroke="var(--c-danger)" strokeWidth="2" />;
        })()}

        {/* Kim chỉ */}
        <polygon
          points={`${needleBase1.x},${needleBase1.y} ${needleBase2.x},${needleBase2.y} ${needleEnd.x},${needleEnd.y}`}
          fill={activeColor}
          filter="url(#gauge-glow)"
          style={{ transition: 'all 0.4s ease', opacity: 0.9 }}
        />
        {/* Trục kim */}
        <circle cx={CX} cy={CY} r={7} fill={activeColor} filter="url(#gauge-glow)" />
        <circle cx={CX} cy={CY} r={4} fill="var(--c-bg)" />

        {/* Giá trị số */}
        <text
          x={CX} y={CY + 30}
          className="gauge-value-label"
          fill={activeColor}
          style={{ transition: 'fill 0.4s ease' }}
        >
          {ppm != null ? ppm.toFixed(0) : '---'}
        </text>
        <text x={CX} y={CY + 48} className="gauge-unit-label">ppm</text>

        {/* Nhãn ngưỡng */}
        {(() => {
          const warnP = polarToXY(warnAngle, R + 18);
          return (
            <text x={warnP.x} y={warnP.y} className="gauge-threshold-label" fill="var(--c-warn)">
              {warnThreshold}
            </text>
          );
        })()}
        {(() => {
          const dangP = polarToXY(dangerAngle, R + 18);
          return (
            <text x={dangP.x} y={dangP.y} className="gauge-threshold-label" fill="var(--c-danger)">
              {dangerThreshold}
            </text>
          );
        })()}

        {/* Label trái/phải */}
        <text x={12} y={CY + 20} className="gauge-threshold-label">0</text>
        <text x={SIZE - 22} y={CY + 20} className="gauge-threshold-label">{maxPpm}</text>
      </svg>
    </div>
  );
}
