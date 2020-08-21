/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * A command which has been committed on ledger
 */
@Immutable
@SerializerId2("ledger.committed_command")
public final class CommittedCommand {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("command")
	@DsonOutput(Output.ALL)
	private final Command command;

	@JsonProperty("vertex_metadata")
	@DsonOutput(Output.ALL)
	private final VertexMetadata vertexMetadata;

	@JsonCreator
	public CommittedCommand(
		@JsonProperty("command") Command command,
		@JsonProperty("vertex_metadata") VertexMetadata vertexMetadata
	) {
		this.command = command;
		this.vertexMetadata = Objects.requireNonNull(vertexMetadata);
	}

	public Command getCommand() {
		return command;
	}

	public VertexMetadata getVertexMetadata() {
		return vertexMetadata;
	}

	@Override
	public int hashCode() {
		return Objects.hash(command, vertexMetadata);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CommittedCommand)) {
			return false;
		}

		CommittedCommand other = (CommittedCommand) o;
		return Objects.equals(this.command, other.command)
			&& Objects.equals(this.vertexMetadata, other.vertexMetadata);
	}

	@Override
	public String toString() {
		return String.format("%s{cmd=%s meta=%s}", this.getClass().getSimpleName(), command, vertexMetadata);
	}
}
