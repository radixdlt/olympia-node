/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.lib.dto;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DtoTest {
	@Test
	public void testAccountBalance() {
		EqualsVerifier.forClass(AccountBalance.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testAction() {
		EqualsVerifier.forClass(Action.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testApiConfiguration() {
		EqualsVerifier.forClass(ApiConfiguration.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testApiDataCount() {
		EqualsVerifier.forClass(ApiDataCount.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testApiDataElapsed() {
		EqualsVerifier.forClass(ApiDataElapsed.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testApiData() {
		EqualsVerifier.forClass(ApiData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testApiDbCount() {
		EqualsVerifier.forClass(ApiDbCount.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testApiDbElapsed() {
		EqualsVerifier.forClass(ApiDbElapsed.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testBalance() {
		EqualsVerifier.forClass(Balance.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testBalanceStakes() {
		EqualsVerifier.forClass(BalanceStakes.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testBuiltTransaction() {
		EqualsVerifier.forClass(BuiltTransaction.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testChannelType() {
		EqualsVerifier.forClass(ChannelType.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testCheckpoint() {
		EqualsVerifier.forClass(Checkpoint.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testConsensusConfiguration() {
		EqualsVerifier.forClass(ConsensusConfiguration.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testConsensusData() {
		EqualsVerifier.forClass(ConsensusData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testConsensusDataSync() {
		EqualsVerifier.forClass(ConsensusDataSync.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testCount() {
		EqualsVerifier.forClass(Count.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testDelegatedStake() {
		EqualsVerifier.forClass(DelegatedStake.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testEpochData() {
		EqualsVerifier.forClass(EpochData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testFinalizedTransaction() {
		EqualsVerifier.forClass(FinalizedTransaction.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testForkDetails() {
		EqualsVerifier.forClass(ForkDetails.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testLocalAccount() {
		EqualsVerifier.forClass(LocalAccount.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testLocalValidatorInfo() {
		EqualsVerifier.forClass(LocalValidatorInfo.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testMempoolConfiguration() {
		EqualsVerifier.forClass(MempoolConfiguration.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testMempoolDataErrors() {
		EqualsVerifier.forClass(MempoolDataErrors.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testMempoolData() {
		EqualsVerifier.forClass(MempoolData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkChannel() {
		EqualsVerifier.forClass(NetworkChannel.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkConfiguration() {
		EqualsVerifier.forClass(NetworkConfiguration.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkData() {
		EqualsVerifier.forClass(NetworkData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkDataMessagesInbound() {
		EqualsVerifier.forClass(NetworkDataMessagesInbound.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkDataMessages() {
		EqualsVerifier.forClass(NetworkDataMessages.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkDataMessagesOutbound() {
		EqualsVerifier.forClass(NetworkDataMessagesOutbound.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkDataNetworking() {
		EqualsVerifier.forClass(NetworkDataNetworking.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkDataNetworkingTcp() {
		EqualsVerifier.forClass(NetworkDataNetworkingTcp.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkDataNetworkingUdp() {
		EqualsVerifier.forClass(NetworkDataNetworkingUdp.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkId() {
		EqualsVerifier.forClass(NetworkId.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkPeer() {
		EqualsVerifier.forClass(NetworkPeer.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testNetworkStats() {
		EqualsVerifier.forClass(NetworkStats.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testProofHeader() {
		EqualsVerifier.forClass(ProofHeader.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testProof() {
		EqualsVerifier.forClass(Proof.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testRadixEngineData() {
		EqualsVerifier.forClass(RadixEngineData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testReadWrite() {
		EqualsVerifier.forClass(ReadWrite.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testReadWriteStats() {
		EqualsVerifier.forClass(ReadWriteStats.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testSignatureDetails() {
		EqualsVerifier.forClass(SignatureDetails.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testSize() {
		EqualsVerifier.forClass(Size.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testStakePositions() {
		EqualsVerifier.forClass(StakePositions.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testSyncConfiguration() {
		EqualsVerifier.forClass(SyncConfiguration.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testSyncData() {
		EqualsVerifier.forClass(SyncData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTimeDTO() {
		EqualsVerifier.forClass(TimeDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTokenBalances() {
		EqualsVerifier.forClass(TokenBalances.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTokenInfo() {
		EqualsVerifier.forClass(TokenInfo.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTransactionDTO() {
		EqualsVerifier.forClass(TransactionDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTransactionHistory() {
		EqualsVerifier.forClass(TransactionHistory.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTransactionStatusDTO() {
		EqualsVerifier.forClass(TransactionStatusDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTransactionStatus() {
		EqualsVerifier.forClass(TransactionStatus.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTxBlobDTO() {
		EqualsVerifier.forClass(TxBlobDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTxBlob() {
		EqualsVerifier.forClass(TxBlob.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTxDTO() {
		EqualsVerifier.forClass(TxDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testUnstakePositions() {
		EqualsVerifier.forClass(UnstakePositions.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testValidatorDTO() {
		EqualsVerifier.forClass(ValidatorDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testValidatorEntry() {
		EqualsVerifier.forClass(ValidatorEntry.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testValidatorsResponse() {
		EqualsVerifier.forClass(ValidatorsResponse.class).suppress(Warning.NULL_FIELDS).verify();
	}
}
