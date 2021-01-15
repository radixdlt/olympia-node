package com.radixdlt.mempool;

import com.radixdlt.consensus.Command;

import java.util.Objects;

/**
 * Message to attempt to add a command to the mempool
 */
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

	@Override
	public int hashCode() {
		return Objects.hashCode(command);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MempoolAdd)) {
			return false;
		}

		MempoolAdd other = (MempoolAdd) o;
		return Objects.equals(this.command, other.command);
	}

	@Override
	public String toString() {
		return String.format("%s{cmd=%s}", this.getClass().getSimpleName(), this.command);
	}
}
