package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

public final class MempoolAddedCommand {
    private final Command command;

    private MempoolAddedCommand(Command command) {
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }

    public static MempoolAddedCommand create(Command command) {
        Objects.requireNonNull(command);
        return new MempoolAddedCommand(command);
    }
}
