package org.radix.atoms.events;

import com.radixdlt.common.EUID;
import org.radix.atoms.Atom;
import org.radix.atoms.PreparedAtom;

import java.util.Set;
import java.util.function.Supplier;

public final class AtomStoredEvent extends AtomEventWithDestinations {

	public AtomStoredEvent(Atom atom, Supplier<Set<EUID>> destinationsSupplier) {
		super(atom, destinationsSupplier);
	}

	public AtomStoredEvent(PreparedAtom preparedAtom) {
		super(preparedAtom.getAtom(), null);
		throw new UnsupportedOperationException("PreparedAtom is deprecated");
	}
}
