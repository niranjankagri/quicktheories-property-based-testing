package com.niranjankagri.properties.inventory;

import com.niranjankagri.properties.generators.LedgerCommands.Model;
import org.quicktheories.core.stateful.Sequential;
import org.testng.annotations.Test;

import static com.niranjankagri.properties.generators.LedgerCommands.sequences;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.quicktheories.QuickTheory.qt;

/**
 * Stateful properties of {@link StockLedger}: random sequences of restock, reserve, release and ship calls.
 * A failing sequence is shrunk to the shortest one that still fails (see {@code shrinking/ShrinkingDemoTest}).
 */
public class StockLedgerPropertiesTest {

    @Test(description = "Example: reserve 3 of 5, ship 2, release 1: 3 on hand, 0 reserved")
    public void example() {
        StockLedger ledger = new StockLedger();
        ledger.restock(5);
        ledger.reserve(3);
        ledger.ship(2);
        ledger.release(1);

        assertThat(ledger.onHand()).isEqualTo(3);
        assertThat(ledger.reserved()).isZero();
        assertThatIllegalStateException().isThrownBy(() -> ledger.ship(1));
    }

    @Test(description = "After every step of any sequence, the ledger matches a simple model")
    public void ledgerMatchesTheModel() {
        qt().forAll(sequences()).checkAssert(commands ->
                Sequential.modelCheck(Model.EMPTY, commands, Model::toLedger, Model::of));
    }

    @Test(description = "After every step of any sequence, nothing is negative and reserved never exceeds on hand")
    public void invariantsHoldAfterEveryStep() {
        qt().forAll(sequences()).checkAssert(commands -> {
            StockLedger ledger = new StockLedger();
            for (var command : commands) {
                command.run(ledger);
                assertThat(ledger.reserved()).as("reserved after %s", command).isNotNegative();
                assertThat(ledger.available()).as("available after %s", command).isNotNegative();
                assertThat(ledger.onHand()).as("on hand after %s", command).isNotNegative();
            }
        });
    }
}
