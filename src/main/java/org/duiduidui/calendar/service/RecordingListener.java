package org.duiduidui.calendar.service;

public interface RecordingListener {
    void onRecordingStart();
    void onRecordingEnd();
    void onRecordingCancel();
    void onError(String message);
}
