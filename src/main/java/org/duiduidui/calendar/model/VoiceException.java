package org.duiduidui.calendar.model;

public class VoiceException extends Exception {

    private final VoiceErrorType type;

    public VoiceException(VoiceErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public VoiceException(VoiceErrorType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public VoiceErrorType getType() {
        return type;
    }
}
