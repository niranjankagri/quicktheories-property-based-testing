package com.niranjankagri.properties.money;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a bill between people, in whole cents, without losing or inventing a cent.
 *
 * <p>100.00 between 3 is 33.34, 33.33, 33.33: the remainder goes one cent at a time to the first people. An example
 * test checks one split; the properties in {@code BillSplitterPropertiesTest} check every split QuickTheories generates, which
 * is how they would catch the classic bug of dropping the remainder (see {@code shrinking/ShrinkingDemoTest}).
 */
public final class BillSplitter {

    /**
     * @param totalCents amount to split, 0 or more
     * @param people     number of people, at least 1
     * @return one share per person, largest first; shares differ by at most one cent and add up to the total
     * @throws IllegalArgumentException for a negative total or fewer than one person
     */
    public List<Long> split(long totalCents, int people) {
        if (totalCents < 0) {
            throw new IllegalArgumentException("Total must not be negative: " + totalCents);
        }
        if (people < 1) {
            throw new IllegalArgumentException("Need at least one person: " + people);
        }
        long base = totalCents / people;
        long remainder = totalCents % people;
        List<Long> shares = new ArrayList<>(people);
        for (int i = 0; i < people; i++) {
            shares.add(i < remainder ? base + 1 : base);
        }
        return shares;
    }
}
