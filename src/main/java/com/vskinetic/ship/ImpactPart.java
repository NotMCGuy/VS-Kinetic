package com.vskinetic.ship;

import java.util.Locale;

public enum ImpactPart {
    AUTO,
    HULL,
    ENGINE,
    LIFT,
    CONTROL;

    public static ImpactPart parseOrAuto(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return ImpactPart.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }
}
