package org.radix.api.observable;

import java.util.stream.Stream;

/**
 * A container of atom events used in the api
 */
public class ObservedAtomEvents {
	private final Stream<AtomEventDto> atomEvents;
	private final boolean isHead;

	ObservedAtomEvents(boolean isHead, Stream<AtomEventDto> atomEvents) {
		this.isHead = isHead;
		this.atomEvents = atomEvents;
	}

	public boolean isHead() {
		return isHead;
	}

	public Stream<AtomEventDto> atomEvents() {
		return atomEvents;
	}
}
