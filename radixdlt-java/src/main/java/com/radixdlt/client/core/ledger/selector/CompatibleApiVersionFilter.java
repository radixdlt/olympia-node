package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixPeerState;

/**
 * An api version filter that rejects any incompatible peers as determined by the
 */
public class CompatibleApiVersionFilter implements RadixPeerFilter {
	private final int version;

	public CompatibleApiVersionFilter(int version) {
		this.version = version;
	}

	@Override
	public boolean test(RadixPeerState peerState) {
		return peerState.getVersion() != null && peerState.getVersion() == this.version;
	}
}
