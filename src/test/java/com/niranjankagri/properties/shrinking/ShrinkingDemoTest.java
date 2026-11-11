package com.niranjankagri.properties.shrinking;

import com.niranjankagri.properties.calendar.TimeSlot;
import com.niranjankagri.properties.generators.CalendarGen;
import org.quicktheories.QuickTheory;
import org.quicktheories.core.Gen;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.niranjankagri.properties.generators.MoneyGen.amountsInCents;
import static com.niranjankagri.properties.generators.MoneyGen.people;
import static com.niranjankagri.properties.generators.TextGen.runs;
import static com.niranjankagri.properties.generators.TextGen.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.lists;

/**
 * Shrinking, shown on purpose. Each test runs a property against a buggy <b>first draft</b> of a class from
 * {@code src/main}, expects it to fail, and checks the smallest failing example QuickTheories shrank it to.
 *
 * <p>The drafts live here, in test code, so the buggy versions never ship. The seed is fixed, so the generated
 * examples, and this report, are the same on every run. {@code target/shrinking-report.md} collects each property's
 * failure (CI puts it in the job summary).
 */
public class ShrinkingDemoTest {

    private static final long SEED = 20_261_111L;
    private static final Pattern FALSIFIED = Pattern.compile(
            "Property falsified after (\\d+) example\\(s\\)\\s*\\R+Smallest found falsifying value\\(s\\) :-\\R(.*?)\\R+Cause was",
            Pattern.DOTALL);

    private final List<String> report = new ArrayList<>();

    private static QuickTheory theory() {
        return QuickTheory.qt().withFixedSeed(SEED).withExamples(1_000);
    }

    // ---- 1. Bill splitter that forgets the remainder -----------------------------------------------------------

    /** First draft: everyone pays total / people, and the remainder disappears. */
    static List<Long> naiveSplit(long totalCents, int people) {
        List<Long> shares = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            shares.add(totalCents / people);
        }
        return shares;
    }

    @Test(description = "Bill splitter draft loses cents; shrunk to 1 cent between 2 people")
    public void billSplitterLosingTheRemainder() {
        AssertionError failure = catchThrowableOfType(AssertionError.class, () -> theory()
                .forAll(amountsInCents(), people())
                .checkAssert((total, people) -> assertThat(naiveSplit(total, people).stream().mapToLong(Long::longValue).sum())
                        .as("sum of shares").isEqualTo(total)));

        assertThat(smallest(failure, "Bill splitter: shares must add up to the total")).isEqualTo("{1 cents, 2}");
    }

    // ---- 2. Run-length codec without a separator ----------------------------------------------------------------

    static String naiveEncode(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int run = 1;
            while (i + run < text.length() && text.charAt(i + run) == c) {
                run++;
            }
            out.append(run).append(c);
            i += run;
        }
        return out.toString();
    }

    static String naiveDecode(String encoded) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < encoded.length()) {
            int digitsEnd = i;
            while (digitsEnd < encoded.length() && Character.isDigit(encoded.charAt(digitsEnd))) {
                digitsEnd++;
            }
            out.repeat(encoded.charAt(digitsEnd), Integer.parseInt(encoded.substring(i, digitsEnd)));
            i = digitsEnd + 1;
        }
        return out.toString();
    }

    @Test(description = "Codec draft without a separator breaks on digits; shrunk to a one-digit text")
    public void codecWithoutSeparator() {
        AssertionError failure = catchThrowableOfType(AssertionError.class, () -> theory()
                .forAll(runs())
                .checkAssert(text -> assertThat(naiveDecode(naiveEncode(text))).isEqualTo(text)));

        // "1" encodes to "11", which the draft reads as a run of eleven with no character after it
        assertThat(smallest(failure, "Run-length codec: decode(encode(text)) must give the text back")).isEqualTo("1");
    }

    @Test(description = "Limitation: a generator built with mix() shrinks less far (same bug, same seed: aaaa1, not 1)")
    public void mixedGeneratorShrinksLessFar() {
        // texts() is anyText().mix(runs()). The failure is found, but shrinking stops at a longer example.
        AssertionError failure = catchThrowableOfType(AssertionError.class, () -> theory()
                .forAll(texts())
                .checkAssert(text -> assertThat(naiveDecode(naiveEncode(text))).isEqualTo(text)));

        assertThat(smallest(failure, "Same codec property, mixed generator texts()")).isEqualTo("aaaa1");
    }

    // ---- 3. Calendar that does not merge touching slots ---------------------------------------------------------

    /** First draft: merges overlapping slots but uses {@code <} where touching slots need {@code <=}. */
    static List<TimeSlot> naiveBusy(List<TimeSlot> bookings) {
        List<TimeSlot> sorted = new ArrayList<>(bookings);
        sorted.sort(Comparator.comparingInt(TimeSlot::start));
        List<TimeSlot> merged = new ArrayList<>();
        for (TimeSlot slot : sorted) {
            if (!merged.isEmpty() && slot.start() < merged.getLast().end()) {
                TimeSlot last = merged.removeLast();
                merged.add(new TimeSlot(last.start(), Math.max(last.end(), slot.end())));
            } else {
                merged.add(slot);
            }
        }
        return merged;
    }

    @Test(description = "Calendar draft leaves touching slots apart; shrunk to two one-minute slots")
    public void calendarNotMergingTouchingSlots() {
        AssertionError failure = catchThrowableOfType(AssertionError.class, () -> theory()
                .forAll(CalendarGen.busyMorningBookings())
                .checkAssert(bookings -> {
                    List<TimeSlot> busy = naiveBusy(bookings);
                    for (int i = 1; i < busy.size(); i++) {
                        assertThat(busy.get(i).start()).as("gap before %s", busy.get(i)).isGreaterThan(busy.get(i - 1).end());
                    }
                }));

        assertThat(smallest(failure, "Booking calendar: busy slots must neither overlap nor touch"))
                .isEqualTo("[09:00-09:01, 09:01-09:02]");
    }

    // ---- 4. Stock ledger whose release() does not check what is reserved ---------------------------------------

    /** First draft: every method looks right alone; a sequence breaks it. */
    static final class LeakyLedger {
        int onHand;
        int reserved;

        void restock(int quantity) {
            onHand += quantity;
        }

        void reserve(int quantity) {
            if (quantity <= onHand - reserved) {
                reserved += quantity;
            }
        }

        void release(int quantity) {
            reserved -= quantity; // the bug: no check against what is reserved
        }
    }

    /** One call on the draft ledger; records, so the shrunk sequence prints readably. */
    sealed interface Step permits Restock, Reserve, Release {
        void applyTo(LeakyLedger ledger);
    }

    record Restock(int quantity) implements Step {
        public void applyTo(LeakyLedger ledger) {
            ledger.restock(quantity);
        }
    }

    record Reserve(int quantity) implements Step {
        public void applyTo(LeakyLedger ledger) {
            ledger.reserve(quantity);
        }
    }

    record Release(int quantity) implements Step {
        public void applyTo(LeakyLedger ledger) {
            ledger.release(quantity);
        }
    }

    @Test(description = "Ledger draft lets reserved stock go negative; shrunk to the shortest failing sequence")
    public void ledgerReleasingMoreThanReserved() {
        Gen<Integer> quantity = integers().between(1, 20);
        Gen<Step> step = quantity.<Step>map(Restock::new)
                .mix(quantity.map(Reserve::new), 33)
                .mix(quantity.map(Release::new), 33);

        AssertionError failure = catchThrowableOfType(AssertionError.class, () -> theory()
                .forAll(lists().of(step).ofSizeBetween(1, 30))
                .checkAssert(steps -> {
                    LeakyLedger ledger = new LeakyLedger();
                    for (Step s : steps) {
                        s.applyTo(ledger);
                        assertThat(ledger.reserved).as("reserved after %s", s).isNotNegative();
                    }
                }));

        // Releasing before anything was reserved: one step is enough, the 30-step sequences shrink to it
        assertThat(smallest(failure, "Stock ledger: reserved stock must never be negative"))
                .isEqualTo("[Release[quantity=1]]");
    }

    // ---- report ------------------------------------------------------------------------------------------------

    /** Checks the property really failed, records it for the report, and returns the shrunk example. */
    private String smallest(AssertionError failure, String property) {
        assertThat(failure).as("the draft should fail the property: " + property).isNotNull();
        Matcher m = FALSIFIED.matcher(failure.getMessage());
        assertThat(m.find()).as("QuickTheories failure message:%n%s", failure.getMessage()).isTrue();
        String examples = m.group(1);
        String smallest = m.group(2).strip();
        report.add("| " + property + " | " + examples + " | `" + smallest.replace("|", "\\|") + "` |");
        return smallest;
    }

    @AfterClass(alwaysRun = true)
    public void writeReport() throws IOException {
        List<String> lines = new ArrayList<>(List.of(
                "### Shrinking demos (buggy first drafts, seed " + SEED + ")",
                "",
                "| Property | Failed after | Smallest failing example |",
                "|---|---:|---|"));
        lines.addAll(report);
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "shrinking-report.md"), lines);
    }
}
