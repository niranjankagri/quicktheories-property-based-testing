package com.niranjankagri.properties.calendar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Busy and free time of a meeting room for one day.
 *
 * <p>Interval code is where example tests run out: overlaps, touching slots, slots inside other slots, unsorted
 * input, the first and last minute of the day. The properties check it against a deliberately simple oracle
 * (a set of busy minutes) for every generated list of bookings.
 */
public final class BookingCalendar {

    /**
     * Merges bookings into the smallest list of busy slots: sorted, no overlaps, and touching slots joined
     * (09:00-10:00 and 10:00-11:00 become 09:00-11:00).
     */
    public List<TimeSlot> busy(Collection<TimeSlot> bookings) {
        List<TimeSlot> sorted = new ArrayList<>(bookings);
        sorted.sort(Comparator.comparingInt(TimeSlot::start));
        List<TimeSlot> merged = new ArrayList<>();
        for (TimeSlot slot : sorted) {
            if (!merged.isEmpty() && slot.start() <= merged.getLast().end()) {
                TimeSlot last = merged.removeLast();
                merged.add(new TimeSlot(last.start(), Math.max(last.end(), slot.end())));
            } else {
                merged.add(slot);
            }
        }
        return merged;
    }

    /**
     * Free slots within the opening hours: the gaps between busy slots, cut to the opening hours.
     *
     * @param bookings     existing bookings, any order, may overlap or lie outside the opening hours
     * @param openingHours when the room can be booked
     * @return free slots, sorted
     */
    public List<TimeSlot> free(Collection<TimeSlot> bookings, TimeSlot openingHours) {
        List<TimeSlot> free = new ArrayList<>();
        int cursor = openingHours.start();
        for (TimeSlot busy : busy(bookings)) {
            if (busy.end() <= cursor) {
                continue;
            }
            if (busy.start() >= openingHours.end()) {
                break;
            }
            if (busy.start() > cursor) {
                free.add(new TimeSlot(cursor, busy.start()));
            }
            cursor = Math.max(cursor, busy.end());
        }
        if (cursor < openingHours.end()) {
            free.add(new TimeSlot(cursor, openingHours.end()));
        }
        return free;
    }
}
