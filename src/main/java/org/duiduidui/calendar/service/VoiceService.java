package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.VoiceException;

public interface VoiceService {

    /** 开始录音并识别，返回识别文本。最长 10 秒，静音 1.5 秒自动结束。 */
    String startListening() throws VoiceException;

    /** 取消当前录音。 */
    void cancelListening();

    /** 异步播放 TTS。 */
    void speak(String text);

    /** 停止 TTS 播放。 */
    void stopSpeaking();

    boolean isListening();
    boolean isSpeaking();

    void addRecordingListener(RecordingListener listener);
    void addRecognitionListener(RecognitionListener listener);
}
