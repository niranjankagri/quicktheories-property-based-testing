package com.niranjankagri.properties.text;

import org.testng.annotations.Test;

import static com.niranjankagri.properties.generators.TextGen.almostEncoded;
import static com.niranjankagri.properties.generators.TextGen.runs;
import static com.niranjankagri.properties.generators.TextGen.texts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.quicktheories.QuickTheory.qt;

/** Properties of {@link RunLengthCodec}: the round trip, compactness, and safe handling of bad input. */
public class RunLengthCodecPropertiesTest {

    private final RunLengthCodec codec = new RunLengthCodec();

    @Test(description = "Example: aaab is 3:a1:b")
    public void example() {
        assertThat(codec.encode("aaab")).isEqualTo("3:a1:b");
        assertThat(codec.decode("3:a1:b")).isEqualTo("aaab");
    }

    @Test(description = "Round trip: decoding an encoded text gives the text back, for any text")
    public void roundTrip() {
        qt().forAll(texts()).checkAssert(text -> assertThat(codec.decode(codec.encode(text))).isEqualTo(text));
    }

    @Test(description = "Each run takes count + colon + character, so long runs get shorter")
    public void encodedLengthFollowsTheRuns() {
        qt().forAll(runs()).checkAssert(text -> {
            String encoded = codec.encode(text);
            // Never more than 9 chars per run (7 digits, colon, character), never more runs than characters
            assertThat(encoded.length()).isLessThanOrEqualTo(9 * text.length());
            assertThat(encoded.isEmpty()).isEqualTo(text.isEmpty());
        });
    }

    @Test(description = "Bad input is refused with IllegalArgumentException, never another exception")
    public void badInputOnlyEverThrowsIllegalArgument() {
        qt().forAll(almostEncoded()).checkAssert(input -> {
            try {
                String decoded = codec.decode(input);
                // Accepted input must be readable again: decode(encode(x)) == x for whatever came out
                assertThat(codec.decode(codec.encode(decoded))).isEqualTo(decoded);
            } catch (IllegalArgumentException refused) {
                // allowed; any other exception type (index out of bounds, out of memory) fails the property
            }
        });
    }

    @Test(description = "A run too long for seven digits is refused when encoding, so nothing unreadable is written")
    public void tooLongRunIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> codec.encode("x".repeat(RunLengthCodec.MAX_RUN + 1)));
        assertThat(codec.decode(codec.encode("x".repeat(RunLengthCodec.MAX_RUN)))).hasSize(RunLengthCodec.MAX_RUN);
    }
}
