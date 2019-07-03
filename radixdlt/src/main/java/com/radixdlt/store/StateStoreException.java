package com.radixdlt.store;

public class StateStoreException extends RuntimeException {
	public StateStoreException(String message) {
		super(message);
	}

	public StateStoreException(String message, Throwable cause) {
		super(message, cause);
	}

	public StateStoreException(Throwable cause) {
		super(cause);
	}
}
