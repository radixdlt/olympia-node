package org.radix.atoms.events;

import com.radixdlt.common.AID;
import org.radix.exceptions.ExceptionEvent;

public class AtomExceptionEvent extends ExceptionEvent {
	private final AID atomId;

	public AtomExceptionEvent(Throwable throwable, AID atomId) {
		super(throwable);
		this.atomId = atomId;
	}

	public AID getAtomId() {
		return atomId;
	}
}
