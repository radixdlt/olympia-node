package com.radixdlt.client.core.fungible;

import java.util.Objects;
import org.radix.utils.UInt256;

public class NotEnoughFungibleException extends Exception {
	private final UInt256 requested;
	private final UInt256 current;

	public NotEnoughFungibleException(UInt256 requested, UInt256 current) {
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
