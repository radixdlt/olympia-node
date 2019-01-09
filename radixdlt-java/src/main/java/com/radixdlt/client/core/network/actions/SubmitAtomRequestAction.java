package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The initial dispatchable action to begin an atom submission flow.
 */
public final class SubmitAtomRequestAction implements SubmitAtomAction, FindANodeRequestAction {
	private final String uuid;
	private final Atom atom;

	private SubmitAtomRequestAction(String uuid, Atom atom) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);

		this.uuid = uuid;
		this.atom = atom;
	}

	public static SubmitAtomRequestAction newRequest(Atom atom) {
		return new SubmitAtomRequestAction(UUID.randomUUID().toString(), atom);
	}

	@Override
	public String getUuid() {
		return uuid;
	}

	@Override
	public Atom getAtom() {
		return atom;
	}

	// TODO: Get rid of this method. Maybe create a new RadixNetworkAction interface?
	@Override
	public RadixNode getNode() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Long> getShards() {
		return atom.getRequiredFirstShard();
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_REQUEST " + uuid + " " + atom.getHid();
	}
}
