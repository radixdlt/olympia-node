package com.radixdlt.client.application.translate;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Exception describing an issue occurring when trying to execute a ledger action
 */
public class ActionExecutionException extends RuntimeException {
	private final List<ActionExecutionExceptionReason> reasons;
	private final JsonObject errorData;

	private ActionExecutionException(JsonObject errorData, List<ActionExecutionExceptionReason> reasons) {
		super(reasons != null && !reasons.isEmpty()
			? reasons.toString()
			: String.valueOf(errorData));

		this.errorData = errorData;
		this.reasons = ImmutableList.copyOf(reasons);
	}

	/**
	 * @return The reasons that caused this exception.
	 */
	public List<ActionExecutionExceptionReason> getReasons() {
		return reasons;
	}

	/**
	 * @return The optional JSON representation of the raw error data. May be empty but never null.
	 */
	public JsonObject getErrorData() {
		return errorData != null
			? errorData.deepCopy()
			: new JsonObject();
	}

	public static class ActionExecutionExceptionBuilder {
		private ActionExecutionException built;
		private final List<ActionExecutionExceptionReason> reasons = new ArrayList<>();
		private JsonObject errorData;

		public ActionExecutionExceptionBuilder errorData(JsonObject errorData) {
			this.errorData = errorData.deepCopy();
			return this;
		}

		public ActionExecutionExceptionBuilder addReason(ActionExecutionExceptionReason reason) {
			reasons.add(reason);
			return this;
		}

		public ActionExecutionException build() {
			if (built != null) {
				throw new IllegalStateException("Already built.");
			}

			if (errorData == null) {
				throw new IllegalStateException("JsonObject errorData cannot be null.");
			}

			this.built = new ActionExecutionException(errorData, reasons);
			return built;
		}
	}
}
