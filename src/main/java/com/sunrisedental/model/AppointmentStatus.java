package com.sunrisedental.model;

import java.util.EnumSet;
import java.util.Set;

/** Appointment lifecycle with the allowed transitions; matches the {@code appointments.status} CHECK. */
public enum AppointmentStatus {
    SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW;

    /**
     * Tells whether an appointment in this status still blocks the dentist's time.
     *
     * @return {@code false} for cancelled and no-show appointments
     */
    public boolean occupiesSlot() {
        return this != CANCELLED && this != NO_SHOW;
    }

    /**
     * Tells whether the status may change to {@code next}.
     * SCHEDULED may go anywhere, CONFIRMED may be completed, cancelled or missed, the rest are final.
     *
     * @param next the desired status
     * @return {@code true} if the transition is allowed
     */
    public boolean canTransitionTo(AppointmentStatus next) {
        return allowedNext().contains(next);
    }

    private Set<AppointmentStatus> allowedNext() {
        return switch (this) {
            case SCHEDULED -> EnumSet.of(CONFIRMED, COMPLETED, CANCELLED, NO_SHOW);
            case CONFIRMED -> EnumSet.of(COMPLETED, CANCELLED, NO_SHOW);
            default -> EnumSet.noneOf(AppointmentStatus.class);
        };
    }
}
