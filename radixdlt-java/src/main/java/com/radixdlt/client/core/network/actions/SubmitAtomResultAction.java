package com.radixdlt.client.core.network.actions;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import java.util.Objects;

/**
 * A dispatchable event action which signifies the end result of an atom submission flow
 */
public final class SubmitAtomResultAction implements SubmitAtomAction {
	public enum SubmitAtomResultActionType {
		FAILED,
		STORED,
		COLLISION,
		ILLEGAL_STATE,
		UNSUITABLE_PEER,
		VALIDATION_ERROR,
		UNKNOWN_ERROR;

		public static SubmitAtomResultActionType from(NodeAtomSubmissionState nodeAtomSubmissionState) {
			Objects.requireNonNull(nodeAtomSubmissionState);

			switch (nodeAtomSubmissionState) {
				case FAILED:
					return FAILED;
				case STORED:
					return STORED;
				case COLLISION:
					return COLLISION;
				case ILLEGAL_STATE:
					return ILLEGAL_STATE;
				case UNSUITABLE_PEER:
					return UNSUITABLE_PEER;
				case VALIDATION_ERROR:
					return VALIDATION_ERROR;
				case UNKNOWN_ERROR:
					return UNKNOWN_ERROR;
				case RECEIVED:
					throw new IllegalArgumentException("RECEIVED event is not a terminal result event");
			}

			throw new IllegalArgumentException("Unable to find match for " + nodeAtomSubmissionState);
		}
	}

	private final String uuid;
	private final Atom atom;
	private final RadixNode node;
	private final SubmitAtomResultActionType type;
	private final JsonElement data;

	private SubmitAtomResultAction(String uuid, Atom atom, RadixNode node, SubmitAtomResultActionType type, JsonElement data) {
		this.uuid = Objects.requireNonNull(uuid);
		this.atom = Objects.requireNonNull(atom);
		this.node = Objects.requireNonNull(node);
		this.type = Objects.requireNonNull(type);

		this.data = data;
	}

	public static SubmitAtomResultAction fromUpdate(String uuid, Atom atom, RadixNode node, NodeAtomSubmissionUpdate update) {
		return new SubmitAtomResultAction(uuid, atom, node, SubmitAtomResultActionType.from(update.getState()), update.getData());
	}

	/**
	 * The end result type of the atom submission
	 *
	 * @return The end result type
	 */
	public SubmitAtomResultActionType getType() {
		return this.type;
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

	public JsonElement getData() {
		return this.data;
	}

	@Override
	public String toString() {
		return "SUBMIT_ATOM_RESULT " + this.uuid + " " + this.atom.getHid() + " " + this.node + " " + this.type;
	}
}
