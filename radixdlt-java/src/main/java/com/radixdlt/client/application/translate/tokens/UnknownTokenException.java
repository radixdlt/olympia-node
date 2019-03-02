package com.radixdlt.client.application.translate.tokens;

/**
 * Exception thrown when information on an unknown token is requested.
 */
public class UnknownTokenException extends RuntimeException {
	private static final long serialVersionUID = -1684509326376059175L;
	private final TokenTypeReference tokenTypeReference;

    /**
     * Constructs a new unknown token exception with the specified token class reference.
     *
     * @param tokenTypeReference the token class that could not be found.
     *     Note that {@link #getMessage()} will include the token class name
     *     in the exception detail message.
     */
	public UnknownTokenException(TokenTypeReference tokenTypeReference) {
		super("Unknown token: " + tokenTypeReference);
		this.tokenTypeReference = tokenTypeReference;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (obj instanceof UnknownTokenException) {
			UnknownTokenException o = (UnknownTokenException) obj;
			return this.tokenTypeReference.equals(o.tokenTypeReference);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.tokenTypeReference.hashCode();
	}
}
