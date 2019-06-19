package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

public final class SubmitAtomCompleteAction implements SubmitAtomAction {
	private final String uuid;
	private final Atom atom;
	private final RadixNode node;

	private SubmitAtomCompleteAction(String uuid, Atom atom, RadixNode node) {
		this.uuid = Objects.requireNonNull(uuid);
		this.atom = Objects.requireNonNull(atom);
		this.node = Objects.requireNonNull(node);
	}

	public static SubmitAtomCompleteAction of(String uuid, Atom atom, RadixNode node) {
		return new SubmitAtomCompleteAction(uuid, atom, node);
	}

	@Override
	public String getUuid() {
		return this.uuid;
	}

	@Override
	public Atom getAtom() {
		return this.atom;
	}

	@Override
	public RadixNode getNode() {
		return this.node;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_COMPLETE " + this.uuid + " " + this.atom.getAid() + " " + this.node;
	}
}
