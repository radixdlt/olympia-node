package com.radixdlt.sync.messages.remote;

import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;

import java.util.Objects;

/**
 * A message pushed to a subset of connected non-validator nodes indicating that the ledger state has been updated.
 */
public final class LedgerStatusUpdate {

    private final VerifiedLedgerHeaderAndProof header;

    public static LedgerStatusUpdate create(VerifiedLedgerHeaderAndProof header) {
        return new LedgerStatusUpdate(header);
    }

    private LedgerStatusUpdate(VerifiedLedgerHeaderAndProof header) {
        this.header = header;
    }

    public VerifiedLedgerHeaderAndProof getHeader() {
        return header;
    }

    @Override
    public String toString() {
        return String.format("%s{header=%s}", this.getClass().getSimpleName(), this.header);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LedgerStatusUpdate that = (LedgerStatusUpdate) o;
        return Objects.equals(header, that.header);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header);
    }
}
