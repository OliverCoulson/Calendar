package org.duiduidui.calendar.voice;

import org.duiduidui.calendar.model.VoiceErrorType;
import org.duiduidui.calendar.model.VoiceException;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 麦克风音频录制工具。
 * 使用 javax.sound.sampled.TargetDataLine 捕获 PCM 音频。
 */
public class AudioRecorder {

    private static final float SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);

    private static volatile TargetDataLine line;
    private static final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * 从麦克风录制 PCM 音频数据。
     *
     * @param maxDurationMs    最长录制时间
     * @param silenceThresholdMs 连续静音超过此时长则提前结束
     * @return PCM 音频字节数组，无人声时返回空数组
     */
    public static byte[] record(long maxDurationMs, long silenceThresholdMs) throws VoiceException {
        cancelled.set(false);

        try {
            line = AudioSystem.getTargetDataLine(FORMAT);
            line.open(FORMAT);
            line.start();
        } catch (LineUnavailableException e) {
            throw new VoiceException(VoiceErrorType.MIC_UNAVAILABLE, "麦克风不可用: " + e.getMessage(), e);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long startTime = System.currentTimeMillis();
        long lastSoundTime = startTime;
        double silenceThreshold = 0.01; // 静音能量阈值

        try {
            while (!cancelled.get()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= maxDurationMs) break;

                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead <= 0) continue;

                out.write(buffer, 0, bytesRead);

                // 简单能量检测 —— 判断 RMS 是否低于静音阈值
                double rms = calculateRMS(buffer, bytesRead);
                if (rms > silenceThreshold) {
                    lastSoundTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - lastSoundTime >= silenceThresholdMs) {
                    break;  // 静音超过阈值，提前结束
                }
            }
        } finally {
            stopLine();
        }

        if (cancelled.get()) {
            cancelled.set(false);
            return new byte[0];
        }

        return out.toByteArray();
    }

    /** 取消当前录音。 */
    public static void cancel() {
        cancelled.set(true);
        stopLine();
    }

    /** 计算音频块的能量（RMS）。 */
    private static double calculateRMS(byte[] buffer, int bytesRead) {
        double sum = 0;
        int samples = bytesRead / 2;  // 16bit = 2 bytes per sample
        for (int i = 0; i < bytesRead; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += sample * sample;
        }
        return Math.sqrt(sum / samples) / 32768.0;
    }

    private static void stopLine() {
        if (line != null) {
            if (line.isRunning()) line.stop();
            if (line.isOpen()) line.close();
            line = null;
        }
    }
}
