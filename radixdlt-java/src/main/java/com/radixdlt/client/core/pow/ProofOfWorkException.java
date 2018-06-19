package com.radixdlt.client.core.pow;

public class ProofOfWorkException extends Exception {
	public ProofOfWorkException(String pow, String target) {
		super(pow + " does not meet target: " + target);
	}
}
