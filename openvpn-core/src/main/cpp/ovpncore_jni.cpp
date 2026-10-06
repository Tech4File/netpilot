// NetPilot embedded OpenVPN engine — JNI bridge (v2.3.0).
//
// Wraps the official OpenVPN 3 C++ core (client/ovpncli.hpp API) for
// NetPilot's VpnDataChannel seam:
//   - NetPilotVpnService opens and configures the TUN itself, then hands the
//     already-established fd to open(); the wrapper returns that fd from
//     tun_builder_establish(), the injection point the core exposes for
//     Android clients (same approach as the official OpenVPN for Android).
//   - socket_protect() routes to Kotlin so VpnService.protect() keeps the
//     core's sockets outside the tunnel (routing-loop safety).
//   - Events and logs stream back over JNI; CONNECTED latches "session up".
//
// Upstream: https://github.com/openvpn/openvpn3 (AGPL-3.0) — sources are
// vendored at build time by scripts/build-ovpn-native.sh; binaries are
// never committed to the NetPilot repository.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <mutex>
#include <string>
#include <thread>

#include <client/ovpncli.hpp>

#define TAG "NetPilotOvpn"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

using namespace openvpn;

namespace {

// Kotlin side: app.netpilot.openvpn.core.OvpnCoreEngine.Callbacks; native
// methods live in the top-level object app.netpilot.openvpn.core.OvpnNative.
jmethodID g_onEvent = nullptr;
jmethodID g_onLog = nullptr;
jmethodID g_onProtect = nullptr;

/// One wrapper instance per active session (guarded by g_client_mutex).
class NetPilotClient final : public ClientAPI::OpenVPNClient
{
  public:
    NetPilotClient(JavaVM *vm, jobject callbacks, jint tunFd)
        : vm_(vm), callbacks_(callbacks), tun_fd_(tunFd) {}

    jobject callbacksRef() const { return callbacks_; }

    // ---- OpenVPNClient callbacks -----------------------------------------
    void event(const ClientAPI::Event &e) override
    {
        ALOGI("event: %s (%s) fatal=%d", e.name.c_str(), e.info.c_str(),
              (int)e.fatal);
        attach([&](JNIEnv *env) {
            jstring name = env->NewStringUTF(e.name.c_str());
            jstring info = env->NewStringUTF(e.info.c_str());
            env->CallVoidMethod(callbacks_, g_onEvent, name, info,
                                (jboolean)(e.fatal ? JNI_TRUE : JNI_FALSE));
            env->DeleteLocalRef(name);
            env->DeleteLocalRef(info);
        });
        if (e.name == "CONNECTED")
        {
            connected_.store(true);
            up_latch_.notify();
        }
    }

    void log(const ClientAPI::LogInfo &l) override
    {
        attach([&](JNIEnv *env) {
            jstring line = env->NewStringUTF(l.text.c_str());
            if (line != nullptr)
            {
                env->CallVoidMethod(callbacks_, g_onLog, line);
                env->DeleteLocalRef(line);
            }
        });
    }

    // App-custom control-channel protocol — NetPilot negotiates none.
    void acc_event(const ClientAPI::AppCustomControlMessageEvent &e) override
    {
        (void)e;
    }

    // External PKI (keystore-backed keys) — not enabled in this build; the
    // base class requires the pair to exist, so fail any request honestly.
    void external_pki_cert_request(ClientAPI::ExternalPKICertRequest &req) override
    {
        req.error = true;
        req.errorText = "external PKI not supported by this NetPilot build";
    }

    void external_pki_sign_request(ClientAPI::ExternalPKISignRequest &req) override
    {
        req.error = true;
        req.errorText = "external PKI not supported by this NetPilot build";
    }

    bool pause_on_connection_timeout() override { return false; }

    // Routing-loop safety: the core's own sockets must bypass the tunnel.
    bool socket_protect(openvpn_io::detail::socket_type socket,
                        std::string remote, bool ipv6) override
    {
        (void)remote;
        (void)ipv6;
        bool ok = false;
        attach([&](JNIEnv *env) {
            jboolean r = env->CallBooleanMethod(callbacks_, g_onProtect,
                                                (jint)socket);
            ok = (r == JNI_TRUE);
        });
        if (!ok)
            ALOGW("socket_protect failed (fd=%d)", (int)socket);
        return ok;
    }

    // ---- TUN: already established and configured by NetPilotVpnService ---
    // Everything below acknowledges the service-side configuration; the
    // ONLY real decision is tun_builder_establish() returning our fd.
    bool tun_builder_new() override { return true; }
    bool tun_builder_set_layer(int layer) override { return layer == 3; }
    bool tun_builder_reroute_gw(bool ipv4, bool ipv6, unsigned int flags) override
    {
        (void)ipv4; (void)ipv6; (void)flags;
        return true;
    }
    bool tun_builder_add_address(const std::string &address, int prefix_length,
                                 const std::string &gateway, bool ipv6,
                                 bool net30) override
    {
        (void)address; (void)prefix_length; (void)gateway; (void)ipv6; (void)net30;
        return true;
    }
    bool tun_builder_add_route(const std::string &address, int prefix_length,
                               int metric, bool ipv6) override
    {
        (void)address; (void)prefix_length; (void)metric; (void)ipv6;
        return true;
    }
    bool tun_builder_exclude_route(const std::string &address, int prefix_length,
                                   int metric, bool ipv6) override
    {
        (void)address; (void)prefix_length; (void)metric; (void)ipv6;
        return true;
    }
    bool tun_builder_set_mtu(int mtu) override { (void)mtu; return true; }
    bool tun_builder_set_session_name(const std::string &name) override
    {
        (void)name;
        return true;
    }
    bool tun_builder_set_remote_address(const std::string &host, bool ipv6) override
    {
        (void)host; (void)ipv6;
        return true;
    }
    bool tun_builder_set_proxy_http(const std::string &host, int port) override
    {
        (void)host; (void)port;
        return false;
    }
    bool tun_builder_set_proxy_https(const std::string &host, int port) override
    {
        (void)host; (void)port;
        return false;
    }
    bool tun_builder_set_proxy_auto_config_url(const std::string &url) override
    {
        (void)url;
        return false;
    }
    bool tun_builder_add_proxy_bypass(const std::string &bypass_host) override
    {
        (void)bypass_host;
        return false;
    }
    bool tun_builder_add_wins_server(const std::string &address) override
    {
        (void)address;
        return false;
    }
    bool tun_builder_set_route_metric_default(int metric) override
    {
        (void)metric;
        return true;
    }
    bool tun_builder_set_allow_family(int af, bool allow) override
    {
        (void)af; (void)allow;
        return true;
    }
    bool tun_builder_set_allow_local_dns(bool allow) override
    {
        (void)allow;
        return true;
    }
    bool tun_builder_set_dns_options(const DnsOptions &dns) override
    {
        (void)dns;
        return true;
    }
    bool tun_builder_persist() override { return false; }

    /// The injection point: hand the core the fd the service already owns.
    int tun_builder_establish() override { return tun_fd_; }


    // ---- CONNECTED latch ---------------------------------------------------
    bool wait_connected(unsigned int seconds) { return up_latch_.wait_for(seconds); }

    /// Public: the connect-worker thread signals completion (also on error).
    void notify_done() { up_latch_.notify(); }

  private:
    template <typename F> void attach(F &&body)
    {
        JNIEnv *env = nullptr;
        bool attachedHere = false;
        if (vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
        {
            if (vm_->AttachCurrentThread(&env, nullptr) != JNI_OK)
                return;
            attachedHere = true;
        }
        body(env);
        if (attachedHere)
            vm_->DetachCurrentThread();
    }

    JavaVM *vm_;
    jobject callbacks_;
    jint tun_fd_;
    std::atomic<bool> connected_{false};

    struct Latch
    {
        std::mutex m;
        std::condition_variable cv;
        bool done = false;
        void notify()
        {
            std::lock_guard<std::mutex> lk(m);
            done = true;
            cv.notify_all();
        }
        bool wait_for(unsigned int seconds)
        {
            std::unique_lock<std::mutex> lk(m);
            cv.wait_for(lk, std::chrono::seconds(seconds),
                        [&] { return done; });
            return done;
        }
    } up_latch_;
};

JavaVM *g_vm = nullptr;
std::mutex g_client_mutex;
NetPilotClient *g_client = nullptr;
std::thread *g_worker = nullptr;

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *)
{
    g_vm = vm;
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;
    jclass cb = env->FindClass(
        "app/netpilot/openvpn/core/OvpnCoreEngine$Callbacks");
    if (cb == nullptr)
        return JNI_ERR;
    g_onEvent = env->GetMethodID(cb, "onEvent",
                                 "(Ljava/lang/String;Ljava/lang/String;Z)V");
    g_onLog = env->GetMethodID(cb, "onLog", "(Ljava/lang/String;)V");
    g_onProtect = env->GetMethodID(cb, "onProtect", "(I)Z");
    env->DeleteLocalRef(cb);
    if (g_onEvent == nullptr || g_onLog == nullptr || g_onProtect == nullptr)
        return JNI_ERR;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_netpilot_openvpn_core_OvpnNative_nativeStart(JNIEnv *env, jobject,
                                                       jobject callbacks,
                                                       jstring config,
                                                       jint tunFd)
{
    std::lock_guard<std::mutex> lk(g_client_mutex);
    if (g_client != nullptr)
    {
        ALOGW("nativeStart: session already running");
        return JNI_FALSE;
    }

    const char *cfg = env->GetStringUTFChars(config, nullptr);
    if (cfg == nullptr)
        return JNI_FALSE;

    auto *client = new NetPilotClient(g_vm, env->NewGlobalRef(callbacks), tunFd);

    ClientAPI::Config opts;
    opts.content = cfg;
    // No compression; the core then only negotiates compression stubs, the
    // safe default for modern servers (mirrors NetPilot's parser stance).
    opts.compressionMode = "no";
    opts.guiVersion = "NetPilot";

    ClientAPI::EvalConfig ec = client->eval_config(opts);
    env->ReleaseStringUTFChars(config, cfg);
    if (ec.error)
    {
        ALOGW("eval_config failed: %s", ec.message.c_str());
        env->DeleteGlobalRef(client->callbacksRef());
        delete client;
        return JNI_FALSE;
    }

    g_client = client;
    g_worker = new std::thread([client] {
        ClientAPI::Status st = client->connect();
        ALOGI("connect() returned: error=%d status=%s message=%s",
              (int)st.error, st.status.c_str(), st.message.c_str());
        client->notify_done();
    });
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_netpilot_openvpn_core_OvpnNative_nativeWaitConnected(JNIEnv *,
                                                               jobject,
                                                               jint seconds)
{
    std::lock_guard<std::mutex> lk(g_client_mutex);
    if (g_client == nullptr)
        return JNI_FALSE;
    return g_client->wait_connected((unsigned int)seconds) ? JNI_TRUE
                                                           : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_netpilot_openvpn_core_OvpnNative_nativeIsRunning(JNIEnv *, jobject)
{
    std::lock_guard<std::mutex> lk(g_client_mutex);
    return g_client != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_app_netpilot_openvpn_core_OvpnNative_nativeStop(JNIEnv *env, jobject)
{
    NetPilotClient *client = nullptr;
    std::thread *worker = nullptr;
    {
        std::lock_guard<std::mutex> lk(g_client_mutex);
        client = g_client;
        worker = g_worker;
        g_client = nullptr;
        g_worker = nullptr;
    }
    if (client != nullptr)
    {
        client->stop();
        if (worker != nullptr)
        {
            if (worker->joinable())
                worker->join();
            delete worker;
        }
        env->DeleteGlobalRef(client->callbacksRef());
        delete client;
    }
}
