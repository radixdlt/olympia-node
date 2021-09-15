/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
	public void testAddressBookEntry() {
		EqualsVerifier.forClass(AddressBookEntry.class).suppress(Warning.NULL_FIELDS).verify();
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
	public void testCurrentEpochInfo() {
		EqualsVerifier.forClass(CurrentEpochInfo.class).suppress(Warning.NULL_FIELDS).verify();
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
	public void testEpochInfo() {
		EqualsVerifier.forClass(EpochInfo.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testEpochValidatorData() {
		EqualsVerifier.forClass(EpochValidatorData.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testFeeTable() {
		EqualsVerifier.forClass(FeeTable.class).suppress(Warning.NULL_FIELDS).verify();
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
	public void testForkDetailsConfiguration() {
		EqualsVerifier.forClass(ForkDetailsConfiguration.class).suppress(Warning.NULL_FIELDS).verify();
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
	public void testPerUpSubstateFee() {
		EqualsVerifier.forClass(PerUpSubstateFee.class).suppress(Warning.NULL_FIELDS).verify();
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
	public void testTransactionsDTO() {
		EqualsVerifier.forClass(TransactionsDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTransaction2DTO() {
		EqualsVerifier.forClass(TransactionDTO.class).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	public void testTransactionHistory2() {
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
	public void testUpdates() {
		EqualsVerifier.forClass(Updates.class).suppress(Warning.NULL_FIELDS).verify();
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
