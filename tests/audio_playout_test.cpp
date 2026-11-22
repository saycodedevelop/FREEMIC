#include "PlayoutBuffer.h"
#include <cassert>
#include <iostream>
#include <vector>
using namespace kl;
static Block block(uint32_t sequence, float value=0.4f) {
    Block b; b.session=7; b.sequence=sequence; b.samples.fill(value); return b;
}
static void reordered() {
    PlayoutBuffer buffer; buffer.reset(7);
    for (uint32_t i : {0,2,1,3,5,4}) assert(buffer.push(block(i,0.1f*(i+1))));
    assert(!buffer.push(block(2)));
    auto foreign=block(6); foreign.session=8; assert(!buffer.push(foreign));
    float sample=0;
    for (int i=0;i<120;++i) sample=buffer.read();
    assert(std::abs(sample-0.1f)<0.01f);
    for (int i=0;i<240;++i) sample=buffer.read();
    assert(std::abs(sample-0.2f)<0.01f);
    assert(!buffer.push(block(0)));
    assert(buffer.underruns()==0);
    buffer.reset(7);
    for (uint32_t i=0;i<6;++i) buffer.push(block(0xfffffffcu+i));
    for (int i=0;i<1000;++i) assert(std::isfinite(buffer.read()));
    assert(buffer.underruns()==0);
}
static void lossAndRestart() {
    PlayoutBuffer buffer; buffer.reset(7);
    for (uint32_t i=0;i<6;++i) buffer.push(block(i));
    // A single dropped datagram should not cause a fresh 30 ms silent rebuffer.
    for (int i=0;i<15*Frames;++i) {
        if (i>0 && i%Frames==0) { uint32_t seq=uint32_t(i/Frames+5); if (seq!=6) buffer.push(block(seq)); }
        const float sample=buffer.read();
        assert(std::isfinite(sample));
        if (i>100) assert(sample>0.15f);
    }
    assert(buffer.underruns()==1);
    assert(buffer.targetMs()>30);
    for (int i=0;i<Rate;++i) buffer.read();
    for (uint32_t i=1000;i<1012;++i) buffer.push(block(i));
    float output=0;
    for (int i=0;i<Frames;++i) output=buffer.read();
    assert(output>0.3f);
    assert(buffer.targetMs()<=60);
    buffer.reset(8);
    assert(!buffer.push(block(0)));
    assert(buffer.read()==0);
}
static void simulatedWifi(double clockPpm, int callbackFrames=48, bool fast=false, bool stress=true) {
    struct Event { double time; Block b; };
    std::vector<Event> events;
    constexpr int Seconds=60;
    const double rate=1.0+clockPpm/1000000.;
    for (uint32_t seq=0;seq<Seconds*200;++seq) {
        // Delayed and reversed datagrams, plus periodically bunched Wi-Fi deliveries.
        const double jitter[]={0,7000,-2000,3000,-1000};
        double arrival=seq*5000./rate+(stress ? jitter[seq%5] : 0);
        if (stress && seq%100<3) arrival=(seq-seq%100+3)*5000./rate;
        events.push_back({arrival,block(seq)});
    }
    std::sort(events.begin(),events.end(),[](const Event& a,const Event& b){return a.time<b.time;});
    PlayoutBuffer buffer; buffer.reset(7);
    buffer.configureLowLatency(fast);
    size_t event=0;
    int maxQueued=0, silent=0;
    buffer.reserveForCallback(callbackFrames);
    for (int sampleTime=0;sampleTime<Seconds*Rate-Rate/10;sampleTime+=callbackFrames) {
        const double time=sampleTime*(1000000./Rate);
        while (event<events.size() && events[event].time<=time) buffer.push(events[event++].b);
        for (int i=0;i<callbackFrames;++i) {
            const float x=buffer.read();
            assert(std::isfinite(x) && std::abs(x)<=1);
            if (sampleTime>Rate*0.15 && x<0.1f) ++silent;
        }
        maxQueued=std::max(maxQueued,buffer.queuedFrames());
    }
    std::cout<<"clock "<<clockPpm<<" ppm, callback="<<callbackFrames<<": underruns="<<buffer.underruns()<<", silent="<<silent<<", max queue="<<maxQueued<<" frames"<<std::endl;
    // The fast profile trades reserve for latency under adversarial Wi-Fi jitter.
    // Permit at most 0.2% briefly concealed blocks; stable and clean fast runs must have none.
    assert(buffer.underruns() <= (fast && stress ? 24u : 0u));
    assert(silent==0);
    assert(maxQueued<Frames*16);
}
static void fastStartupAndSwitching() {
    PlayoutBuffer buffer; buffer.configureLowLatency(true); buffer.reset(7);
    buffer.reserveForCallback(192);
    assert(buffer.targetMs()==15);
    buffer.push(block(0)); buffer.push(block(1));
    for (int i=0;i<Frames;++i) assert(buffer.read()==0);
    buffer.push(block(2));
    float output=0;
    for (int i=0;i<Frames;++i) output=buffer.read();
    assert(output>0.3f);
    buffer.configureLowLatency(false); buffer.reserveForCallback(192);
    assert(buffer.targetMs()>=30);
    buffer.configureLowLatency(true); buffer.reserveForCallback(1920);
    assert(buffer.targetMs()>=45);
}
int main() {
    reordered(); lossAndRestart(); fastStartupAndSwitching();
    simulatedWifi(0); simulatedWifi(-1500); simulatedWifi(1500); simulatedWifi(-1500,1920); simulatedWifi(1500,1920);
    simulatedWifi(0,192,true); simulatedWifi(-1500,192,true); simulatedWifi(1500,192,true); simulatedWifi(1500,1920,true);
    simulatedWifi(-1500,192,true,false); simulatedWifi(1500,192,true,false);
    std::cout<<"Reordering, duplicate/session rejection, wraparound, loss concealment, restart and 60-second clock drift simulations passed\n";
}