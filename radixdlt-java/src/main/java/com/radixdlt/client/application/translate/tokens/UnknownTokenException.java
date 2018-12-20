package com.radixdlt.client.application.translate.tokens;

public class UnknownTokenException extends RuntimeException {
	private static final long serialVersionUID = -1684509326376059175L;
	private final TokenClassReference tokenClassReference;

	public UnknownTokenException(TokenClassReference tokenClassReference) {
		super("Unknown token: " + tokenClassReference);
		this.tokenClassReference = tokenClassReference;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (obj instanceof UnknownTokenException) {
			UnknownTokenException o = (UnknownTokenException) obj;
			return this.tokenClassReference.equals(o.tokenClassReference);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.tokenClassReference.hashCode();
	}
}
