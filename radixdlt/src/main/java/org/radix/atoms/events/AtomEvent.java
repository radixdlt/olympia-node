package org.radix.atoms.events;

import com.radixdlt.atoms.Atom;
import org.radix.common.Syncronicity;
import org.radix.events.Event;

public abstract class AtomEvent extends Event
{
	private final Atom atom;

	public AtomEvent(Atom atom)
	{
		super();

		this.atom = atom;
	}

	public Atom getAtom()
	{
		return atom;
	}

	@Override
	public final boolean supportedSyncronicity(Syncronicity syncronicity)
	{
		if (syncronicity.equals(Syncronicity.ASYNCRONOUS) == false)
			return false;

		return true;
	}
}
