#pragma once
#include <math.h>

namespace helmet {
enum class GasLevel { WARMING_UP, SAFE, WARNING, DANGER };

// Hardware-independent thresholds and hysteresis, shared by firmware and tests.
class GasPolicy {
public:
    GasPolicy(float warn = 300, float danger = 600, float adcWarn = 1200,
              float adcDanger = 2200, float ppmHysteresis = 25, float adcHysteresis = 50)
        : warn_(warn), danger_(danger), adcWarn_(adcWarn), adcDanger_(adcDanger),
          ppmHysteresis_(ppmHysteresis), adcHysteresis_(adcHysteresis) {}
    bool setThresholds(float warn, float danger) {
        if (!isfinite(warn) || !isfinite(danger) || warn <= ppmHysteresis_ || danger <= warn || danger > 10000) return false;
        warn_ = warn; danger_ = danger;
        return true;
    }
    GasLevel update(float ppm, float adc, bool ready, bool enabled, bool digitalDanger = false) {
        if (!enabled) return level_ = GasLevel::SAFE;
        if (!ready || !isfinite(ppm) || !isfinite(adc) || ppm < 0 || adc < 0 || adc > 4095)
            return level_ = GasLevel::WARMING_UP;
        if (digitalDanger || ppm >= danger_ || adc >= adcDanger_) return level_ = GasLevel::DANGER;
        if (level_ == GasLevel::DANGER &&
            (ppm > danger_ - ppmHysteresis_ || adc > adcDanger_ - adcHysteresis_)) return level_;
        if (ppm >= warn_ || adc >= adcWarn_) return level_ = GasLevel::WARNING;
        if (ppm <= warn_ - ppmHysteresis_ && adc <= adcWarn_ - adcHysteresis_) return level_ = GasLevel::SAFE;
        // A decreasing danger condition has crossed its own hysteresis boundary.
        if (level_ == GasLevel::DANGER) return level_ = GasLevel::WARNING;
        if (level_ == GasLevel::WARMING_UP) level_ = GasLevel::SAFE;
        return level_;
    }
private:
    float warn_, danger_, adcWarn_, adcDanger_, ppmHysteresis_, adcHysteresis_;
    GasLevel level_ = GasLevel::WARMING_UP;
};
}
