package com.niranjankagri.properties.generators;

import com.niranjankagri.properties.inventory.StockLedger;
import org.quicktheories.core.Gen;
import org.quicktheories.core.stateful.Command;
import org.quicktheories.generators.Generate;

import java.util.List;

import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.lists;

/**
 * Random sequences of calls on a {@link StockLedger}, each paired with what a deliberately simple model expects.
 *
 * <p>For QuickTheories' {@code Sequential.modelCheck}, every command does two things: {@code run} calls the real
 * ledger, {@code nextState} works out the expected state on the {@link Model}. After each step the ledger's state must
 * equal the model's. Commands are records, so a shrunk failure reads like {@code [Reserve[quantity=1]]}.
 */
public final class LedgerCommands {

    private LedgerCommands() {
    }

    /** The expected state: just two numbers and the rules, written as plainly as possible. */
    public record Model(int onHand, int reserved) {

        public static final Model EMPTY = new Model(0, 0);

        int available() {
            return onHand - reserved;
        }

        /** Builds a real ledger in this state (used for the starting state). */
        public StockLedger toLedger() {
            StockLedger ledger = new StockLedger();
            if (onHand > 0) {
                ledger.restock(onHand);
            }
            if (reserved > 0) {
                ledger.reserve(reserved);
            }
            return ledger;
        }

        public static Model of(StockLedger ledger) {
            return new Model(ledger.onHand(), ledger.reserved());
        }
    }

    public record Restock(int quantity) implements Command<StockLedger, Model> {
        @Override
        public void run(StockLedger ledger) {
            ledger.restock(quantity);
        }

        @Override
        public Model nextState(Model m) {
            return new Model(m.onHand() + quantity, m.reserved());
        }
    }

    public record Reserve(int quantity) implements Command<StockLedger, Model> {
        @Override
        public void run(StockLedger ledger) {
            ledger.reserve(quantity);
        }

        @Override
        public Model nextState(Model m) {
            return quantity <= m.available() ? new Model(m.onHand(), m.reserved() + quantity) : m;
        }
    }

    /** Releasing more than is reserved must be refused (exception) and change nothing. */
    public record Release(int quantity) implements Command<StockLedger, Model> {
        @Override
        public void run(StockLedger ledger) {
            try {
                ledger.release(quantity);
            } catch (IllegalStateException refused) {
                // expected when quantity > reserved; the state comparison checks nothing changed
            }
        }

        @Override
        public Model nextState(Model m) {
            return quantity <= m.reserved() ? new Model(m.onHand(), m.reserved() - quantity) : m;
        }
    }

    /** Shipping takes reserved units off the shelf; more than is reserved must be refused. */
    public record Ship(int quantity) implements Command<StockLedger, Model> {
        @Override
        public void run(StockLedger ledger) {
            try {
                ledger.ship(quantity);
            } catch (IllegalStateException refused) {
                // expected when quantity > reserved
            }
        }

        @Override
        public Model nextState(Model m) {
            return quantity <= m.reserved() ? new Model(m.onHand() - quantity, m.reserved() - quantity) : m;
        }
    }

    /** One command with a quantity of 1 to 20. Small quantities make refusals (not enough stock) common. */
    public static Gen<Command<StockLedger, Model>> command() {
        Gen<Integer> quantity = integers().between(1, 20);
        return Generate.oneOf(
                quantity.map(q -> (Command<StockLedger, Model>) new Restock(q)),
                quantity.map(q -> (Command<StockLedger, Model>) new Reserve(q)),
                quantity.map(q -> (Command<StockLedger, Model>) new Release(q)),
                quantity.map(q -> (Command<StockLedger, Model>) new Ship(q)));
    }

    /** 1 to 40 commands in a row. */
    public static Gen<List<Command<StockLedger, Model>>> sequences() {
        return lists().of(command()).ofSizeBetween(1, 40);
    }
}
