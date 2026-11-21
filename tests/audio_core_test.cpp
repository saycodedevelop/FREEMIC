#include "AudioCore.h"
#include <cassert>
#include <thread>
#include <iostream>
using namespace kl;
int main() {
    Block original; original.session=123; original.sequence=0xfffffffe; original.timestamp=42;
    for(int i=0;i<Frames;++i) original.samples[i]=(i%5-2)*0.5f;
    std::array<uint8_t,PacketBytes> packet{};
    encode(original,packet.data());
    assert(packet[4]==0 && packet[7]==123 && packet[8]==255);
    Block decoded;
    assert(decode(packet.data(),packet.size(),decoded));
    assert(decoded.session==123 && decoded.sequence==0xfffffffe && decoded.timestamp==42);
    for(int i=0;i<Frames;++i) assert(std::abs(original.samples[i]-decoded.samples[i])<0.00007f);
    for(int n=0;n<PacketBytes;++n) assert(!decode(packet.data(),n,decoded));
    assert(!decode(packet.data(),PacketBytes+1,decoded));
    for(int offset: {0,16,20,22,23}) {
        auto bad=packet; bad[offset]^=0x7f; assert(!decode(bad.data(),bad.size(),decoded));
    }
    auto noSession=original; noSession.session=0; encode(noSession,packet.data());
    assert(!decode(packet.data(),packet.size(),decoded));
    Queue<8> queue;
    Block empty;
    assert(!queue.pop(empty));
    for(int i=0;i<8;++i) { empty.sequence=i; assert(queue.push(empty)); }
    assert(!queue.push(empty));
    for(int i=0;i<8;++i) { assert(queue.pop(empty)); assert(empty.sequence==uint32_t(i)); }
    constexpr uint32_t Count=100000;
    std::thread producer([&] {
        Block b;
        for(uint32_t i=0;i<Count;++i) { b.sequence=i; b.samples[0]=float(i); while(!queue.push(b)) std::this_thread::yield(); }
    });
    for(uint32_t i=0;i<Count;++i) {
        Block b; while(!queue.pop(b)) std::this_thread::yield();
        assert(b.sequence==i && b.samples[0]==float(i));
    }
    producer.join();
    Dsp dsp; Effects effects;
    effects.gain.store(4); effects.feedback.store(0.85f); effects.echoMix.store(1); effects.reverb.store(1);
    for(int i=0;i<Rate*5;++i) {
        const float sample=dsp.process(i%240<120 ? 0.8f : -0.8f,effects);
        assert(std::isfinite(sample) && std::abs(sample)<=1);
    }
    std::cout << "PCM validation, wire format, SPSC stress and DSP bounds passed\n";
}