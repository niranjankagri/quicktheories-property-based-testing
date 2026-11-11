package com.niranjankagri.properties.calendar;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

import static com.niranjankagri.properties.generators.CalendarGen.bookings;
import static com.niranjankagri.properties.generators.CalendarGen.openingHours;
import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;

/**
 * Properties of {@link BookingCalendar}, checked against an oracle: a {@link BitSet} with one bit per busy minute.
 * The oracle is too slow and too plain for production, and that is the point: it is obviously right, so the clever
 * merge code is checked against something a reader can trust at a glance.
 */
public class BookingCalendarPropertiesTest {

    private final BookingCalendar calendar = new BookingCalendar();

    @Test(description = "Example: 09:00-10:00, 09:30-11:00 and 11:00-12:00 are one busy slot 09:00-12:00")
    public void exampleOverlappingAndTouchingSlots() {
        List<TimeSlot> busy = calendar.busy(List.of(new TimeSlot(540, 600), new TimeSlot(570, 660), new TimeSlot(660, 720)));

        assertThat(busy).containsExactly(new TimeSlot(540, 720));
    }

    @Test(description = "Busy time covers exactly the booked minutes: none lost, none added")
    public void busyCoversExactlyTheBookedMinutes() {
        qt().forAll(bookings()).checkAssert(bookings ->
                assertThat(minutes(calendar.busy(bookings))).isEqualTo(minutes(bookings)));
    }

    @Test(description = "Busy slots are sorted, and neither overlap nor touch (touching ones are merged)")
    public void busySlotsAreSortedAndSeparated() {
        qt().forAll(bookings()).checkAssert(bookings -> {
            List<TimeSlot> busy = calendar.busy(bookings);
            for (int i = 1; i < busy.size(); i++) {
                assertThat(busy.get(i).start()).as("gap before %s", busy.get(i)).isGreaterThan(busy.get(i - 1).end());
            }
        });
    }

    @Test(description = "Merging twice gives the same as merging once")
    public void mergingIsIdempotent() {
        qt().forAll(bookings()).checkAssert(bookings -> {
            List<TimeSlot> once = calendar.busy(bookings);
            assertThat(calendar.busy(once)).isEqualTo(once);
        });
    }

    @Test(description = "The order of the bookings does not matter")
    public void orderDoesNotMatter() {
        qt().forAll(bookings()).checkAssert(bookings -> {
            List<TimeSlot> reversed = new ArrayList<>(bookings.reversed());
            assertThat(calendar.busy(reversed)).isEqualTo(calendar.busy(bookings));
        });
    }

    @Test(description = "Within opening hours every minute is either free or booked, never both, never neither")
    public void freeAndBookedSplitTheOpeningHours() {
        qt().forAll(bookings(), openingHours()).checkAssert((bookings, open) -> {
            BitSet free = minutes(calendar.free(bookings, open));
            BitSet booked = minutes(bookings);
            for (int minute = open.start(); minute < open.end(); minute++) {
                assertThat(free.get(minute)).as("minute %d free", minute).isNotEqualTo(booked.get(minute));
            }
        });
    }

    @Test(description = "Free slots lie within the opening hours, sorted and separated")
    public void freeSlotsStayInsideOpeningHours() {
        qt().forAll(bookings(), openingHours()).checkAssert((bookings, open) -> {
            List<TimeSlot> free = calendar.free(bookings, open);
            for (int i = 0; i < free.size(); i++) {
                assertThat(free.get(i).start()).isGreaterThanOrEqualTo(open.start());
                assertThat(free.get(i).end()).isLessThanOrEqualTo(open.end());
                if (i > 0) {
                    assertThat(free.get(i).start()).isGreaterThan(free.get(i - 1).end());
                }
            }
        });
    }

    /** The oracle: one bit per minute that any of the slots covers. */
    private static BitSet minutes(Collection<TimeSlot> slots) {
        BitSet minutes = new BitSet(TimeSlot.MINUTES_PER_DAY);
        slots.forEach(slot -> minutes.set(slot.start(), slot.end()));
        return minutes;
    }
}
