package org.radix.atoms.events;

import com.radixdlt.atoms.Atom;
import org.radix.exceptions.ExceptionEvent;

public class AtomExceptionEvent extends ExceptionEvent
{
	private final Atom atom;
	
	public AtomExceptionEvent(Throwable throwable, Atom atom)
	{
		super(throwable);
		
		this.atom = atom;
	}

	public Atom getAtom()
	{
		return this.atom;
	}
}
