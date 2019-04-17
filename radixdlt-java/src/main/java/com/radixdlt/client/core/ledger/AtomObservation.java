package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomEvent.AtomEventType;

public final class AtomObservation {
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

	/**
	 * Describes the type of observation including whether the update is "soft", or a weakly
	 * supported atom which could possibly be deleted soon
	 */
	public static final class AtomObservationUpdateType {
		private final Type type;
		private final boolean soft;

		private AtomObservationUpdateType(Type type, boolean soft) {
			this.type = type;
			this.soft = soft;
		}

		public static AtomObservationUpdateType of(Type type, boolean soft) {
			return new AtomObservationUpdateType(type, soft);
		}

		public boolean isSoft() {
			return soft;
		}

		public Type getType() {
			return type;
		}
	}

	private final Atom atom;
	private final long receivedTimestamp;
	private final AtomObservationUpdateType updateType;

	private AtomObservation(Atom atom, Type type, long receivedTimestamp, boolean soft) {
		this.atom = atom;
		this.receivedTimestamp = receivedTimestamp;
		this.updateType = new AtomObservationUpdateType(type, soft);
	}

	public Atom getAtom() {
		return atom;
	}

	public Type getType() {
		return updateType.type;
	}

	public boolean hasAtom() {
		return updateType.type == Type.STORE || updateType.type == Type.DELETE;
	}

	public boolean isStore() {
		return updateType.type == Type.STORE;
	}

	public boolean isHead() {
		return updateType.type == Type.HEAD;
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
	 * to being stored by a server via a submission but is not part of the normal server fetch
	 * atom flow and so must be handled as "soft state", state which to the clients knowledge
	 * is stored but can easily be replaced by "harder" state.
	 *
	 * @param atom the atom which is soft stored
	 * @return the atom stored observation
	 */
	public static AtomObservation softStored(Atom atom) {
		return new AtomObservation(atom, Type.STORE, System.currentTimeMillis(), true);
	}

	public static AtomObservation softDeleted(Atom atom) {
		return new AtomObservation(atom, Type.DELETE, System.currentTimeMillis(), true);
	}

	public static AtomObservation stored(Atom atom) {
		return new AtomObservation(atom, Type.STORE, System.currentTimeMillis(), false);
	}

	public AtomObservationUpdateType getUpdateType() {
		return updateType;
	}

	public static AtomObservation deleted(Atom atom) {
		return new AtomObservation(atom, Type.DELETE, System.currentTimeMillis(), false);
	}

	public static AtomObservation head() {
		return new AtomObservation(null, Type.HEAD, System.currentTimeMillis(), false);
	}

	@Override
	public String toString() {
		return updateType.type + " " + atom;
	}
}
