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
import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.Before;
import org.junit.Test;

public class LedgerHeaderTest {
	private LedgerHeader ledgerHeader;
	private long timestamp;
	private Hash commandId;

	@Before
	public void setup() {
		this.timestamp = 12345678L;
		this.commandId = mock(Hash.class);
		this.ledgerHeader = LedgerHeader.create(0, View.genesis(), 12345, commandId, timestamp, false);
	}

	@Test
	public void testGetters() {
		assertThat(ledgerHeader.getStateVersion()).isEqualTo(12345);
		assertThat(ledgerHeader.timestamp()).isEqualTo(timestamp);
		assertThat(ledgerHeader.isEndOfEpoch()).isFalse();
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(LedgerHeader.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		String s = this.ledgerHeader.toString();
		AssertionsForClassTypes.assertThat(s).contains("12345");
	}
}