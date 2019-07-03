package org.radix.atoms.events;

import com.radixdlt.atoms.Atom;

public class AtomUpdateEvent extends AtomEvent
{
	public AtomUpdateEvent(Atom atom)
	{
		super(atom);
	}
}
