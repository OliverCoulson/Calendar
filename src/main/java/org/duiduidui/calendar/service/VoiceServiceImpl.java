package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.VoiceException;
import org.duiduidui.calendar.voice.AudioRecorder;
import org.duiduidui.calendar.voice.TtsPlayer;
import org.duiduidui.calendar.voice.aliyun.AliyunASRClient;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 语音服务实现 —— 阿里云 ASR + TTS。
 * 编排音频录制、云端识别、语音播放的完整流程。
 */
public class VoiceServiceImpl implements VoiceService {

    private final List<RecordingListener> recordingListeners = new CopyOnWriteArrayList<>();
    private final List<RecognitionListener> recognitionListeners = new CopyOnWriteArrayList<>();
    private final AliyunASRClient asrClient;

    private static final long MAX_RECORD_MS = 10_000;
    private static final long SILENCE_THRESHOLD_MS = 1_500;

    public VoiceServiceImpl(String appKey, String accessKeyId, String accessKeySecret) {
        this.asrClient = new AliyunASRClient(appKey, accessKeyId, accessKeySecret);
    }

    public VoiceServiceImpl(String appKey, String accessKeyId, String accessKeySecret, String gateway) {
        this.asrClient = new AliyunASRClient(appKey, accessKeyId, accessKeySecret, gateway);
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
        TtsPlayer.play(text);
    }

    @Override
    public void stopSpeaking() {
        TtsPlayer.stop();
    }

    @Override
    public boolean isListening() {
        return false; // AudioRecorder 当前为同步阻塞模式
    }

    @Override
    public boolean isSpeaking() {
        return TtsPlayer.isPlaying();
    }

    @Override
    public void addRecordingListener(RecordingListener listener) {
        recordingListeners.add(listener);
    }

    @Override
    public void addRecognitionListener(RecognitionListener listener) {
        recognitionListeners.add(listener);
    }
}
