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

package com.radixdlt.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.radixdlt.sync.RemoteSyncResponseSignaturesVerifier.InvalidSignaturesSender;
import com.radixdlt.sync.RemoteSyncResponseSignaturesVerifier.VerifiedSignaturesSender;
import org.junit.Before;
import org.junit.Test;

public class RemoteSyncResponseSignaturesVerifierTest {
	private RemoteSyncResponseSignaturesVerifier verifier;
	private VerifiedSignaturesSender verifiedSignaturesSender;
	private InvalidSignaturesSender invalidSignaturesSender;
	private Hasher hasher;
	private HashVerifier hashVerifier;

	private RemoteSyncResponse response;
	private HashCode headerHash;

	@Before
	public void setup() {
		this.verifiedSignaturesSender = mock(VerifiedSignaturesSender.class);
		this.invalidSignaturesSender = mock(InvalidSignaturesSender.class);
		this.hasher = mock(Hasher.class);
		this.hashVerifier = mock(HashVerifier.class);
		this.verifier = new RemoteSyncResponseSignaturesVerifier(
			verifiedSignaturesSender,
			invalidSignaturesSender,
			hasher,
			hashVerifier
		);

		this.response = mock(RemoteSyncResponse.class);
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

		this.verifier.processSyncResponse(response);

		verify(verifiedSignaturesSender, times(1)).sendVerified(eq(response));
		verify(invalidSignaturesSender, never()).sendInvalid(any());
	}

	@Test
	public void given_an_invalid_response__when_process__then_should_send_invalid() {
		when(hashVerifier.verify(any(), eq(headerHash), any())).thenReturn(false);

		this.verifier.processSyncResponse(response);

		verify(verifiedSignaturesSender, never()).sendVerified(any());
		verify(invalidSignaturesSender, times(1)).sendInvalid(eq(response));
	}
}