package com.radixdlt.client.application.actions;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.assets.AssetAmount;
import com.radixdlt.client.core.address.RadixAddress;

public class TokenTransfer {
	private final RadixAddress from;
	private final RadixAddress to;
	private final Asset tokenClass;
	private final long subUnitAmount;

	public TokenTransfer(RadixAddress from, RadixAddress to, Asset tokenClass, long subUnitAmount) {
		this.from = from;
		this.to = to;
		this.tokenClass = tokenClass;
		this.subUnitAmount = subUnitAmount;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public Asset getTokenClass() {
		return tokenClass;
	}

	public long getSubUnitAmount() {
		return subUnitAmount;
	}

	@Override
	public String toString() {
		return from + " -> " + to + ": " + new AssetAmount(tokenClass, subUnitAmount).toString();
	}
}
