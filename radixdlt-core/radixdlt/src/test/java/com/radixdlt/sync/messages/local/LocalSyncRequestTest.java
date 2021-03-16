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

package com.radixdlt.sync.messages.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static com.radixdlt.utils.TypedMocks.rmock;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;

public class LocalSyncRequestTest {
	private LocalSyncRequest request;
	private ImmutableList<BFTNode> targetNodes;
	private VerifiedLedgerHeaderAndProof target;

	@Before
	public void setup() {
		this.targetNodes = rmock(ImmutableList.class);
		this.target = mock(VerifiedLedgerHeaderAndProof.class);
		request = new LocalSyncRequest(target, targetNodes);
	}

	@Test
	public void testGetters() {
		assertThat(request.getTarget()).isEqualTo(target);
		assertThat(request.getTargetNodes()).isEqualTo(targetNodes);
	}

	@Test
	public void sensibleToString() {
		assertThat(request.toString()).contains(LocalSyncRequest.class.getSimpleName());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(LocalSyncRequest.class)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.verify();
	}
}
