package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.atoms.Atom;
import java.util.UUID;

/**
 * An action which is part of a submit atom flow from node search to atom updates
 */
public class SubmitAtomAction implements RadixNodeAction {
	public enum SubmitAtomActionType {
		FIND_A_NODE(false, false, null),
		SUBMIT(false, true, null),
		RECEIVED(false, true, NodeAtomSubmissionState.RECEIVED),
		FAILED(true, true, NodeAtomSubmissionState.FAILED),
		STORED(true, true, NodeAtomSubmissionState.STORED),
		COLLISION(true, true, NodeAtomSubmissionState.COLLISION),
		ILLEGAL_STATE(true, true, NodeAtomSubmissionState.ILLEGAL_STATE),
		UNSUITABLE_PEER(true, true, NodeAtomSubmissionState.UNSUITABLE_PEER),
		VALIDATION_ERROR(true, true, NodeAtomSubmissionState.VALIDATION_ERROR),
		UNKNOWN_ERROR(true, true, NodeAtomSubmissionState.UNKNOWN_ERROR);

		private final boolean isComplete;
		private final boolean requiresNode;
		private final NodeAtomSubmissionState mapsTo;

		SubmitAtomActionType(boolean isComplete, boolean requiresNode, NodeAtomSubmissionState mapsTo) {
			this.isComplete = isComplete;
			this.requiresNode = requiresNode;
			this.mapsTo = mapsTo;
		}

		public boolean isComplete() {
			return isComplete;
		}

		public static SubmitAtomActionType fromNodeAtomSubmissionState(NodeAtomSubmissionState nodeAtomSubmissionState) {
			return Arrays.stream(SubmitAtomActionType.values())
				.filter(t -> nodeAtomSubmissionState == t.mapsTo)
				.findAny()
				.orElseThrow(() -> new IllegalArgumentException("Unable to find match for " + nodeAtomSubmissionState));
		}
	}

	private final SubmitAtomActionType type;
	private final Atom atom;
	private final RadixNode node;
	private final long timestamp;
	private final JsonElement data;
	private final String uuid;

	private SubmitAtomAction(String uuid, Atom atom, SubmitAtomActionType type, RadixNode node, JsonElement data, long timestamp) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(type);

		if (type.requiresNode == (node == null)) {
			throw new IllegalArgumentException("AtomState " + type + " requires " + (type.requiresNode ? "a" : "no") + " node"
				+ " but is " + node);
		}

		this.uuid = uuid;
		this.atom = atom;
		this.type = type;
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

	public SubmitAtomActionType getType() {
		return type;
	}

	public Atom getAtom() {
		return atom;
	}

	public boolean isComplete() {
		return this.getType().isComplete();
	}

	public long getTimestamp() {
		return timestamp;
	}

	public static SubmitAtomAction searchForNode(Atom atom) {
		return new SubmitAtomAction(
			UUID.randomUUID().toString(),
			atom,
			SubmitAtomActionType.FIND_A_NODE,
			null,
			null,
			System.currentTimeMillis()
		);
	}

	public static SubmitAtomAction submit(String uuid, Atom atom, RadixNode node) {
		return new SubmitAtomAction(uuid, atom, SubmitAtomActionType.SUBMIT, node, null, System.currentTimeMillis());
	}

	public static SubmitAtomAction update(String uuid, Atom atom, NodeAtomSubmissionUpdate update, RadixNode node) {
		return new SubmitAtomAction(
			uuid,
			atom,
			SubmitAtomActionType.fromNodeAtomSubmissionState(update.getState()),
			node,
			update.getData(),
			update.getTimestamp()
		);
	}


	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return "SUBMIT_ATOM(" + type + ") " + uuid + " " + atom.getHid() + " " + sdf.format(new Date(timestamp));
	}
}
