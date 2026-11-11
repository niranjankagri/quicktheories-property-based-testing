package com.niranjankagri.properties.generators;

import org.quicktheories.core.Gen;
import org.quicktheories.generators.Generate;

import java.util.List;

import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.lists;
import static org.quicktheories.generators.SourceDSL.strings;

/**
 * Generators for texts.
 *
 * <p>Random Unicode text almost never repeats a character, so it would never test a run longer than 1. The
 * {@link #runs()} generator builds texts from runs of the characters that matter to the codec: a letter, a digit, the
 * colon separator, and a character outside the Basic Multilingual Plane (two Java chars).
 */
public final class TextGen {

    private static final List<String> TRICKY = List.of("a", "1", "0", ":", "😀");

    private TextGen() {
    }

    /** Texts made of runs: "aaa111::😀😀", with runs long enough for two- and three-digit counts. */
    public static Gen<String> runs() {
        Gen<String> run = Generate.pick(TRICKY).zip(integers().between(1, 120), String::repeat);
        return lists().of(run).ofSizeBetween(0, 6).map(parts -> String.join("", parts));
    }

    /** Any text: every Unicode code point, up to 40 of them. */
    public static Gen<String> anyText() {
        return strings().allPossible().ofLengthBetween(0, 40);
    }

    /** Both of the above. */
    public static Gen<String> texts() {
        return anyText().mix(runs(), 50);
    }

    /** Strings that look a bit like encodings but mostly are not ("12:a3:", ":b", "00:x", "99999999:z"). */
    public static Gen<String> almostEncoded() {
        return strings().betweenCodePoints('0', ':').ofLengthBetween(0, 12)
                .mix(lists().of(Generate.pick(List.of("1", "0", "9", ":", "a", "12:", "99999999:"))).ofSizeBetween(0, 6)
                        .map(parts -> String.join("", parts)), 50);
    }
}
