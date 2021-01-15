package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

/**
 * Message indicating that a command failed to be added to the mempool
 */
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

	@Override
	public int hashCode() {
		return Objects.hash(command, exception);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MempoolAddFailure)) {
			return false;
		}

		MempoolAddFailure other = (MempoolAddFailure) o;
		return Objects.equals(this.command, other.command)
			&& Objects.equals(this.exception, other.exception);
	}

	@Override
	public String toString() {
		return String.format("%s{cmd=%s ex=%s}", this.getClass().getSimpleName(), this.command, this.exception);
	}
}
