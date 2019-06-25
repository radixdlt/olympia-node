package com.radixdlt.client.application.translate;

/**
 * An exception which occurs during the construction of an action.
 */
public abstract class StageActionException extends RuntimeException {
	public StageActionException(String message) {
		super(message);
	}
}
