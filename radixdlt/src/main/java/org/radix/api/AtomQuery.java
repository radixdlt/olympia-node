package org.radix.api;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.EUID;

import java.util.Set;

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

	public boolean filter(Set<EUID> destinations) {
		if (this.destination != null) {
			return destinations.contains(this.destination);
		}

		return true;
	}
}
