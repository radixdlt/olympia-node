package com.radixdlt.tempo.sync;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("tempo.sync.iterative.cursor")
public final class IterativeCursor {
	public static final IterativeCursor INITIAL = new IterativeCursor(0, null);

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("position")
	@DsonOutput(DsonOutput.Output.ALL)
	private long logicalClockPosition;

	@JsonProperty("next")
	@DsonOutput(DsonOutput.Output.ALL)
	private IterativeCursor next;

	private IterativeCursor() {
		// For serializer
	}

	public IterativeCursor(long logicalClockPosition, IterativeCursor next) {
		this.logicalClockPosition = logicalClockPosition;
		this.next = next;
	}

	public long getLogicalClockPosition() {
		return logicalClockPosition;
	}

	public boolean hasNext() {
		return next != null;
	}

	public IterativeCursor getNext() {
		return next;
	}
}
