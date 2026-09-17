package com.moyue.content.tts;

/**
 * TTS 服务不可用异常：缺密钥 / 厂商接口异常 / 返回空音频。
 * 由 {@link TtsService} 捕获并转换为 {@code SERVICE_DEGRADED}，保证阅读主流程不阻断。
 */
public class TtsUnavailableException extends RuntimeException {

    public TtsUnavailableException(String message) {
        super(message);
    }

    public TtsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
