package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.atoms.AtomEvent.AtomEventType;

public class AtomObservation {
	public enum Type {
		STORE,
		DELETE,
		HEAD;

		public static Type fromAtomEventType(AtomEventType type) {
			if (type == AtomEventType.STORE) {
				return STORE;
			} else if (type == AtomEventType.DELETE) {
				return DELETE;
			}

			throw new IllegalArgumentException(type + " is not a valid type");
		}
	}

	private final Atom atom;
	private final Type type;
	private final long receivedTimestamp;
	private final boolean soft;

	private AtomObservation(Atom atom, Type type, long receivedTimestamp, boolean soft) {
		this.atom = atom;
		this.type = type;
		this.receivedTimestamp = receivedTimestamp;
		this.soft = soft;
	}

	public Atom getAtom() {
		return atom;
	}

	public Type getType() {
		return type;
	}

	public boolean hasAtom() {
		return type == Type.STORE || type == Type.DELETE;
	}

	public boolean isStore() {
		return type == Type.STORE;
	}

	public boolean isHead() {
		return type == Type.HEAD;
	}

	public long getReceivedTimestamp() {
		return receivedTimestamp;
	}

	public static AtomObservation ofEvent(AtomEvent atomEvent) {
		final Type type = Type.fromAtomEventType(atomEvent.getType());
		return new AtomObservation(atomEvent.getAtom(), type, System.currentTimeMillis(), false);
	}

	/**
	 * An atom stored observation marked as soft, meaning that it has been confirmed
	 * to being stored by a server but is not part of the normal fetch atom flow
	 *
	 * @param atom the atom which is soft stored
	 * @return the atom stored observation
	 */
	public static AtomObservation softStored(Atom atom) {
		return new AtomObservation(atom, Type.STORE, System.currentTimeMillis(), true);
	}

	public static AtomObservation stored(Atom atom) {
		return new AtomObservation(atom, Type.STORE, System.currentTimeMillis(), false);
	}

	public boolean isSoft() {
		return soft;
	}

	public static AtomObservation deleted(Atom atom) {
		return new AtomObservation(atom, Type.DELETE, System.currentTimeMillis(), false);
	}

	public static AtomObservation head() {
		return new AtomObservation(null, Type.HEAD, System.currentTimeMillis(), false);
	}

	@Override
	public String toString() {
		return type + " " + atom;
	}
}
