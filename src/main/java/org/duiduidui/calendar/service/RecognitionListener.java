package org.duiduidui.calendar.service;

import org.duiduidui.calendar.model.VoiceException;

public interface RecognitionListener {
    void onSuccess(String text);
    void onError(VoiceException e);
}
