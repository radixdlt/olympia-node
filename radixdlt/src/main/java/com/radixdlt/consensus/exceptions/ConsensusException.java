package com.radixdlt.consensus.exceptions;

/**
 * Generic runtime exception in a {@link Consensus}
 */
public class ConsensusException extends RuntimeException {
	public ConsensusException(String message) {
		super(message);
	}

	public ConsensusException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConsensusException(Throwable cause) {
		super(cause);
	}
}
