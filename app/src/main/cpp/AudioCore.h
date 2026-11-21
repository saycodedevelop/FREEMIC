#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>

namespace kl {
constexpr int Rate = 48000, Frames = 240, AudioPort = 40102;
constexpr int Header = 24, PacketBytes = Header + Frames * 2;
inline void put16(uint8_t* b, uint16_t v) { b[0] = v >> 8; b[1] = v & 255; }
inline void put32(uint8_t* b, uint32_t v) { put16(b, v >> 16); put16(b + 2, v & 65535); }
inline uint16_t get16(const uint8_t* b) { return uint16_t(b[0]) << 8 | b[1]; }
inline uint32_t get32(const uint8_t* b) { return uint32_t(get16(b)) << 16 | get16(b + 2); }
struct Block {
    uint32_t session = 0, sequence = 0, timestamp = 0;
    std::array<float, Frames> samples{};
};
inline void encode(const Block& block, uint8_t* b) {
    b[0]='K'; b[1]='L'; b[2]='A'; b[3]='1';
    put32(b+4, block.session); put32(b+8, block.sequence); put32(b+12, block.timestamp);
    put32(b+16, Rate); put16(b+20, Frames); b[22]=1; b[23]=1; // mono, PCM16-BE
    for (int i=0; i<Frames; ++i) {
        auto pcm = static_cast<int16_t>(std::clamp(block.samples[i], -1.f, 1.f) * 32767.f);
        put16(b+Header+i*2, static_cast<uint16_t>(pcm));
    }
}
inline bool decode(const uint8_t* b, int length, Block& block) {
    if (length != PacketBytes || b[0]!='K' || b[1]!='L' || b[2]!='A' || b[3]!='1' ||
        get32(b+16) != Rate || get16(b+20) != Frames || b[22]!=1 || b[23]!=1) return false;
    block.session=get32(b+4); block.sequence=get32(b+8); block.timestamp=get32(b+12);
    for (int i=0; i<Frames; ++i) {
        const int value = get16(b+Header+i*2);
        block.samples[i] = (value < 32768 ? value : value - 65536) / 32768.f;
    }
    return block.session != 0;
}
/** One producer / one consumer. Each side exclusively owns its index. Never reset live. */
template<size_t N> class Queue {
    static_assert((N & (N-1)) == 0, "power of two required");
    std::array<Block, N> blocks{};
    alignas(64) std::atomic<uint32_t> write{0};
    alignas(64) std::atomic<uint32_t> read{0};
public:
    bool push(const Block& b) {
        auto w=write.load(std::memory_order_relaxed);
        if (w-read.load(std::memory_order_acquire) >= N) return false;
        blocks[w & (N-1)]=b; write.store(w+1, std::memory_order_release); return true;
    }
    bool pop(Block& b) {
        auto r=read.load(std::memory_order_relaxed);
        if (r==write.load(std::memory_order_acquire)) return false;
        b=blocks[r & (N-1)]; read.store(r+1, std::memory_order_release); return true;
    }
    uint32_t size() const { return write.load(std::memory_order_acquire)-read.load(std::memory_order_acquire); }
};
struct Effects {
    std::atomic<float> gain{1}, gate{0.f}, echoMs{140}, feedback{0.2f}, echoMix{0},
        reverb{0}, low{1}, mid{1}, high{1};
};
/** Starter DSP: smoothed three-band crossover, soft gate, echo, three comb reverberators. */
class Dsp {
    std::array<float, Rate> echo{};
    std::array<float, 5000> comb1{}, comb2{}, comb3{};
    size_t pos=0, r1=0, r2=0, r3=0;
    float lpLow=0, lpHigh=0, envelope=0, gateGain=1, gain=1, low=1, mid=1, high=1;
public:
    float process(float x, const Effects& e) {
        gain += 0.001f*(e.gain.load(std::memory_order_relaxed)-gain);
        low += 0.001f*(e.low.load(std::memory_order_relaxed)-low);
        mid += 0.001f*(e.mid.load(std::memory_order_relaxed)-mid);
        high += 0.001f*(e.high.load(std::memory_order_relaxed)-high);
        x *= gain;
        envelope = std::max(std::abs(x), envelope*0.9995f);
        float target = envelope >= e.gate.load(std::memory_order_relaxed) ? 1.f : 0.f;
        gateGain += (target > gateGain ? 0.01f : 0.001f)*(target-gateGain);
        x *= gateGain;
        lpLow += 0.02585f*(x-lpLow); // 200 Hz
        lpHigh += 0.32477f*(x-lpHigh); // 3 kHz
        x = low*lpLow + mid*(lpHigh-lpLow) + high*(x-lpHigh);
        const auto delay = static_cast<size_t>(std::clamp(e.echoMs.load(), 5.f, 700.f)*48);
        const float delayed = echo[(pos+echo.size()-delay)%echo.size()];
        echo[pos] = std::clamp(x+delayed*e.feedback.load(), -2.f, 2.f);
        pos=(pos+1)%echo.size();
        const float a=comb1[r1], b=comb2[r2], c=comb3[r3];
        comb1[r1]=x+a*0.61f; comb2[r2]=x+b*0.57f; comb3[r3]=x+c*0.53f;
        r1=(r1+1)%1499; r2=(r2+1)%2081; r3=(r3+1)%2903;
        return std::clamp(x+delayed*e.echoMix.load()+(a+b+c)*0.2f*e.reverb.load(), -1.f, 1.f);
    }
};
} // namespace kl