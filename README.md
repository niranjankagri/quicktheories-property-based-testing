# Property-based testing with QuickTheories

Property-based tests for Java domain logic, with [QuickTheories](https://github.com/quicktheories/QuickTheories) and
TestNG. Instead of a few hand-picked examples, each test states a rule that must hold for **every** input
("the shares add up to the total"), and QuickTheories checks it on 1,000 generated inputs per run. When a rule
breaks, it **shrinks** the failing input to the smallest one that still fails.

**28 tests, 0 failures**, about 11 seconds (Java 21, QuickTheories 0.26, TestNG 7.12). The weekly CI run checks
20,000 inputs per property (about 24 seconds locally).

This complements [pit-mutation-testing](https://github.com/niranjankagri/pit-mutation-testing): PIT changes the
**code** to see whether the tests notice; property-based testing generates the **inputs** to find one the code gets
wrong.

## Shrinking: from a random failure to the smallest example

`ShrinkingDemoTest` runs properties against buggy **first drafts** of the classes (kept in test code, so they never
ship) and pins the smallest failing example QuickTheories found. Real run, fixed seed 20261111:

| First draft and its bug | Property it breaks | Failed after | Shrunk to |
|---|---|---:|---|
| Bill splitter gives everyone `total / people` and drops the remainder | shares add up to the total | 2 examples | `{1 cents, 2}`: 1 cent between 2 people pays 0 |
| Run-length codec writes `3a1b` with no separator | `decode(encode(text)) == text` | 2 | `1`: encodes to `11`, read back as "eleven of nothing" |
| Calendar merges overlapping slots with `<` instead of `<=` | busy slots neither overlap nor touch | 15 | `[09:00-09:01, 09:01-09:02]`: two touching minutes |
| Stock ledger's `release()` does not check what is reserved | reserved stock is never negative | 1 | `[Release[quantity=1]]`: one call, out of up to 30 generated |

The original failing inputs were long random texts, lists of up to 12 slots and sequences of up to 30 calls; the
shrunk ones are what you would write as a regression example. CI writes this table into the job summary.

The calendar bug was not invented for the demo: it is the first thing tried when checking that the calendar
properties can fail at all (change `<=` to `<` in `BookingCalendar.busy`, run the tests, read the shrunk example).

## The properties

| Class | Properties (each checked on 1,000 generated inputs) | Plus an example |
|---|---|---|
| `money/BillSplitter` | shares add up to the total · one share per person · shares differ by at most 1 cent · largest share first · one person pays everything · negative totals and 0 people refused | 100.00 / 3 = 33.34, 33.33, 33.33 |
| `calendar/BookingCalendar` | busy time covers exactly the booked minutes · busy slots sorted, never overlapping or touching · merging twice = merging once · input order does not matter · inside opening hours every minute is free xor booked · free slots stay inside opening hours | three overlapping/touching slots merge into one |
| `text/RunLengthCodec` | round trip for any text (all of Unicode, digits, the `:` separator, emoji) · encoded size follows the runs · bad input only ever throws `IllegalArgumentException` | `aaab` = `3:a1:b` |
| `inventory/StockLedger` | **stateful**: after every step of a random call sequence the ledger equals a simple model · nothing ever negative | reserve, ship, release by hand |

Techniques used:

- **An oracle that is obviously right.** The calendar is checked against a `BitSet` with one bit per busy minute:
  far too plain for production, simple enough to trust at a glance.
- **Round trip.** `decode(encode(x)) == x` needs no expected values at all.
- **Model check (stateful).** QuickTheories' `Sequential.modelCheck` runs random sequences of commands
  (restock, reserve, release, ship) on the real ledger and on a two-field model, comparing them after every step
  ([`LedgerCommands`](src/test/java/com/niranjankagri/properties/generators/LedgerCommands.java)).
- **Invariants and refusals.** Bad input must fail in one documented way (`IllegalArgumentException`), never with an
  index or memory error. Writing that property is what led to `RunLengthCodec` limiting run lengths: `"99999999:x"`
  would otherwise ask for 100 million characters.

## Generators: steering the random inputs

Uniformly random inputs rarely hit the cases that matter, so the generators in
[`generators/`](src/test/java/com/niranjankagri/properties/generators) steer them:

| Generator | Why |
|---|---|
| `MoneyGen.amountsInCents()` | Random amounts up to 1,000,000.00, with a quarter drawn from edge values (0, 1, 99, 100, 101, 9,999...) |
| `MoneyGen.people()` | 1 to 200, with 1, 2 and 3 mixed in often (where the remainder logic differs most) |
| `CalendarGen.bookings()` | Half the slots packed into one busy morning, so overlaps and touching slots are common; some at 00:00 and 24:00 |
| `TextGen.runs()` | Texts built from runs of `a`, `1`, `0`, `:` and an emoji (two Java chars), up to 120 long: random Unicode text almost never repeats a character |
| `TextGen.almostEncoded()` | Strings that look like encodings but mostly are not (`12:a3:`, `00:x`, `99999999:z`) |
| `LedgerCommands.sequences()` | 1 to 40 commands with quantities 1 to 20, so "not enough stock" happens often |

## Limits found on real runs

QuickTheories 0.26 is from March 2019 and the project has had no commits since 2020. It works on Java 21 and with
TestNG 7.12 here, but two shrinking limits showed up and are pinned in tests:

| What | Observed (seed 20261111) | What to do |
|---|---|---|
| A generator built with `mix()` shrinks less far | The codec bug shrinks to `aaaa1` with `texts()` (= `anyText().mix(runs())`), but to `1` with `runs()` alone. The calendar bug: 6 slots with the mixed `bookings()`, 2 with `busyMorningBookings()`. More shrink cycles (10,000 or 1,000,000) gave the same results | Keep the mixed generators for finding bugs; when one fails, re-run with a single-source generator to get a small example |
| Numbers shrink close to the edge, not always onto it | `i < 900000` failed; the shrunk value was 900,003 (900,007 with another seed), not 900,000 | Read the shrunk value as "near here", then write the exact boundary as an example test |

## Reproducing a failure

A failing property prints the smallest input, other failing inputs, and the seed:

```
Property falsified after 2 example(s)
Smallest found falsifying value(s) :-
900007
...
Seed was 42
```

Run again with that seed to get the same inputs (checked: the same seed gave the same failure twice):

```bash
./mvnw test -Dtest=BillSplitterPropertiesTest -DQT_SEED=42
```

## Run it

Needs Java 21+.

```bash
./mvnw test                          # 28 tests, 1,000 examples per property
./mvnw test -Dqt.examples=20000      # deeper, like the weekly CI run
./mvnw test -Dtest=ShrinkingDemoTest # the shrinking demos; table in target/shrinking-report.md
```

## Design

| Pattern | Where |
|---|---|
| Generators as test data objects | `generators/MoneyGen`, `CalendarGen`, `TextGen`, `LedgerCommands`: each says in its Javadoc why it is shaped that way |
| Command objects for stateful tests | `LedgerCommands.Restock`, `Reserve`, `Release`, `Ship`: records, so a shrunk sequence prints as `[Release[quantity=1]]` |
| Oracle / model | `BitSet` of busy minutes for the calendar, `LedgerCommands.Model` for the ledger |
| Buggy drafts in test code only | `shrinking/ShrinkingDemoTest`: the demos fail on purpose, and the test passes when they fail as expected |
| One example per class | Next to the properties, so a reader sees a concrete case before the general rule |

## Why QuickTheories (and not jqwik)

jqwik is the better-known Java library, but from version 1.10 its maintainer added an
[Anti-AI Usage Clause](https://jqwik.net/docs/current/user-guide.html#anti-ai-usage-clause), and its test engine
prints a message telling AI coding agents not to use it. This project respects the maintainer's stated intent and
uses QuickTheories (Apache 2.0) instead, rather than pinning a jqwik version from before the clause.

## Project layout

```
src/main/java/.../money        BillSplitter
src/main/java/.../calendar     TimeSlot, BookingCalendar
src/main/java/.../text         RunLengthCodec
src/main/java/.../inventory    StockLedger
src/test/java/.../generators   MoneyGen, CalendarGen, TextGen, LedgerCommands
src/test/java/.../<area>       *PropertiesTest: properties plus one example each
src/test/java/.../shrinking    ShrinkingDemoTest: buggy drafts and their shrunk failures
```

## License

MIT
