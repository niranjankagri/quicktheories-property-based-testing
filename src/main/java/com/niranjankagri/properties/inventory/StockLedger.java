package com.niranjankagri.properties.inventory;

/**
 * Stock of one product: what is on the shelf, and how much of it is reserved for orders not yet shipped.
 *
 * <pre>
 *   restock(n)   n more units on hand
 *   reserve(n)   hold n units for an order; refused (false) when fewer than n are available
 *   release(n)   give n reserved units back (order cancelled)
 *   ship(n)      n reserved units leave the warehouse
 * </pre>
 *
 * <p>Single calls are easy to test. Bugs hide in sequences ("reserve, ship, release, restock, reserve..."), so
 * {@code StockLedgerPropertiesTest} lets QuickTheories generate random sequences of calls and compares the ledger with
 * a simple model after every step. A failing sequence is shrunk to the fewest steps that still fail.
 */
public final class StockLedger {

    private int onHand;
    private int reserved;

    /** @throws IllegalArgumentException for a quantity below 1 */
    public void restock(int quantity) {
        requirePositive(quantity);
        onHand += quantity;
    }

    /** @return true if the units were reserved; false (and nothing changes) when not enough are available */
    public boolean reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > available()) {
            return false;
        }
        reserved += quantity;
        return true;
    }

    /** @throws IllegalStateException when releasing more than is reserved */
    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved) {
            throw new IllegalStateException("Cannot release " + quantity + ", only " + reserved + " reserved");
        }
        reserved -= quantity;
    }

    /** @throws IllegalStateException when shipping more than is reserved */
    public void ship(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved) {
            throw new IllegalStateException("Cannot ship " + quantity + ", only " + reserved + " reserved");
        }
        reserved -= quantity;
        onHand -= quantity;
    }

    public int onHand() {
        return onHand;
    }

    public int reserved() {
        return reserved;
    }

    /** Units that can still be reserved. */
    public int available() {
        return onHand - reserved;
    }

    private static void requirePositive(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1: " + quantity);
        }
    }
}
