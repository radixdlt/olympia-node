package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatusNotification;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

/**
 * A dispatchable event action which signifies the end result of an atom submission flow
 */
public final class SubmitAtomStatusAction implements SubmitAtomAction {
	private final String uuid;
	private final Atom atom;
	private final RadixNode node;
	private final AtomStatusNotification statusNotification;

	private SubmitAtomStatusAction(String uuid, Atom atom, RadixNode node, AtomStatusNotification statusNotification) {
		this.uuid = Objects.requireNonNull(uuid);
		this.atom = Objects.requireNonNull(atom);
		this.node = Objects.requireNonNull(node);
		this.statusNotification = Objects.requireNonNull(statusNotification);
	}

	public static SubmitAtomStatusAction fromStatusNotification(String uuid, Atom atom, RadixNode node, AtomStatusNotification statusNotification) {
		return new SubmitAtomStatusAction(uuid, atom, node, statusNotification);
	}

	/**
	 * The end result type of the atom submission
	 *
	 * @return The end result type
	 */
	public AtomStatusNotification getStatusNotification() {
		return this.statusNotification;
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
		return "SUBMIT_ATOM_RESULT " + this.uuid + " " + this.atom.getAid() + " " + this.node + " " + this.statusNotification;
	}
}
