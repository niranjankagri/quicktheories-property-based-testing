package com.niranjankagri.properties.money;

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static com.niranjankagri.properties.generators.MoneyGen.amountsInCents;
import static com.niranjankagri.properties.generators.MoneyGen.people;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.longs;

/**
 * Properties of {@link BillSplitter}: rules that hold for every total and every number of people, checked on
 * generated inputs (1,000 per property by default). One example test pins a split a reader can verify by hand.
 */
public class BillSplitterPropertiesTest {

    private final BillSplitter splitter = new BillSplitter();

    @Test(description = "Example: 100.00 between 3 is 33.34, 33.33, 33.33")
    public void exampleThreeWaySplit() {
        assertThat(splitter.split(10_000, 3)).containsExactly(3_334L, 3_333L, 3_333L);
    }

    @Test(description = "No cent is lost or invented: the shares add up to the total")
    public void sharesAddUpToTheTotal() {
        qt().forAll(amountsInCents(), people()).checkAssert((total, people) ->
                assertThat(sum(splitter.split(total, people))).isEqualTo(total));
    }

    @Test(description = "Everyone gets a share")
    public void oneSharePerPerson() {
        qt().forAll(amountsInCents(), people()).checkAssert((total, people) ->
                assertThat(splitter.split(total, people)).hasSize(people));
    }

    @Test(description = "Fair: shares differ by at most one cent")
    public void sharesDifferByAtMostOneCent() {
        qt().forAll(amountsInCents(), people()).checkAssert((total, people) -> {
            List<Long> shares = splitter.split(total, people);
            assertThat(Collections.max(shares) - Collections.min(shares)).isBetween(0L, 1L);
        });
    }

    @Test(description = "Shares come largest first, so whoever is listed first pays the extra cent")
    public void largestShareFirst() {
        qt().forAll(amountsInCents(), people()).checkAssert((total, people) ->
                assertThat(splitter.split(total, people)).isSortedAccordingTo(Collections.reverseOrder()));
    }

    @Test(description = "Splitting between one person changes nothing")
    public void onePersonPaysEverything() {
        qt().forAll(amountsInCents()).checkAssert(total ->
                assertThat(splitter.split(total, 1)).containsExactly(total));
    }

    @Test(description = "A negative total is refused, whatever the number of people")
    public void negativeTotalIsRefused() {
        qt().forAll(longs().between(Long.MIN_VALUE, -1), people()).checkAssert((total, people) ->
                assertThatIllegalArgumentException().isThrownBy(() -> splitter.split(total, people)));
    }

    @Test(description = "Zero or fewer people is refused")
    public void noPeopleIsRefused() {
        qt().forAll(amountsInCents(), integers().between(Integer.MIN_VALUE, 0)).checkAssert((total, people) ->
                assertThatIllegalArgumentException().isThrownBy(() -> splitter.split(total, people)));
    }

    private static long sum(List<Long> shares) {
        return shares.stream().mapToLong(Long::longValue).sum();
    }
}
