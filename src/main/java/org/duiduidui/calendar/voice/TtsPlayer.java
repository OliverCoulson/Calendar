package org.duiduidui.calendar.voice;

/**
 * TTS 播放器。
 * 当前为 Phase 1 模拟实现，仅打印日志。
 * Phase 2 将集成阿里云语音合成 REST API 并播放 PCM 音频。
 */
public class TtsPlayer {

    private static volatile boolean playing = false;

    /** 播放文本（当前为模拟，仅输出日志）。 */
    public static void play(String text) {
        if (text == null || text.isEmpty()) return;
        stop();
        playing = true;
        System.out.println("[TTS] " + text);
        playing = false;
    }

    /** 停止播放。 */
    public static void stop() {
        playing = false;
    }

    public static boolean isPlaying() {
        return playing;
    }
}
