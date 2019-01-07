package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.atoms.Atom;
import java.util.UUID;

public class AtomSubmissionUpdate implements RadixNodeAction {
	public enum AtomSubmissionState {
		SEARCHING_FOR_NODE(false, false),
		SUBMITTING(false, true),
		SUBMITTED(false, true),
		FAILED(true, true),
		STORED(true, true),
		COLLISION(true, true),
		ILLEGAL_STATE(true, true),
		UNSUITABLE_PEER(true, true),
		VALIDATION_ERROR(true, true),
		UNKNOWN_ERROR(true, true);

		private boolean isComplete;
		private boolean requiresNode;

		AtomSubmissionState(boolean isComplete, boolean requiresNode) {
			this.isComplete = isComplete;
			this.requiresNode = requiresNode;
		}

		public boolean isComplete() {
			return isComplete;
		}

		public static AtomSubmissionState fromNodeAtomSubmissionState(NodeAtomSubmissionState nodeAtomSubmissionState) {
			return AtomSubmissionState.valueOf(nodeAtomSubmissionState.name());
		}
	}

	private final AtomSubmissionState state;
	private final Atom atom;
	private final RadixNode node;
	private final long timestamp;
	private final JsonElement data;
	private final String uuid;

	private AtomSubmissionUpdate(String uuid, Atom atom, AtomSubmissionState state, RadixNode node, JsonElement data, long timestamp) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(state);

		if (state.requiresNode == (node == null)) {
			throw new IllegalArgumentException("AtomState " + state + " requires " + (state.requiresNode ? "a" : "no") + " node"
				+ " but is " + node);
		}

		this.uuid = uuid;
		this.atom = atom;
		this.state = state;
		this.node = node;
		this.data = data;
		this.timestamp = timestamp;
	}

	public String getUuid() {
		return uuid;
	}

	public JsonElement getData() {
		return data;
	}

	public RadixNode getNode() {
		return node;
	}

	public AtomSubmissionState getState() {
		return state;
	}

	public Atom getAtom() {
		return atom;
	}

	public boolean isComplete() {
		return this.getState().isComplete();
	}

	public long getTimestamp() {
		return timestamp;
	}

	public static AtomSubmissionUpdate searchForNode(Atom atom) {
		return new AtomSubmissionUpdate(
			UUID.randomUUID().toString(),
			atom,
			AtomSubmissionState.SEARCHING_FOR_NODE,
			null,
			null,
			System.currentTimeMillis()
		);
	}

	public static AtomSubmissionUpdate submit(String uuid, Atom atom, RadixNode node) {
		return new AtomSubmissionUpdate(uuid, atom, AtomSubmissionState.SUBMITTING, node, null, System.currentTimeMillis());
	}

	public static AtomSubmissionUpdate update(String uuid, Atom atom, NodeAtomSubmissionUpdate update, RadixNode node) {
		return new AtomSubmissionUpdate(
			uuid,
			atom,
			AtomSubmissionState.fromNodeAtomSubmissionState(update.getState()),
			node,
			update.getData(),
			update.getTimestamp()
		);
	}


	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return sdf.format(new Date(timestamp)) + " atom " + atom.getHid() + " " + state;
	}
}
