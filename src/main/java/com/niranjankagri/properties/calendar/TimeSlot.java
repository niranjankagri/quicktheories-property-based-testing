package com.niranjankagri.properties.calendar;

/**
 * A slot within one day, in minutes after midnight, half open: {@code [start, end)}. 09:00-10:00 is
 * {@code new TimeSlot(540, 600)} and does not overlap 10:00-11:00.
 *
 * @param start first minute, 0 to 1439
 * @param end   minute after the last one, start + 1 to 1440
 */
public record TimeSlot(int start, int end) {

    public static final int MINUTES_PER_DAY = 1_440;

    public TimeSlot {
        if (start < 0 || end > MINUTES_PER_DAY || start >= end) {
            throw new IllegalArgumentException("Not a slot within one day: [" + start + ", " + end + ")");
        }
    }

    public int minutes() {
        return end - start;
    }

    /** True when the slots share at least one minute. Touching slots (10:00-11:00 and 11:00-12:00) do not overlap. */
    public boolean overlaps(TimeSlot other) {
        return start < other.end && other.start < end;
    }

    public boolean contains(int minute) {
        return minute >= start && minute < end;
    }

    @Override
    public String toString() {
        return "%02d:%02d-%02d:%02d".formatted(start / 60, start % 60, end / 60, end % 60);
    }
}
