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

package com.radixdlt.middleware2.store;

import com.google.inject.Inject;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import java.util.Objects;

public final class CommandToBinaryConverter {
	private final Serialization serializer;

	@Inject
	public CommandToBinaryConverter(Serialization serializer) {
		this.serializer = Objects.requireNonNull(serializer);
	}

	public byte[] toLedgerEntryContent(StoredCommittedCommand command) {
		return serializer.toDson(command, DsonOutput.Output.PERSIST);
	}

	public StoredCommittedCommand toCommand(byte[] ledgerEntryContent) {
		try {
			return serializer.fromDson(ledgerEntryContent, StoredCommittedCommand.class);
		} catch (DeserializeException e) {
			throw new IllegalStateException("Deserialization of Command failed", e);
		}
	}
}
