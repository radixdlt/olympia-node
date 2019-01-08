package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable action which signifies that an atom being submitted was received by the node.
 */
public class SubmitAtomReceivedAction implements SubmitAtomAction {
	private final String uuid;
	private final Atom atom;
	private final RadixNode node;

	private SubmitAtomReceivedAction(String uuid, Atom atom, RadixNode node) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(node);

		this.uuid = uuid;
		this.atom = atom;
		this.node = node;
	}

	public static SubmitAtomReceivedAction of(String uuid, Atom atom, RadixNode node) {
		return new SubmitAtomReceivedAction(uuid, atom, node);
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
		return "SUBMIT_ATOM_RECEIVED " + uuid + " " + atom.getHid() + " " + node;
	}
}
