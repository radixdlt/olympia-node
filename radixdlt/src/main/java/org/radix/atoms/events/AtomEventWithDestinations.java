package org.radix.atoms.events;

import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;

import java.util.Set;
import java.util.function.Supplier;

public class AtomEventWithDestinations extends AtomEvent {
	private Set<EUID> destinations;

	public AtomEventWithDestinations(Atom atom, Supplier<Set<EUID>> destinationsSupplier) {
		super(atom);
		this.destinations = destinationsSupplier.get();
	}

	public Set<EUID> getDestinations() {
		return destinations;
	}

}
