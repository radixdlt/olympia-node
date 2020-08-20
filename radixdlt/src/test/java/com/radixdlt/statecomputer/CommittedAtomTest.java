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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.utils.TypedMocks;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class CommittedAtomTest {
	private CommittedAtom committedAtom;
	private ClientAtom clientAtom;
	private VertexMetadata vertexMetadata;

	@Before
	public void setUp() {
		this.clientAtom = mock(ClientAtom.class);
		when(clientAtom.getAID()).thenReturn(mock(AID.class));
		when(clientAtom.getCMInstruction()).thenReturn(mock(CMInstruction.class));
		when(clientAtom.getPowFeeHash()).thenReturn(mock(Hash.class));
		when(clientAtom.getMetaData()).thenReturn(TypedMocks.rmock(ImmutableMap.class));
		this.vertexMetadata = mock(VertexMetadata.class);
		this.committedAtom = new CommittedAtom(clientAtom, vertexMetadata);
	}

	@Test
	public void testGetters() {
		assertThat(committedAtom.getClientAtom()).isEqualTo(clientAtom);
		assertThat(committedAtom.getAID()).isEqualTo(clientAtom.getAID());
		assertThat(committedAtom.getCMInstruction()).isEqualTo(clientAtom.getCMInstruction());
		assertThat(committedAtom.getPowFeeHash()).isEqualTo(clientAtom.getPowFeeHash());
		assertThat(committedAtom.getMetaData()).isEqualTo(clientAtom.getMetaData());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(CommittedAtom.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		Assertions.assertThat(committedAtom.toString()).contains(CommittedAtom.class.getSimpleName());
	}
}