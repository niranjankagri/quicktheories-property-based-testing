package com.niranjankagri.properties.generators;

import com.niranjankagri.properties.calendar.TimeSlot;
import org.quicktheories.core.Gen;
import org.quicktheories.generators.Generate;

import java.util.List;

import static com.niranjankagri.properties.calendar.TimeSlot.MINUTES_PER_DAY;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.lists;

/**
 * Generators for slots and lists of bookings.
 *
 * <p>Slots spread over a whole day rarely overlap, and overlapping or touching slots are exactly what
 * {@code BookingCalendar} must handle. So half the slots are packed into one busy morning (09:00 to 12:00), and some
 * sit at the very start or end of the day.
 */
public final class CalendarGen {

    private static final int NINE = 9 * 60;
    private static final int NOON = 12 * 60;

    private CalendarGen() {
    }

    /** Any slot of up to four hours. Built from start + length (not two independent ends) so it shrinks cleanly. */
    public static Gen<TimeSlot> anySlot() {
        return integers().between(0, MINUTES_PER_DAY - 1)
                .zip(integers().between(1, 240), (start, length) -> slot(start, start + length));
    }

    /** Short slots in one busy morning: most lists of these overlap or touch somewhere. */
    public static Gen<TimeSlot> busyMorningSlot() {
        return integers().between(NINE, NOON - 1)
                .zip(integers().between(1, 60), (start, length) -> slot(start, Math.min(NOON, start + length)));
    }

    /** Slots at the edges of the day: 00:00 and 24:00 are where off-by-one errors in interval code show up. */
    public static Gen<TimeSlot> dayEdgeSlot() {
        return Generate.pick(List.of(slot(0, 1), slot(0, 60), slot(1_439, 1_440), slot(1_380, 1_440), slot(0, 1_440)));
    }

    /** Any of the three kinds above. */
    public static Gen<TimeSlot> slots() {
        return anySlot().mix(busyMorningSlot(), 50).mix(dayEdgeSlot(), 10);
    }

    /**
     * 0 to 12 short bookings in one busy morning. Built from a single generator (no {@code mix}), so QuickTheories
     * can shrink a failing list all the way down; see the README on shrinking and {@code mix}.
     */
    public static Gen<List<TimeSlot>> busyMorningBookings() {
        return lists().of(busyMorningSlot()).ofSizeBetween(0, 12);
    }

    /** 0 to 12 bookings, any order, overlaps allowed. */
    public static Gen<List<TimeSlot>> bookings() {
        return lists().of(slots()).ofSizeBetween(0, 12);
    }

    /** Opening hours: any slot within the day, the whole day included. */
    public static Gen<TimeSlot> openingHours() {
        return integers().between(0, MINUTES_PER_DAY - 1)
                .zip(integers().between(1, MINUTES_PER_DAY), (start, length) -> slot(start, start + length))
                .mix(Generate.pick(List.of(slot(NINE, 17 * 60), slot(0, MINUTES_PER_DAY))), 20);
    }

    private static TimeSlot slot(int start, int end) {
        return new TimeSlot(start, Math.min(MINUTES_PER_DAY, end));
    }
}
