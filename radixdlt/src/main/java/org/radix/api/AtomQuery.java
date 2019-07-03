package org.radix.api;

import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.PreparedAtom;
import org.radix.discovery.DiscoveryCursor;
import org.radix.discovery.DiscoveryRequest.Action;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.EUID;

public class AtomQuery {
	private final EUID destination;

	public AtomQuery(RadixAddress address) {
		this.destination = address.getUID();
	}

	public AtomQuery(EUID destination) {
		this.destination = destination;
	}

	public AtomQuery() {
		this.destination = null;
	}

	public EUID getDestination() {
		return this.destination;
	}

	@Override
	public String toString() {
		return "AtomQuery: destination(" + this.destination + ")";
	}

	public AtomDiscoveryRequest toAtomDiscovery() {
		AtomDiscoveryRequest atomDiscoveryRequest = new AtomDiscoveryRequest(null, Action.DISCOVER_AND_DELIVER);
		atomDiscoveryRequest.setCursor(new DiscoveryCursor(0));

		if (this.destination != null) {
			atomDiscoveryRequest.setDestination(this.destination);
		}

		// TODO: get all atoms
		atomDiscoveryRequest.setLimit((short) 50);
		return atomDiscoveryRequest;
	}

	public boolean filter(PreparedAtom atom) {
		if (this.destination != null) {
			return atom.getDestinations().contains(this.destination);
		}

		return true;
	}
}
