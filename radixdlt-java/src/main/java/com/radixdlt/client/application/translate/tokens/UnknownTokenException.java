package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.identifiers.RRI;

/**
 * Exception thrown when information on an unknown token is requested.
 */
public class UnknownTokenException extends StageActionException {
	private final RRI tokenDefinitionReference;

    /**
     * Constructs a new unknown token exception with the specified token class reference.
     *
     * @param tokenDefinitionReference the token class that could not be found.
     *     Note that {@link #getMessage()} will include the token class name
     *     in the exception detail message.
     */
	public UnknownTokenException(RRI tokenDefinitionReference) {
		super("Unknown token: " + tokenDefinitionReference);
		this.tokenDefinitionReference = tokenDefinitionReference;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}

		if (obj instanceof UnknownTokenException) {
			UnknownTokenException o = (UnknownTokenException) obj;
			return this.tokenDefinitionReference.equals(o.tokenDefinitionReference);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.tokenDefinitionReference.hashCode();
	}
}
