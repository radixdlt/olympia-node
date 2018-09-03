package com.radixdlt.client.core.network;

import com.radixdlt.client.core.address.EUID;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
	private final long timestamp;
	private final String message;
	private final EUID hid;

	public AtomSubmissionUpdate(EUID hid, AtomSubmissionState state, String message, long timestamp) {
		this.hid = hid;
		this.state = state;
		this.message = message;
		this.timestamp = timestamp;
	}

	public AtomSubmissionState getState() {
		return state;
	}

	public String getMessage() {
		return message;
	}

	public boolean isComplete() {
		return this.getState().isComplete();
	}

	public long getTimestamp() {
		return timestamp;
	}

	public static AtomSubmissionUpdate now(EUID hid, AtomSubmissionState code) {
		return new AtomSubmissionUpdate(hid, code, null, System.currentTimeMillis());
	}

	public static AtomSubmissionUpdate now(EUID hid, AtomSubmissionState code, String message) {
		return new AtomSubmissionUpdate(hid, code, message, System.currentTimeMillis());
	}

	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return sdf.format(new Date(timestamp)) + " atom " + hid + " " + state + (message != null ? ": " + message : "");
	}
}
