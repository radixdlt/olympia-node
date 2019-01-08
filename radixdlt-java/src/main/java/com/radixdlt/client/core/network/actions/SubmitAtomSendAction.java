package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Objects;

public class SubmitAtomSendAction implements SubmitAtomAction, RadixNodeAction {
	private final String uuid;
	private final Atom atom;
	private final RadixNode node;

	private SubmitAtomSendAction(String uuid, Atom atom, RadixNode node) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(node);

		this.uuid = uuid;
		this.atom = atom;
		this.node = node;
	}

	public static SubmitAtomSendAction of(String uuid, Atom atom, RadixNode node) {
		return new SubmitAtomSendAction(uuid, atom, node);
	}

	public String getUuid() {
		return uuid;
	}

	public Atom getAtom() {
		return atom;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_SUBSCRIBE " + uuid + " " + atom.getHid() + " " + node;
	}
}
