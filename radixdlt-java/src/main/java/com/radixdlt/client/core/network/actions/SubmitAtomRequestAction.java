package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class SubmitAtomRequestAction implements SubmitAtomAction, FindANodeAction {
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

	public String getUuid() {
		return uuid;
	}

	public Atom getAtom() {
		return atom;
	}

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
