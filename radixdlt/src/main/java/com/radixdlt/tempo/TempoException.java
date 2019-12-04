package com.radixdlt.tempo;

import com.radixdlt.ledger.exceptions.ConsensusException;

/**
 * A Tempo exception
 */
public class TempoException extends ConsensusException {
	public TempoException(String message) {
		super(message);
	}

	public TempoException(String message, Throwable cause) {
		super(message, cause);
	}

	public TempoException(Throwable cause) {
		super(cause);
	}
}
