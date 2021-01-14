package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

public final class MempoolAddFailure {
	private final Command command;
	private final Exception exception;

	private MempoolAddFailure(Command command, Exception exception) {
		this.command = command;
		this.exception = exception;
	}

	public Command getCommand() {
		return command;
	}

	public Exception getException() {
		return exception;
	}

	public static MempoolAddFailure create(Command command, Exception exception) {
		Objects.requireNonNull(command);
		return new MempoolAddFailure(command, exception);
	}
}
