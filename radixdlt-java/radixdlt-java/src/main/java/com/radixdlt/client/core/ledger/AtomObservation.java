/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.ledger;

import com.radixdlt.atom.Atom;
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
	private final long atomTimestamp;
	private final long receivedTimestamp;
	private final AtomObservationUpdateType updateType;

	private AtomObservation(Atom atom, long atomTimestamp, Type type, long receivedTimestamp, boolean soft) {
		this.atom = atom;
		this.atomTimestamp = atomTimestamp;
		this.receivedTimestamp = receivedTimestamp;
		this.updateType = AtomObservationUpdateType.of(type, soft);
	}

	public Atom getAtom() {
		return atom;
	}

	public long atomTimestamp() {
		return this.atomTimestamp;
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
		return new AtomObservation(atomEvent.getAtom(), atomEvent.timestamp(), type, System.currentTimeMillis(), false);
	}

	/**
	 * An atom stored observation marked as soft, meaning that it has been confirmed
	 * to being stored by a server via a submission but is not part of the normal server fetch
	 * atom flow and so must be handled as "soft state", state which to the clients knowledge
	 * is stored but can easily be replaced by "harder" state.
	 *
	 * @param atom the atom which is soft stored
	 * @param timestamp the atom timestamp supplied by the node
	 * @return the atom stored observation
	 */
	public static AtomObservation softStored(Atom atom, long timestamp) {
		return new AtomObservation(atom, timestamp, Type.STORE, System.currentTimeMillis(), true);
	}

	public static AtomObservation softDeleted(Atom atom) {
		long now = System.currentTimeMillis();
		return new AtomObservation(atom, now, Type.DELETE, now, true);
	}

	public static AtomObservation stored(Atom atom, long atomTimestamp) {
		return new AtomObservation(atom, atomTimestamp, Type.STORE, System.currentTimeMillis(), false);
	}

	public AtomObservationUpdateType getUpdateType() {
		return updateType;
	}

	public static AtomObservation deleted(Atom atom) {
		long now = System.currentTimeMillis();
		return new AtomObservation(atom, now, Type.DELETE, now, false);
	}

	public static AtomObservation head() {
		long now = System.currentTimeMillis();
		return new AtomObservation(null, now, Type.HEAD, now, false);
	}

	@Override
	public String toString() {
		return updateType.type + " " + atom;
	}
}
