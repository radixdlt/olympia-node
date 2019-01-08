package com.radixdlt.client.core.network.actions;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import java.util.Arrays;
import java.util.Objects;

public class SubmitAtomResultAction implements SubmitAtomAction {
	public enum SubmitAtomResultActionType {
		FAILED(NodeAtomSubmissionState.FAILED),
		STORED(NodeAtomSubmissionState.STORED),
		COLLISION(NodeAtomSubmissionState.COLLISION),
		ILLEGAL_STATE(NodeAtomSubmissionState.ILLEGAL_STATE),
		UNSUITABLE_PEER(NodeAtomSubmissionState.UNSUITABLE_PEER),
		VALIDATION_ERROR(NodeAtomSubmissionState.VALIDATION_ERROR),
		UNKNOWN_ERROR(NodeAtomSubmissionState.UNKNOWN_ERROR);

		private final NodeAtomSubmissionState mapsTo;

		SubmitAtomResultActionType(NodeAtomSubmissionState mapsTo) {
			this.mapsTo = mapsTo;
		}

		public static SubmitAtomResultActionType from(NodeAtomSubmissionState nodeAtomSubmissionState) {
			return Arrays.stream(SubmitAtomResultActionType.values())
				.filter(t -> nodeAtomSubmissionState == t.mapsTo)
				.findAny()
				.orElseThrow(() -> new IllegalArgumentException("Unable to find match for " + nodeAtomSubmissionState));
		}
	}

	private final String uuid;
	private final Atom atom;
	private final RadixNode node;
	private final SubmitAtomResultActionType type;
	private final JsonElement data;

	private SubmitAtomResultAction(String uuid, Atom atom, RadixNode node, SubmitAtomResultActionType type, JsonElement data) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(atom);
		Objects.requireNonNull(node);
		Objects.requireNonNull(type);

		this.uuid = uuid;
		this.atom = atom;
		this.node = node;
		this.type = type;
		this.data = data;
	}

	public static SubmitAtomResultAction fromUpdate(String uuid, Atom atom, RadixNode node, NodeAtomSubmissionUpdate update) {
		return new SubmitAtomResultAction(uuid, atom, node, SubmitAtomResultActionType.from(update.getState()), update.getData());
	}

	public String getUuid() {
		return this.uuid;
	}

	public Atom getAtom() {
		return this.atom;
	}

	public SubmitAtomResultActionType getType() {
		return type;
	}

	@Override
	public RadixNode getNode() {
		return this.node;
	}

	public JsonElement getData() {
		return this.data;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_EVENT " + uuid + " " + atom.getHid() + " " + node + " " + type;
	}
}
