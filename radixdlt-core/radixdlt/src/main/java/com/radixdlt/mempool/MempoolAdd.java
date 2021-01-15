package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

public final class MempoolAdd {
	private final Command command;

	private MempoolAdd(Command command) {
		this.command = command;
	}

	public Command getCommand() {
		return command;
	}

	public static MempoolAdd create(Command command) {
		Objects.requireNonNull(command);
		return new MempoolAdd(command);
	}
}
