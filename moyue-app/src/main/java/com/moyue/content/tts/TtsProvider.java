package com.moyue.content.tts;

/**
 * TTS 合成抽象。具体实现可为云厂商（腾讯云/讯飞/微软）或本地引擎。
 * 约定：合成失败 / 未配置时抛 {@link TtsUnavailableException}，由上层降级为 {@code SERVICE_DEGRADED}。
 */
public interface TtsProvider {

    /**
     * 合成单段文本为音频字节。
     *
     * @param text  待合成文本（已分段，长度受 {@code moyue.tts.segment-max-chars} 约束）
     * @param voice 音色标识（可空，走厂商默认）
     * @param speed 语速（0.5~2.0，1.0 为原速）
     * @return 音频字节（如 mp3/wav）
     * @throws TtsUnavailableException 未配置密钥 / 调用失败 / 返回空
     */
    byte[] synthesize(String text, String voice, double speed) throws TtsUnavailableException;
}
