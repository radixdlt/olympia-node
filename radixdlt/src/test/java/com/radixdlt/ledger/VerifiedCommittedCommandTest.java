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
import com.radixdlt.consensus.VerifiedCommittedHeader;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class VerifiedCommittedCommandTest {
	private Command command;
	private VerifiedCommittedHeader proof;
	private VerifiedCommittedCommand committedCommand;

	@Before
	public void setUp() {
		this.command = mock(Command.class);
		this.proof = mock(VerifiedCommittedHeader.class);
		this.committedCommand = new VerifiedCommittedCommand(command, proof);
	}

	@Test
	public void testGetters() {
		assertThat(this.committedCommand.getCommand()).isEqualTo(command);
		assertThat(this.committedCommand.getProof()).isEqualTo(proof);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(VerifiedCommittedCommand.class)
			.verify();
	}

}