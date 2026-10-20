/**
 * Phát âm thanh cảnh báo trên trình duyệt bằng Web Audio API.
 * Cần người dùng tương tác trang ít nhất một lần mới hoạt động.
 */

let audioCtx = null;

function getCtx() {
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  }
  return audioCtx;
}

/**
 * Phát tiếng bíp ngắn
 * @param {'warning'|'danger'|'sos'} level
 */
export function playAlert(level = 'warning') {
  try {
    const ctx = getCtx();
    if (ctx.state === 'suspended') ctx.resume();

    const configs = {
      warning: [{ freq: 880, dur: 0.15 }, { freq: 0, dur: 0.08 }, { freq: 880, dur: 0.15 }],
      danger:  [
        { freq: 1047, dur: 0.1 }, { freq: 0, dur: 0.05 },
        { freq: 1047, dur: 0.1 }, { freq: 0, dur: 0.05 },
        { freq: 1047, dur: 0.1 }, { freq: 0, dur: 0.05 },
        { freq: 1319, dur: 0.3 },
      ],
      sos: [
        { freq: 800, dur: 0.1 }, { freq: 0, dur: 0.1 },
        { freq: 800, dur: 0.1 }, { freq: 0, dur: 0.1 },
        { freq: 800, dur: 0.1 }, { freq: 0, dur: 0.2 },
        { freq: 800, dur: 0.3 }, { freq: 0, dur: 0.1 },
        { freq: 800, dur: 0.3 }, { freq: 0, dur: 0.1 },
        { freq: 800, dur: 0.3 }, { freq: 0, dur: 0.2 },
        { freq: 800, dur: 0.1 }, { freq: 0, dur: 0.1 },
        { freq: 800, dur: 0.1 }, { freq: 0, dur: 0.1 },
        { freq: 800, dur: 0.1 },
      ],
    };

    const steps = configs[level] || configs.warning;
    let t = ctx.currentTime;

    for (const step of steps) {
      if (step.freq === 0) {
        t += step.dur;
        continue;
      }
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.type = 'square';
      osc.frequency.setValueAtTime(step.freq, t);
      gain.gain.setValueAtTime(0.15, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + step.dur);
      osc.start(t);
      osc.stop(t + step.dur);
      t += step.dur;
    }
  } catch (e) {
    // Bỏ qua lỗi audio (autoplay policy)
    console.warn('[alertSound] Không thể phát âm thanh:', e.message);
  }
}
