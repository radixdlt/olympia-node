package com.radixdlt.consensus.tempo;

import com.radixdlt.consensus.exceptions.ConsensusException;

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
