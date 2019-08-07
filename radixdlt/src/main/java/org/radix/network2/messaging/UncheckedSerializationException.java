package org.radix.network2.messaging;

import com.radixdlt.serialization.SerializationException;

public class UncheckedSerializationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public UncheckedSerializationException(String msg, SerializationException e) {
		super(msg, e);
	}

}
