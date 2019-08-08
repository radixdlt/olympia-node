package org.radix.network2.transport;

public class TransportException extends RuntimeException {
	private static final long serialVersionUID = 8857013204934239128L;

	public TransportException(String message) {
		super(message);
	}

	public TransportException(String message, Throwable cause) {
		super(message, cause);
	}

}
