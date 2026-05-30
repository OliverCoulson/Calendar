package org.duiduidui.calendar.voice;

import org.duiduidui.calendar.voice.aliyun.AliyunTTSClient;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TTS 播放器。
 * 使用阿里云语音合成 API 将文本转为 PCM 音频并播放。
 */
public class TtsPlayer {

    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AliyunTTSClient ttsClient;
    private Thread playThread;
    private SourceDataLine line;

    public TtsPlayer(AliyunTTSClient ttsClient) {
        this.ttsClient = ttsClient;
    }

    /** 播放文本（异步）。 */
    public void play(String text) {
        if (text == null || text.isEmpty()) return;
        stop();

        playing.set(true);
        playThread = new Thread(() -> {
            try {
                // 1. 调用阿里云 TTS API 合成音频
                byte[] pcmData = ttsClient.synthesize(text);

                // 2. 播放 PCM 音频
                AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();

                ByteArrayInputStream bis = new ByteArrayInputStream(pcmData);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while (playing.get() && (bytesRead = bis.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                System.err.println("[TTS] API 合成失败: " + e.getMessage());
                // 降级：控制台打印
                System.out.println("[TTS] " + text);
            } catch (LineUnavailableException e) {
                System.err.println("[TTS] 音频播放失败: " + e.getMessage());
                System.out.println("[TTS] " + text);
            } finally {
                closeLine();
                playing.set(false);
            }
        }, "tts-playback");
        playThread.setDaemon(true);
        playThread.start();
    }

    /** 停止播放。 */
    public void stop() {
        playing.set(false);
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
        closeLine();
    }

    public boolean isPlaying() {
        return playing.get();
    }

    private void closeLine() {
        if (line != null) {
            if (line.isRunning()) line.stop();
            if (line.isOpen()) line.close();
            line = null;
        }
    }
}
