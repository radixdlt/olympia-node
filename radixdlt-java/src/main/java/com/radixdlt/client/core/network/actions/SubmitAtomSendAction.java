package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Objects;

/**
 * A dispatchable action which signifies to send an atom to a node
 */
public final class SubmitAtomSendAction implements SubmitAtomAction, RadixNodeAction {
	private final String uuid;
	private final Atom atom;
	private final RadixNode node;
	private final boolean completeOnStoreOnly;

	private SubmitAtomSendAction(String uuid, Atom atom, RadixNode node, boolean completeOnStoreOnly) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(node);

		this.uuid = uuid;
		this.atom = atom;
		this.node = node;
		this.completeOnStoreOnly = completeOnStoreOnly;
	}

	public static SubmitAtomSendAction of(String uuid, Atom atom, RadixNode node, boolean completeOnStoreOnly) {
		return new SubmitAtomSendAction(uuid, atom, node, completeOnStoreOnly);
	}

	public boolean isCompleteOnStoreOnly() {
		return completeOnStoreOnly;
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public Atom getAtom() {
		return atom;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_SEND " + uuid + " " + atom.getAid() + " " + node;
	}
}
