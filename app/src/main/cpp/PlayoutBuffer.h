#pragma once
#include "AudioCore.h"

namespace kl {
/** Audio-thread-owned, bounded reorder/playout buffer. Network enters via the SPSC queue.
 * 30-60 ms adaptive reserve, short loss concealment, and fractional clock correction.
 */
class PlayoutBuffer {
    static constexpr int Slots = 64;
    struct Slot { Block block{}; bool valid = false; };
    std::array<Slot, Slots> slots{};
    Block playing{}, lastGood{};
    uint32_t session = 0, nextSequence = 0;
    int count = 0, offset = Frames, target = 6, minimum = 6, losses = 0, stable = 0;
    uint32_t missing = 0;
    bool based = false, primed = false, interpolating = false;
    float a = 0, b = 0, fraction = 0, speed = 1, output = 0;
    float fillError = 0, clockEstimate = 0;
    float previousSource = 0, blendFrom = 0;
    int blend = 0;
    bool lowLatency = false;
    void clearPackets() { for (auto& slot : slots) slot.valid = false; count = 0; }
    void transition() { blendFrom = previousSource; blend = 48; }
    float source() {
        if (offset >= Frames) {
            auto& slot = slots[nextSequence & (Slots - 1)];
            if (slot.valid && slot.block.sequence == nextSequence) {
                playing = slot.block; slot.valid = false; --count;
                lastGood = playing;
                if (losses) transition();
                losses = 0;
                if (++stable >= 1000) { target = std::max(minimum, target - 1); stable = 0; }
            } else {
                ++missing; ++losses; stable = 0; target = std::min(std::max(12, minimum), target + 1);
                transition();
                const float attenuation = std::pow(0.65f, float(losses));
                for (int i = 0; i < Frames; ++i) playing.samples[i] = lastGood.samples[i] * attenuation;
                if (losses >= 4) {
                    // A real interruption: fade out and prime from the next arriving packet.
                    clearPackets(); based = false; primed = false;
                    playing.samples.fill(0);
                }
            }
            ++nextSequence; offset = 0;
        }
        float sample = playing.samples[offset++];
        if (blend > 0) {
            const float mix = float(49 - blend) / 48.f;
            sample = blendFrom + (sample - blendFrom) * mix; --blend;
        }
        previousSource = sample;
        return sample;
    }
public:
    void reset(uint32_t id) {
        clearPackets(); session = id; based = primed = interpolating = false;
        offset = Frames; target = minimum = lowLatency ? 3 : 6; losses = stable = 0; missing = 0;
        a = b = fraction = output = previousSource = blendFrom = 0; speed = 1; fillError = clockEstimate = 0; blend = 0;
        lastGood.samples.fill(0);
    }
    void configureLowLatency(bool enabled) {
        if (lowLatency == enabled) return;
        lowLatency = enabled;
        target = minimum = enabled ? 3 : 6;
        fillError = clockEstimate = 0;
    }
    bool push(const Block& block) {
        if (!session || block.session != session) return false;
        if (!based) { nextSequence = block.sequence; based = true; }
        const int32_t distance = static_cast<int32_t>(block.sequence - nextSequence);
        if (distance < 0) {
            if (primed || distance <= -Slots) return false;
            nextSequence = block.sequence;
        } else if (distance >= Slots) {
            clearPackets(); nextSequence = block.sequence; offset = Frames;
            primed = interpolating = false; transition();
        }
        auto& slot = slots[block.sequence & (Slots - 1)];
        if (slot.valid && slot.block.sequence == block.sequence) return false;
        if (!slot.valid) ++count;
        slot.block = block; slot.valid = true;
        return true;
    }
    float read() {
        if (!primed) {
            if (!based || count < target) { output *= 0.99f; return output; }
            primed = true; interpolating = false; offset = Frames; losses = 0; transition();
        }
        if (count > target + (lowLatency ? 6 : 12)) {
            // Bounded emergency backlog recovery, with a short fade instead of a hard jump.
            for (int budget = Slots; budget > 0 && count > target + 2; --budget, ++nextSequence) {
                auto& slot = slots[nextSequence & (Slots - 1)];
                if (slot.valid && slot.block.sequence == nextSequence) { slot.valid = false; --count; }
            }
            offset = Frames; interpolating = false; transition();
        }
        const int queued = count * Frames + (offset < Frames ? Frames - offset : 0);
        const float error = queued - (target - 0.5f) * Frames;
        fillError += 0.00005f * (error - fillError);
        clockEstimate = std::clamp(clockEstimate + fillError * 0.00000000001f, -0.003f, 0.003f);
        const float desired = 1.f + std::clamp(clockEstimate + fillError * 0.00001f, -0.004f, 0.004f);
        speed += 0.0001f * (desired - speed);
        if (!interpolating) { a = source(); b = source(); fraction = 0; interpolating = true; }
        output = a + (b - a) * fraction;
        fraction += speed;
        while (fraction >= 1.f) { fraction -= 1.f; a = b; b = source(); }
        return output;
    }
    void reserveForCallback(int frames) {
        minimum = std::clamp((frames + Frames - 1) / Frames + (lowLatency ? 1 : 2), lowLatency ? 3 : 6, 24);
        target = std::max(target, minimum);
    }
    int queuedFrames() const { return count * Frames + (offset < Frames ? Frames - offset : 0); }
    int targetMs() const { return target * 5; }
    uint32_t underruns() const { return missing; }
};
} // namespace kl