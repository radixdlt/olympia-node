package org.radix.network2.messaging;

import java.util.Arrays;
import java.util.Objects;

import org.radix.network2.transport.TransportInfo;

import com.radixdlt.utils.Bytes;

public final class InboundMessage {
	private final TransportInfo source;
	private final byte[] message;

	public static InboundMessage of(TransportInfo source, byte[] message) {
		return new InboundMessage(source, message);
	}

	private InboundMessage(TransportInfo source, byte[] message) {
		// Null checking not performed for high-frequency interface
		this.source = source;
		this.message = message;
	}

	public TransportInfo source() {
		return source;
	}

	public byte[] message() {
		return message;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(message) * 31 + Objects.hashCode(source);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof InboundMessage) {
			InboundMessage other = (InboundMessage) obj;
			return Objects.equals(this.source, other.source) && Arrays.equals(this.message, other.message);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), source, Bytes.toHexString(message));
	}
}
