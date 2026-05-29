package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.VoiceException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模拟语音服务 —— 无需 API Key，返回预设文本用于前期开发测试。
 */
public class MockVoiceService implements VoiceService {

    private final List<RecordingListener> recordingListeners = new CopyOnWriteArrayList<>();
    private final List<RecognitionListener> recognitionListeners = new CopyOnWriteArrayList<>();

    @Override
    public String startListening() {
        recordingListeners.forEach(RecordingListener::onRecordingStart);
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        recordingListeners.forEach(RecordingListener::onRecordingEnd);

        String mockText = "添加明天下午三点的会议";
        recognitionListeners.forEach(l -> l.onSuccess(mockText));
        return mockText;
    }

    @Override
    public void cancelListening() {
        recordingListeners.forEach(RecordingListener::onRecordingCancel);
    }

    @Override
    public void speak(String text) {
        System.out.println("[Mock TTS] " + text);
    }

    @Override
    public void stopSpeaking() {}

    @Override
    public boolean isListening() { return false; }

    @Override
    public boolean isSpeaking() { return false; }

    @Override
    public void addRecordingListener(RecordingListener listener) {
        recordingListeners.add(listener);
    }

    @Override
    public void addRecognitionListener(RecognitionListener listener) {
        recognitionListeners.add(listener);
    }
}
