package org.radix.atoms.events;

import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;

import java.util.Set;
import java.util.function.Supplier;

public final class AtomStoredEvent extends AtomEventWithDestinations {

	public AtomStoredEvent(Atom atom, Supplier<Set<EUID>> destinationsSupplier) {
		super(atom, destinationsSupplier);
	}
}
