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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VertexMetadata;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class CommittedCommandTest {
	private Command command;
	private VertexMetadata vertexMetadata;
	private CommittedCommand committedCommand;

	@Before
	public void setUp() {
		this.command = mock(Command.class);
		this.vertexMetadata = mock(VertexMetadata.class);
		this.committedCommand = new CommittedCommand(command, vertexMetadata);
	}

	@Test
	public void testGetters() {
		assertThat(this.committedCommand.getCommand()).isEqualTo(command);
		assertThat(this.committedCommand.getVertexMetadata()).isEqualTo(vertexMetadata);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(CommittedCommand.class)
			.verify();
	}

}