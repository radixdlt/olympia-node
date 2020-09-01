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

package com.radixdlt.consensus;

import static org.assertj.core.api.Assertions.assertThat;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Before;
import org.junit.Test;

public class CommandOutputTest {
	private CommandOutput commandOutput;
	private long timestamp;

	@Before
	public void setup() {
		this.timestamp = 12345678L;
		this.commandOutput = CommandOutput.create(12345, timestamp, false);
	}

	@Test
	public void testGetters() {
		assertThat(commandOutput.getStateVersion()).isEqualTo(12345);
		assertThat(commandOutput.timestamp()).isEqualTo(timestamp);
		assertThat(commandOutput.isEndOfEpoch()).isFalse();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(CommandOutput.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		String s = this.commandOutput.toString();
		AssertionsForClassTypes.assertThat(s).contains("12345");
	}
}