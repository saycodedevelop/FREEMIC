#include "AudioCore.h"
#include <oboe/Oboe.h>
#include <jni.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <poll.h>
#include <unistd.h>
#include <chrono>
#include <memory>
#include <thread>

using namespace kl;
static_assert(std::atomic<float>::is_always_lock_free, "audio controls must be lock free");

class Engine final : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
    struct Peer {
        std::atomic<uint32_t> id{0}, ip{0};
        Queue<32> queue;
        // network worker owns sequence tracking
        uint32_t receivedId=0, expected=0;
        bool hasSequence=false;
        // audio callback owns playout tracking
        Block playing{};
        uint32_t playingId=0;
        int offset=Frames;
        bool primed=false;
    };
    std::array<Peer,2> peers;
    Queue<32> captured;
    std::shared_ptr<oboe::AudioStream> stream;
    std::thread worker;
    std::atomic<bool> running{false}, failed{false};
    std::atomic<float> vu{0}, hostGain{1};
    std::atomic<int> underruns{0}, queuedMs{0}, delayMs{0};
    bool mic=false;
    int socketFd=-1;
    sockaddr_in destination{};
    uint32_t session=0;
    Effects effects;
    Dsp dsp;
    Block capture{};
    int captureOffset=0;
    std::array<float, Rate/2+1> delayLine{};
    size_t delayPos=0;
    float smoothHostGain=1;

    bool openAudio(int deviceId) {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(mic ? oboe::Direction::Input : oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setSampleRate(Rate)->setChannelCount(1)->setFormat(oboe::AudioFormat::Float)
            ->setFormatConversionAllowed(true)->setChannelConversionAllowed(true)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setDataCallback(this)->setErrorCallback(this);
        if (mic) builder.setInputPreset(oboe::InputPreset::Unprocessed);
        else builder.setUsage(oboe::Usage::Media)->setContentType(oboe::ContentType::Music);
        if (deviceId != 0) builder.setDeviceId(deviceId);
        auto result=builder.openStream(stream);
        if (result != oboe::Result::OK) {
            builder.setSharingMode(oboe::SharingMode::Shared);
            result=builder.openStream(stream);
        }
        if (result != oboe::Result::OK || !stream || stream->getSampleRate()!=Rate) return false;
        stream->setBufferSizeInFrames(stream->getFramesPerBurst()*2);
        return stream->requestStart()==oboe::Result::OK;
    }
    void networkLoop() {
        std::array<uint8_t,2048> bytes{};
        while (running.load(std::memory_order_acquire)) {
            if (mic) {
                Block b;
                int budget=8;
                while (budget-- && captured.pop(b)) {
                    encode(b, bytes.data());
                    sendto(socketFd, bytes.data(), PacketBytes, MSG_DONTWAIT,
                        reinterpret_cast<sockaddr*>(&destination), sizeof(destination));
                }
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            } else {
                pollfd fd{socketFd, POLLIN, 0};
                if (poll(&fd,1,10)<=0) continue;
                sockaddr_in from{}; socklen_t length=sizeof(from);
                int count=recvfrom(socketFd, bytes.data(), bytes.size(), MSG_DONTWAIT,
                    reinterpret_cast<sockaddr*>(&from), &length);
                Block block;
                if (!decode(bytes.data(),count,block)) continue;
                for (auto& peer:peers) {
                    auto id=peer.id.load(std::memory_order_acquire);
                    if (!id || id!=block.session || peer.ip.load()!=from.sin_addr.s_addr) continue;
                    if (peer.receivedId!=id) {
                        peer.receivedId=id; peer.hasSequence=false;
                    }
                    if (peer.hasSequence) {
                        const int32_t gap=static_cast<int32_t>(block.sequence-peer.expected);
                        if (gap<0) break; // duplicates / late datagrams
                        // Bounded concealment; do not fill huge gaps or grow latency indefinitely.
                        Block silence; silence.session=id;
                        for (int i=0; i<std::min(gap,3); ++i) peer.queue.push(silence);
                    }
                    peer.expected=block.sequence+1; peer.hasSequence=true;
                    peer.queue.push(block); // full queue drops newest; consumer trims backlog
                    break;
                }
            }
        }
    }
    float readPeer(Peer& p) {
        const auto id=p.id.load(std::memory_order_acquire);
        if (!id) { p.primed=false; p.offset=Frames; return 0; }
        if (p.playingId!=id) { p.playingId=id; p.primed=false; p.offset=Frames; }
        if (p.offset==Frames) {
            if (!p.primed) {
                if (p.queue.size()<3) return 0; // 15 ms initial target
                p.primed=true;
            }
            Block next;
            while (p.queue.size()>7) p.queue.pop(next); // receiver owns trimming
            bool found=false;
            while (p.queue.pop(next)) { if (next.session==id) { found=true; break; } }
            if (!found) { p.primed=false; underruns.fetch_add(1); return 0; }
            p.playing=next; p.offset=0;
        }
        return p.playing.samples[p.offset++];
    }
public:
    ~Engine() { stop(); }
    bool start(bool isMic, const char* ip, uint32_t id, int deviceId) {
        mic=isMic; session=id; capture.session=id;
        socketFd=socket(AF_INET,SOCK_DGRAM,0);
        if (socketFd<0) return false;
        int buffer=16384;
        setsockopt(socketFd,SOL_SOCKET,SO_RCVBUF,&buffer,sizeof(buffer));
        if (mic) {
            destination.sin_family=AF_INET; destination.sin_port=htons(AudioPort);
            if (inet_pton(AF_INET,ip,&destination.sin_addr)!=1) { stop(); return false; }
        } else {
            sockaddr_in local{}; local.sin_family=AF_INET; local.sin_port=htons(AudioPort);
            local.sin_addr.s_addr=htonl(INADDR_ANY);
            if (bind(socketFd,reinterpret_cast<sockaddr*>(&local),sizeof(local))<0) { stop(); return false; }
        }
        running.store(true,std::memory_order_release);
        worker=std::thread([this] { networkLoop(); });
        if (!openAudio(deviceId)) { stop(); return false; }
        return true;
    }
    void stop() {
        if (stream) { stream->requestStop(); stream->close(); stream.reset(); }
        running.store(false,std::memory_order_release);
        if (worker.joinable()) worker.join();
        if (socketFd>=0) { close(socketFd); socketFd=-1; }
    }
    bool allow(uint32_t id, const char* ip) {
        in_addr address{};
        if (!id || inet_pton(AF_INET,ip,&address)!=1) return false;
        for (auto& p:peers) if (p.id.load()==0) {
            p.ip.store(address.s_addr); p.id.store(id,std::memory_order_release); return true;
        }
        return false;
    }
    void remove(uint32_t id) { for (auto& p:peers) if (p.id.load()==id) p.id.store(0); }
    Effects& controls() { return effects; }
    void mix(float gain, int delay) { hostGain.store(std::clamp(gain,0.f,2.f)); delayMs.store(std::clamp(delay,0,500)); }
    std::array<float,4> metrics() { return {vu.load(),float(underruns.load()),float(queuedMs.load()),failed.load()?1.f:0.f}; }
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        auto* samples=static_cast<float*>(data);
        float peak=0;
        if (mic) {
            for (int i=0;i<frames;++i) {
                float x=dsp.process(samples[i],effects);
                peak=std::max(peak,std::abs(x)); capture.samples[captureOffset++]=x;
                if (captureOffset==Frames) {
                    captured.push(capture); capture.sequence++; capture.timestamp+=Frames; captureOffset=0;
                }
            }
        } else {
            const size_t delay=static_cast<size_t>(delayMs.load())*48;
            const float gain=hostGain.load();
            for (int i=0;i<frames;++i) {
                float x=0;
                for (auto& p:peers) x+=readPeer(p);
                smoothHostGain+=0.001f*(gain-smoothHostGain);
                x*=smoothHostGain;
                delayLine[delayPos]=x;
                x=delayLine[(delayPos+delayLine.size()-delay)%delayLine.size()];
                delayPos=(delayPos+1)%delayLine.size();
                samples[i]=std::clamp(x,-1.f,1.f); peak=std::max(peak,std::abs(samples[i]));
            }
            uint32_t queued=0;
            for (auto& p:peers) if (p.id.load()!=0) queued=std::max(queued,p.queue.size());
            queuedMs.store(queued*5);
        }
        vu.store(peak);
        return oboe::DataCallbackResult::Continue;
    }
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result) override {
        // Never destroy/reopen streams on the error callback thread. UI requests reconnect.
        failed.store(true);
    }
};

static std::unique_ptr<Engine> engine;
static Effects savedEffects;
static float savedGain=1;
static int savedDelay=0;
static void copyEffects(Effects& to,const Effects& from) {
    to.gain.store(from.gain.load()); to.gate.store(from.gate.load()); to.echoMs.store(from.echoMs.load());
    to.feedback.store(from.feedback.load()); to.echoMix.store(from.echoMix.load()); to.reverb.store(from.reverb.load());
    to.low.store(from.low.load()); to.mid.store(from.mid.load()); to.high.store(from.high.load());
}
extern "C" {
JNIEXPORT jboolean JNICALL Java_com_karaokelink_core_audio_NativeAudio_startHost(JNIEnv*,jobject,jint device) {
    engine.reset(); engine=std::make_unique<Engine>(); copyEffects(engine->controls(),savedEffects); engine->mix(savedGain,savedDelay);
    if (engine->start(false,"",0,device)) return JNI_TRUE;
    engine.reset(); return JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_com_karaokelink_core_audio_NativeAudio_startMic(JNIEnv* env,jobject,jstring ip,jint session,jint device) {
    engine.reset(); engine=std::make_unique<Engine>(); copyEffects(engine->controls(),savedEffects);
    const char* address=env->GetStringUTFChars(ip,nullptr);
    if (!address) { engine.reset(); return JNI_FALSE; }
    bool ok=engine->start(true,address,static_cast<uint32_t>(session),device);
    env->ReleaseStringUTFChars(ip,address);
    if (!ok) engine.reset();
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_com_karaokelink_core_audio_NativeAudio_allowPeer(JNIEnv* env,jobject,jint id,jstring ip) {
    const char* address=env->GetStringUTFChars(ip,nullptr);
    if (!address) return JNI_FALSE;
    bool ok=engine && engine->allow(id,address);
    env->ReleaseStringUTFChars(ip,address); return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL Java_com_karaokelink_core_audio_NativeAudio_removePeer(JNIEnv*,jobject,jint id) { if(engine) engine->remove(id); }
JNIEXPORT void JNICALL Java_com_karaokelink_core_audio_NativeAudio_stop(JNIEnv*,jobject) { engine.reset(); }
JNIEXPORT jfloatArray JNICALL Java_com_karaokelink_core_audio_NativeAudio_metrics(JNIEnv* env,jobject) {
    const auto values=engine ? engine->metrics() : std::array<float,4>{};
    auto out=env->NewFloatArray(4); if(out) env->SetFloatArrayRegion(out,0,4,values.data()); return out;
}
JNIEXPORT void JNICALL Java_com_karaokelink_core_audio_NativeAudio_setEffects(JNIEnv*,jobject,
    jfloat gain,jfloat gate,jfloat echo,jfloat feedback,jfloat echoMix,jfloat reverb,jfloat low,jfloat mid,jfloat high) {
    savedEffects.gain.store(std::clamp(gain,0.f,4.f)); savedEffects.gate.store(std::clamp(gate,0.f,0.2f));
    savedEffects.echoMs.store(std::clamp(echo,5.f,700.f)); savedEffects.feedback.store(std::clamp(feedback,0.f,0.85f));
    savedEffects.echoMix.store(std::clamp(echoMix,0.f,1.f)); savedEffects.reverb.store(std::clamp(reverb,0.f,1.f));
    savedEffects.low.store(std::clamp(low,0.f,2.f)); savedEffects.mid.store(std::clamp(mid,0.f,2.f)); savedEffects.high.store(std::clamp(high,0.f,2.f));
    if(engine) copyEffects(engine->controls(),savedEffects);
}
JNIEXPORT void JNICALL Java_com_karaokelink_core_audio_NativeAudio_setHostMix(JNIEnv*,jobject,jfloat gain,jint delay) {
    savedGain=gain; savedDelay=delay; if(engine) engine->mix(gain,delay);
}
}