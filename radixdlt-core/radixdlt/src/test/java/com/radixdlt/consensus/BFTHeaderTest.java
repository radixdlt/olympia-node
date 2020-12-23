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

package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import nl.jqno.equalsverifier.EqualsVerifier;

public class BFTHeaderTest {

	private BFTHeader testObject;
	private HashCode id;
	private LedgerHeader ledgerHeader;

	@Before
	public void setUp() {
		View view = View.of(1234567890L);
		this.id = HashUtils.random256();
		this.ledgerHeader = mock(LedgerHeader.class);
		this.testObject = new BFTHeader(view, id, ledgerHeader);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(BFTHeader.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}

	@Test
	public void testGetters() {
		assertThat(View.of(1234567890L)).isEqualTo(this.testObject.getView());

		assertThat(id).isEqualTo(this.testObject.getVertexId());
		assertThat(ledgerHeader).isEqualTo(this.testObject.getLedgerHeader());
	}

	@Test
	public void testSerializerConstructor() {
		// Don't want to see any exceptions here
		assertThat(new BFTHeader()).isNotNull();
	}

	@Test
	public void testToString() {
		assertThat(this.testObject.toString()).contains(BFTHeader.class.getSimpleName());
	}
}
