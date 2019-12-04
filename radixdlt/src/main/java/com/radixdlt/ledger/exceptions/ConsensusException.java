package com.radixdlt.ledger.exceptions;

import com.radixdlt.ledger.Consensus;

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
