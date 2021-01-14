package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

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
}
