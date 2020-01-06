package org.radix.time;

public class NtpException extends RuntimeException {
	public NtpException(String message) {
		super(message);
	}

	public NtpException(String message, Throwable cause) {
		super(message, cause);
	}
}
