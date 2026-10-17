package com.sunrisedental.model;

import java.time.LocalTime;

/**
 * An existing booking of a dentist on a given day, used for overlap detection.
 *
 * @param start           start time
 * @param durationMinutes length of the booked treatment
 */
public record BookedSlot(LocalTime start, int durationMinutes) {

    /** @return the end time (exclusive) */
    public LocalTime end() {
        return start.plusMinutes(durationMinutes);
    }

    /**
     * Tells whether another interval overlaps this one. Back-to-back intervals do not overlap.
     *
     * @param otherStart           start of the other interval
     * @param otherDurationMinutes length of the other interval
     * @return {@code true} if the two intervals share any time
     */
    public boolean overlaps(LocalTime otherStart, int otherDurationMinutes) {
        int thisStart = minutes(start);
        int thisEnd = thisStart + durationMinutes;
        int otherStartMin = minutes(otherStart);
        int otherEnd = otherStartMin + otherDurationMinutes;
        return thisStart < otherEnd && otherStartMin < thisEnd;
    }

    private static int minutes(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }
}
