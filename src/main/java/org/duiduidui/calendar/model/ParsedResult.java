package org.duiduidui.calendar.model;

import java.util.Map;

public class ParsedResult {

    private IntentType intent;
    private Map<String, Object> entities;
    private double confidence;
    private String clarificationQuestion;

    public ParsedResult() {}

    public ParsedResult(IntentType intent, Map<String, Object> entities, double confidence) {
        this.intent = intent;
        this.entities = entities;
        this.confidence = confidence;
    }

    public boolean isConfident() {
        return confidence >= 0.6;
    }

    public boolean isAmbiguous() {
        return confidence < 0.6;
    }

    // ---- Getters & Setters ----

    public IntentType getIntent() { return intent; }
    public void setIntent(IntentType intent) { this.intent = intent; }

    public Map<String, Object> getEntities() { return entities; }
    public void setEntities(Map<String, Object> entities) { this.entities = entities; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getClarificationQuestion() { return clarificationQuestion; }
    public void setClarificationQuestion(String clarificationQuestion) { this.clarificationQuestion = clarificationQuestion; }
}
