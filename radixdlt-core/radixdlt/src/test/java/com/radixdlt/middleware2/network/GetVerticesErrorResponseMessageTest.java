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

package com.radixdlt.middleware2.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

import java.util.Optional;

public class GetVerticesErrorResponseMessageTest {
	@Test
	public void sensibleToString() {
		VerifiedVertex verifiedVertex = mock(VerifiedVertex.class);
		when(verifiedVertex.getView()).thenReturn(View.genesis());
		QuorumCertificate qc = QuorumCertificate.ofGenesis(verifiedVertex, mock(LedgerHeader.class));
		HighQC highQC = HighQC.from(qc, qc, Optional.empty());
		GetVerticesErrorResponseMessage msg1 = new GetVerticesErrorResponseMessage(0, highQC);
		String s1 = msg1.toString();
		assertThat(s1).contains(GetVerticesErrorResponseMessage.class.getSimpleName());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(GetVerticesErrorResponseMessage.class)
				.withIgnoredFields("instance")
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}