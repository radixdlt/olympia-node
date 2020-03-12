package com.radixdlt.client.core.fungible;

import java.util.Objects;
import com.radixdlt.utils.UInt256;

/**
 * Exception thrown indicating that there is not enough input
 * into fungible matcher to create requested output.
 */
public class NotEnoughFungiblesException extends Exception {
	private final UInt256 requested;
	private final UInt256 current;

	public NotEnoughFungiblesException(UInt256 requested, UInt256 current) {
		this.requested = Objects.requireNonNull(requested);
		this.current = Objects.requireNonNull(current);
	}

	public UInt256 getCurrent() {
		return current;
	}

	public UInt256 getRequested() {
		return requested;
	}
}
