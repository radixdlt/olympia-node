package com.radixdlt.client.core.network;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.atoms.Atom;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class AtomSubmissionUpdate {
	public enum AtomSubmissionState {
		SUBMITTING(false),
		SUBMITTED(false),
		FAILED(true),
		STORED(true),
		COLLISION(true),
		ILLEGAL_STATE(true),
		UNSUITABLE_PEER(true),
		VALIDATION_ERROR(true),
		UNKNOWN_ERROR(true);

		private boolean isComplete;

		AtomSubmissionState(boolean isComplete) {
			this.isComplete = isComplete;
		}

		public boolean isComplete() {
			return isComplete;
		}
	}

	private final AtomSubmissionState state;
	private final JsonElement data;
	private final Atom atom;
	private final Map<String, Object> metaData = new HashMap<>();
	private final long timestamp;

	private AtomSubmissionUpdate(Atom atom, AtomSubmissionState state, JsonElement data) {
		this.atom = atom;
		this.state = state;
		this.data = data;
		this.timestamp = System.currentTimeMillis();
	}

	public AtomSubmissionState getState() {
		return state;
	}

	public Atom getAtom() {
		return atom;
	}

	public JsonElement getData() {
		return data;
	}

	public boolean isComplete() {
		return this.getState().isComplete();
	}

	public long getTimestamp() {
		return timestamp;
	}

	public AtomSubmissionUpdate putMetaData(String key, Object value) {
		metaData.put(key, value);
		return this;
	}

	public Object getMetaData(String key) {
		return metaData.get(key);
	}

	public Map<String, Object> getMetaData() {
		return Collections.unmodifiableMap(metaData);
	}

	public static AtomSubmissionUpdate create(Atom atom, AtomSubmissionState code) {
		return new AtomSubmissionUpdate(atom, code, null);
	}

	public static AtomSubmissionUpdate create(Atom atom, AtomSubmissionState code, JsonElement data) {
		return new AtomSubmissionUpdate(atom, code, data);
	}

	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return sdf.format(new Date(timestamp)) + " atom " + atom.getHid() + " " + state + (data != null ? ": " + data : "");
	}
}
