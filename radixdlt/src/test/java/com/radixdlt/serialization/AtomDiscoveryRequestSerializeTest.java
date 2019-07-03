package com.radixdlt.serialization;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.universe.Universe;
import org.radix.atoms.AtomDiscoveryRequest;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.CryptoException;
import org.radix.discovery.DiscoveryCursor;
import org.radix.discovery.DiscoveryRequest.Action;
import org.radix.modules.Modules;

/**
 * Check serialization of AtomDiscoveryRequest
 */
public class AtomDiscoveryRequestSerializeTest extends SerializeObject<AtomDiscoveryRequest> {
	public AtomDiscoveryRequestSerializeTest() {
		super(AtomDiscoveryRequest.class, AtomDiscoveryRequestSerializeTest::get);
	}

	private static AtomDiscoveryRequest get() {
		try {
			AtomDiscoveryRequest adr = new AtomDiscoveryRequest(Action.DISCOVER);
			adr.setCursor(new DiscoveryCursor(12));
			ECKeyPair key = new ECKeyPair();
			adr.setDestination(RadixAddress.from(Modules.get(Universe.class), key.getPublicKey()).getUID());
			return adr;
		} catch (CryptoException e) {
			throw new IllegalStateException("Can't create discovery request", e);
		}
	}
}
