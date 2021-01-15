package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

/**
 * Message indicating that a command was successfully added to the mempool
 */
public final class MempoolAddSuccess {
    private final Command command;

    private MempoolAddSuccess(Command command) {
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }

    public static MempoolAddSuccess create(Command command) {
        Objects.requireNonNull(command);
        return new MempoolAddSuccess(command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MempoolAddSuccess)) {
            return false;
        }

        MempoolAddSuccess other = (MempoolAddSuccess) o;
        return Objects.equals(this.command, other.command);
    }

    @Override
    public String toString() {
        return String.format("%s{cmd=%s}", this.getClass().getSimpleName(), this.command);
    }
}
