/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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

	LogicalClockCursor() {
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
