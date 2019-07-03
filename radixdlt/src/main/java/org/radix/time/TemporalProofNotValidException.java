package org.radix.time;

import org.radix.exceptions.ValidationException;

public final class TemporalProofNotValidException extends ValidationException {
	private final TemporalProof temporalProof;

	public TemporalProofNotValidException(TemporalProof temporalProof) {
		super("TemporalProof is not valid for " + temporalProof.getAID());

		this.temporalProof = temporalProof;
	}

	public TemporalProofNotValidException(String message, TemporalProof temporalProof) {
		super(message);

		this.temporalProof = temporalProof;
	}

	public TemporalProof getTemporalProof() {
		return temporalProof;
	}
}
