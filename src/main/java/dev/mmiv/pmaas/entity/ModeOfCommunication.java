package dev.mmiv.pmaas.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumerated modes of communication between clinic staff and a patient/guardian.
 * Stored as a VARCHAR in PostgreSQL via @Enumerated(EnumType.STRING).
 *
 * displayLabel is the human-readable form used in Excel export headers
 * and is the value accepted by the import parser (case-insensitive).
 */
public enum ModeOfCommunication {
    PHONE("Phone"),
    SMS("SMS"),
    EMAIL("Email"),
    IN_PERSON("In Person"),
    ONLINE("Online"),
    FAX("Fax"),
    WALK_IN("Walk-in");

    private final String displayLabel;

    ModeOfCommunication(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    @JsonValue
    public String getDisplayLabel() {
        return displayLabel;
    }

    /**
     * Parses either the enum name ("PHONE") or the display label ("Phone")
     * so that both JSON payloads and Excel cell values are accepted.
     */
    @JsonCreator
    public static ModeOfCommunication fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ModeOfCommunication mode : values()) {
            if (
                mode.name().equalsIgnoreCase(value.trim()) ||
                mode.displayLabel.equalsIgnoreCase(value.trim())
            ) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
            "Unknown ModeOfCommunication: '" +
                value +
                "'. " +
                "Accepted values: PHONE, SMS, EMAIL, IN_PERSON, ONLINE, FAX, WALK_IN"
        );
    }
}
