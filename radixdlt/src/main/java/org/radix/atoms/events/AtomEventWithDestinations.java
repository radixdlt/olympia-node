package org.radix.atoms.events;

import com.radixdlt.common.EUID;
import org.radix.atoms.Atom;
import org.radix.atoms.PreparedAtom;

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

	//Method for compilation of legacy code without changes in NodeMassStore and AtomSyncStore
	public PreparedAtom getPreparedAtom() {
		throw new UnsupportedOperationException("PreparedAtom is deprecated");
	}
}
