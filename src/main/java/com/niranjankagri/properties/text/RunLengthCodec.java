package com.niranjankagri.properties.text;

/**
 * Run-length encoding: {@code "aaab"} becomes {@code "3:a1:b"}.
 *
 * <p>Each run is written as count, a colon, and the character. The colon matters: without it ({@code "3a1b"}) a text
 * containing digits cannot be decoded, because {@code "11"} (one "1") would read as a count of 11. That first draft is
 * in {@code shrinking/ShrinkingDemoTest}; the round-trip property finds the problem and
 * shrinks it to a one-character text.
 */
public final class RunLengthCodec {

    /**
     * @throws IllegalArgumentException for a run of more than 9,999,999 equal characters, which
     *                                  {@link #decode} would refuse
     */
    public String encode(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int run = 1;
            while (i + run < text.length() && text.charAt(i + run) == c) {
                run++;
            }
            if (run > MAX_RUN) {
                // decode() refuses longer runs, so encoding one would produce text that cannot be read back
                throw new IllegalArgumentException("Run of " + run + " is longer than " + MAX_RUN);
            }
            out.append(run).append(':').append(c);
            i += run;
        }
        return out.toString();
    }

    /**
     * @throws IllegalArgumentException for text that {@link #encode} cannot have produced
     */
    public String decode(String encoded) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < encoded.length()) {
            int colon = encoded.indexOf(':', i);
            // A run needs at least one digit, the colon and one character after it
            if (colon <= i || colon + 1 >= encoded.length()) {
                throw new IllegalArgumentException("Not run-length encoded at index " + i + ": " + encoded);
            }
            int run = parseCount(encoded.substring(i, colon), encoded);
            out.repeat(encoded.charAt(colon + 1), run);
            i = colon + 2;
        }
        return out.toString();
    }

    /**
     * Run lengths are plain ASCII digits (not other scripts' digits, which {@code Character.isDigit} would accept),
     * 1 to {@value #MAX_RUN_DIGITS} digits long, and never 0: {@link #encode} never writes anything else. The length
     * limit stops a short hostile input ("9999999999:x") from asking for gigabytes of memory.
     */
    private static int parseCount(String digits, String encoded) {
        if (digits.length() > MAX_RUN_DIGITS) {
            throw new IllegalArgumentException("Run length too long '" + digits + "' in: " + encoded);
        }
        for (int k = 0; k < digits.length(); k++) {
            char d = digits.charAt(k);
            if (d < '0' || d > '9') {
                throw new IllegalArgumentException("Not a run length '" + digits + "' in: " + encoded);
            }
        }
        int run = Integer.parseInt(digits);
        if (run == 0) {
            throw new IllegalArgumentException("Run length 0 in: " + encoded);
        }
        return run;
    }

    private static final int MAX_RUN_DIGITS = 7;
    /** Longest run that fits in {@value #MAX_RUN_DIGITS} digits. */
    static final int MAX_RUN = 9_999_999;
}
