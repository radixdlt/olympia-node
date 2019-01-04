package com.radixdlt.client.core.network;

import com.radixdlt.client.core.network.RadixJsonRpcClient.NodeAtomSubmissionState;
import com.radixdlt.client.core.network.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.atoms.Atom;

public class AtomSubmissionUpdate {
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
	private final RadixPeer node;
	private final long timestamp;
	private final JsonElement data;

	private AtomSubmissionUpdate(Atom atom, AtomSubmissionState state, RadixPeer node, JsonElement data, long timestamp) {
		if (state.requiresNode == (node == null)) {
			throw new IllegalArgumentException("AtomState " + state + " requires " + (state.requiresNode ? "a" : "no") + " node"
				+ " but is " + node);
		}

		this.atom = atom;
		this.state = state;
		this.node = node;
		this.data = data;
		this.timestamp = timestamp;
	}

	public JsonElement getData() {
		return data;
	}

	public RadixPeer getNode() {
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

	public static AtomSubmissionUpdate create(Atom atom, AtomSubmissionState code) {
		return new AtomSubmissionUpdate(atom, code, null, null, System.currentTimeMillis());
	}

	public static AtomSubmissionUpdate fromNodeUpdate(Atom atom, NodeAtomSubmissionUpdate update, RadixPeer node) {
		return new AtomSubmissionUpdate(atom, AtomSubmissionState.fromNodeAtomSubmissionState(update.getState()), node, update.getData(), update.getTimestamp());
	}

	public static AtomSubmissionUpdate create(Atom atom, AtomSubmissionState code, RadixPeer node) {
		return new AtomSubmissionUpdate(atom, code, node, null, System.currentTimeMillis());
	}

	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return sdf.format(new Date(timestamp)) + " atom " + atom.getHid() + " " + state;
	}
}
