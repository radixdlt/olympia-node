package com.radixdlt.discovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("tempo.sync.iterative.cursor")
public final class LogicalClockCursor {
	public static final LogicalClockCursor INITIAL = new LogicalClockCursor(0, null);

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("position")
	@DsonOutput(DsonOutput.Output.ALL)
	private long logicalClockPosition;

	@JsonProperty("next")
	@DsonOutput(DsonOutput.Output.ALL)
	private LogicalClockCursor next;

	private LogicalClockCursor() {
		// For serializer
	}

	public LogicalClockCursor(long logicalClockPosition, LogicalClockCursor next) {
		this.logicalClockPosition = logicalClockPosition;
		this.next = next;
	}

	public LogicalClockCursor(long logicalClockPosition) {
		this(logicalClockPosition, null);
	}

	public long getLcPosition() {
		return logicalClockPosition;
	}

	public boolean hasNext() {
		return next != null;
	}

	public LogicalClockCursor getNext() {
		return next;
	}

	@Override
	public String toString() {
		return String.format("LCCursor{pos=%d, next=%s}", logicalClockPosition, next == null ? "<none>" : next);
	}
}
