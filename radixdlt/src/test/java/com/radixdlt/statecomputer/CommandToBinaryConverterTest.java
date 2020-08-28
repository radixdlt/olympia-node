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

package com.radixdlt.statecomputer;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.CommittedCommand;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CommandToBinaryConverterTest {
	private CommandToBinaryConverter commandToBinaryConverter;

	@Before
	public void setUp() {
		commandToBinaryConverter = new CommandToBinaryConverter(DefaultSerialization.getInstance());
	}

	@Test
	public void test_atom_content_transformation_to_byte_array_and_back() {
		PreparedCommand preparedCommand = PreparedCommand.create(0, 0L, false);
		VertexMetadata vertexMetadata = new VertexMetadata(0, View.of(1), Hash.random(), preparedCommand);
		CommittedCommand committedCommand = new CommittedCommand(new Command(new byte[] {0, 1, 2, 3}), vertexMetadata);

		byte[] serializedCommand = commandToBinaryConverter.toLedgerEntryContent(committedCommand);
		CommittedCommand deserializedCommand = commandToBinaryConverter.toCommand(serializedCommand);
		assertEquals(committedCommand, deserializedCommand);
	}
}