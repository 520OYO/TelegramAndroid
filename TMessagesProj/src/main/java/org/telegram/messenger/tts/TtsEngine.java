package org.telegram.messenger.tts;

/**
 * 朗读引擎统一接口。
 * 引擎 1：系统本地 TTS（SystemTtsEngine），不联网、不需要任何密钥。
 * 引擎 2+：云端 TTS，依赖「模型配置中心」，后续接入时实现本接口即可。
 */
public interface TtsEngine {

    /** 引擎标识，用于持久化选择 */
    String getId();

    /** 开始朗读，会先停止上一次朗读 */
    void speak(String text, Callback callback);

    /** 停止朗读 */
    void stop();

    /** 释放资源，释放后可重新使用（会重新初始化） */
    void release();

    /** 当前是否正在朗读 */
    boolean isSpeaking();

    interface Callback {

        void onStart();

        void onDone();

        /** 失败原因：no_engine / no_language / speak_failed / exception */
        void onError(String reason);
    }
}
