package com.radixdlt.client.core.atoms;

public class AtomObservation {
	public enum Type {
		STORE,
		HEAD
	};

	private final Atom atom;
	private final Type type;
	private final long receivedTimestamp;

	private AtomObservation(Atom atom, Type type, long receivedTimestamp) {
		this.atom = atom;
		this.type = type;
		this.receivedTimestamp = receivedTimestamp;
	}

	public Atom getAtom() {
		return atom;
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

	public static AtomObservation storeAtom(Atom atom) {
		return new AtomObservation(atom, Type.STORE, System.currentTimeMillis());
	}

	public static AtomObservation head() {
		return new AtomObservation(null, Type.HEAD, System.currentTimeMillis());
	}
}
