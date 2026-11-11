package com.niranjankagri.properties.generators;

import org.quicktheories.core.Gen;
import org.quicktheories.generators.Generate;

import java.util.List;

import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.longs;

/**
 * Generators for amounts and head counts.
 *
 * <p>Uniform random amounts up to a million almost never hit 0, 1 cent or "one cent less than a multiple of the
 * people", where splitting bugs live. So a quarter of the amounts come from a fixed list of edge values.
 */
public final class MoneyGen {

    private static final List<Long> EDGE_AMOUNTS = List.of(0L, 1L, 2L, 99L, 100L, 101L, 999L, 1_000L, 9_999L, 10_000L);

    private MoneyGen() {
    }

    /** 0.00 to 1,000,000.00 in cents, with edge values mixed in (25%). */
    public static Gen<Long> amountsInCents() {
        return longs().between(0, 100_000_000).mix(Generate.pick(EDGE_AMOUNTS), 25)
                .describedAs(c -> c + " cents");
    }

    /** 1 to 200 people; 1, 2 and 3 are mixed in often because the remainder logic differs most there. */
    public static Gen<Integer> people() {
        return integers().between(1, 200).mix(Generate.pick(List.of(1, 2, 3)), 25);
    }
}
