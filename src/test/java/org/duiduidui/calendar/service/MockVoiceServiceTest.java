package org.duiduidui.calendar.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MockVoiceServiceTest {

    private final MockVoiceService service = new MockVoiceService();

    @Test
    void testStartListeningReturnsPresetText() throws Exception {
        String result = service.startListening();
        assertEquals("添加明天下午三点的会议", result);
    }

    @Test
    void testSpeakDoesNotThrow() {
        assertDoesNotThrow(() -> service.speak("测试语音"));
    }

    @Test
    void testStopSpeakingDoesNotThrow() {
        assertDoesNotThrow(service::stopSpeaking);
    }

    @Test
    void testInitialStates() {
        assertFalse(service.isListening());
        assertFalse(service.isSpeaking());
    }

    @Test
    void testListeners() {
        boolean[] startCalled = {false};
        service.addRecordingListener(new RecordingListener() {
            @Override public void onRecordingStart() { startCalled[0] = true; }
            @Override public void onRecordingEnd() {}
            @Override public void onRecordingCancel() {}
            @Override public void onError(String message) {}
        });

        service.startListening();
        assertTrue(startCalled[0]);
    }
}
