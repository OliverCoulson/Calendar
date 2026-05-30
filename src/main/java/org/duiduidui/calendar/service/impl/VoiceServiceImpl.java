package org.duiduidui.calendar.service.impl;

import org.duiduidui.calendar.model.VoiceException;
import org.duiduidui.calendar.service.RecognitionListener;
import org.duiduidui.calendar.service.RecordingListener;
import org.duiduidui.calendar.service.VoiceService;
import org.duiduidui.calendar.voice.AudioRecorder;
import org.duiduidui.calendar.voice.TtsPlayer;
import org.duiduidui.calendar.voice.aliyun.AliyunASRClient;
import org.duiduidui.calendar.voice.aliyun.AliyunTTSClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class VoiceServiceImpl implements VoiceService {

    private final List<RecordingListener> recordingListeners = new CopyOnWriteArrayList<>();
    private final List<RecognitionListener> recognitionListeners = new CopyOnWriteArrayList<>();
    private final AliyunASRClient asrClient;
    private final TtsPlayer ttsPlayer;

    private static final long MAX_RECORD_MS = 10_000;
    private static final long SILENCE_THRESHOLD_MS = 1_500;

    public VoiceServiceImpl(String appKey, String accessKeyId, String accessKeySecret) {
        this.asrClient = new AliyunASRClient(appKey, accessKeyId, accessKeySecret);
        this.ttsPlayer = new TtsPlayer(new AliyunTTSClient(appKey, accessKeyId, accessKeySecret));
    }

    public VoiceServiceImpl(String appKey, String accessKeyId, String accessKeySecret, String gateway) {
        this.asrClient = new AliyunASRClient(appKey, accessKeyId, accessKeySecret, gateway);
        this.ttsPlayer = new TtsPlayer(new AliyunTTSClient(appKey, accessKeyId, accessKeySecret, gateway));
    }

    @Override
    public String startListening() throws VoiceException {
        recordingListeners.forEach(RecordingListener::onRecordingStart);

        try {
            byte[] audioData = AudioRecorder.record(MAX_RECORD_MS, SILENCE_THRESHOLD_MS);
            recordingListeners.forEach(RecordingListener::onRecordingEnd);

            if (audioData == null || audioData.length == 0) {
                VoiceException e = new VoiceException(
                        org.duiduidui.calendar.model.VoiceErrorType.NO_SPEECH, "未检测到语音输入");
                recognitionListeners.forEach(l -> l.onError(e));
                throw e;
            }

            String result = asrClient.recognize(audioData);
            recognitionListeners.forEach(l -> l.onSuccess(result));
            return result;

        } catch (VoiceException e) {
            recognitionListeners.forEach(l -> l.onError(e));
            throw e;
        } catch (Exception e) {
            VoiceException ve = new VoiceException(
                    org.duiduidui.calendar.model.VoiceErrorType.UNKNOWN, "语音识别异常: " + e.getMessage(), e);
            recognitionListeners.forEach(l -> l.onError(ve));
            throw ve;
        }
    }

    @Override
    public void cancelListening() {
        AudioRecorder.cancel();
        recordingListeners.forEach(RecordingListener::onRecordingCancel);
    }

    @Override
    public void speak(String text) {
        ttsPlayer.play(text);
    }

    @Override
    public void stopSpeaking() {
        ttsPlayer.stop();
    }

    @Override
    public boolean isListening() { return false; }

    @Override
    public boolean isSpeaking() { return ttsPlayer.isPlaying(); }

    @Override
    public void addRecordingListener(RecordingListener listener) {
        recordingListeners.add(listener);
    }

    @Override
    public void addRecognitionListener(RecognitionListener listener) {
        recognitionListeners.add(listener);
    }
}
