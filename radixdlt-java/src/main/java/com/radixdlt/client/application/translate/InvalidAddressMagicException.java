package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

public final class InvalidAddressMagicException extends StageActionException {
	private final RadixAddress address;

	public InvalidAddressMagicException(RadixAddress address, int expectingMagicByte) {
		super("Expecting magic of " + address + " to be " + expectingMagicByte + " but was " + address.getMagicByte());
		this.address = address;
	}
}
