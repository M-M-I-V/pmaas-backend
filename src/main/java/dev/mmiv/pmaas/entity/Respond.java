package dev.mmiv.pmaas.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Outcome of a contact attempt.
 *
 * Using an enum instead of free text prevents the inconsistency that
 * accumulates in a manual logbook ("yes", "Yes", "YES", "responded", etc.)
 * and makes filtering reliable.
 */
public enum Respond {
    RESPONDED("Responded"),
    NO_RESPONSE("No Response"),
    LEFT_MESSAGE("Left Message"),
    CALLBACK_REQUESTED("Callback Requested"),
    PENDING("Pending");

    private final String displayLabel;

    Respond(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    @JsonValue
    public String getDisplayLabel() {
        return displayLabel;
    }

    @JsonCreator
    public static Respond fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Respond r : values()) {
            if (
                r.name().equalsIgnoreCase(value.trim()) ||
                r.displayLabel.equalsIgnoreCase(value.trim())
            ) {
                return r;
            }
        }
        throw new IllegalArgumentException(
            "Unknown Respond value: '" +
                value +
                "'. " +
                "Accepted values: RESPONDED, NO_RESPONSE, LEFT_MESSAGE, " +
                "CALLBACK_REQUESTED, PENDING"
        );
    }
}
