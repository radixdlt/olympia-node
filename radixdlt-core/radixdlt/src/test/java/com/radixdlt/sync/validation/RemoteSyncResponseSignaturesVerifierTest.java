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

package com.radixdlt.sync.validation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.messages.remote.SyncResponse;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncResponseSignaturesVerifierTest {
	private RemoteSyncResponseSignaturesVerifier verifier;
	private Hasher hasher;
	private HashVerifier hashVerifier;

	private SyncResponse response;
	private HashCode headerHash;

	@Before
	public void setup() {
		this.hasher = mock(Hasher.class);
		this.hashVerifier = mock(HashVerifier.class);
		this.verifier = new RemoteSyncResponseSignaturesVerifier(hasher, hashVerifier);

		this.response = mock(SyncResponse.class);
		DtoCommandsAndProof commandsAndProof = mock(DtoCommandsAndProof.class);
		when(response.getCommandsAndProof()).thenReturn(commandsAndProof);
		DtoLedgerHeaderAndProof tail = mock(DtoLedgerHeaderAndProof.class);
		VoteData voteData = mock(VoteData.class);
		when(tail.toVoteData()).thenReturn(voteData);
		when(commandsAndProof.getTail()).thenReturn(tail);
		this.headerHash = mock(HashCode.class);
		when(hasher.hash(any())).thenReturn(headerHash);
		TimestampedECDSASignatures timestampedECDSASignatures = mock(TimestampedECDSASignatures.class);
		BFTNode node = mock(BFTNode.class);
		when(node.getKey()).thenReturn(mock(ECPublicKey.class));
		when(timestampedECDSASignatures.getSignatures()).thenReturn(
			ImmutableMap.of(node, mock(TimestampedECDSASignature.class))
		);
		when(tail.getSignatures()).thenReturn(timestampedECDSASignatures);
	}

	@Test
	public void given_a_valid_response__when_process__then_should_send_valid() {
		when(hashVerifier.verify(any(), eq(headerHash), any())).thenReturn(true);

		assertTrue(this.verifier.verifyResponseSignatures(response));
	}

	@Test
	public void given_an_invalid_response__when_process__then_should_send_invalid() {
		when(hashVerifier.verify(any(), eq(headerHash), any())).thenReturn(false);

        assertFalse(this.verifier.verifyResponseSignatures(response));
	}
}
