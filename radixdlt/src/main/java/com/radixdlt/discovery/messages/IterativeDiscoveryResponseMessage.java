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

package com.radixdlt.discovery.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.discovery.LogicalClockCursor;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.discovery.iterative.response")
public class IterativeDiscoveryResponseMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableList<AID> aids;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LogicalClockCursor cursor;

	IterativeDiscoveryResponseMessage() {
		// Serializer only
		super(0);
		aids = ImmutableList.of();
	}

	public IterativeDiscoveryResponseMessage(ImmutableList<AID> aids, LogicalClockCursor cursor, int magic) {
		super(magic);
		this.aids = aids;
		this.cursor = cursor;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}
}
