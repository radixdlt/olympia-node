/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.core;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.api.core.model.CoreApiException;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.handlers.NodeSignHandler;
import com.radixdlt.api.core.model.EntityOperation;
import com.radixdlt.api.core.model.NotEnoughNativeTokensForFeesException;
import com.radixdlt.api.core.model.OperationTxBuilder;
import com.radixdlt.api.core.model.ResourceOperation;
import com.radixdlt.api.core.model.TokenResource;
import com.radixdlt.api.core.model.entities.AccountVaultEntity;
import com.radixdlt.api.core.openapitools.model.InvalidTransactionError;
import com.radixdlt.api.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.openapitools.model.PublicKeyNotSupportedError;
import com.radixdlt.api.core.openapitools.model.KeySignRequest;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.deterministic.SingleNodeDeterministicRunner;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NodeSignHandlerTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);

	private final Amount totalTokenAmount = Amount.ofTokens(110);
	private final Amount stakeAmount = Amount.ofTokens(10);

	@Inject
	private NodeSignHandler sut;
	@Inject
	private SingleNodeDeterministicRunner runner;
	@Inject
	private Forks forks;
	@Inject
	private CoreModelMapper mapper;
	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;

	@Before
	public void setup() {
		var injector = Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new MainnetForkConfigsModule(),
			new RadixEngineForksLatestOnlyModule(
				RERulesConfig.testingDefault()
					.overrideFeeTable(FeeTable.create(Amount.ofSubunits(UInt256.ONE), Map.of()))
			),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY),
			new MockedGenesisModule(
				Set.of(TEST_KEY.getPublicKey()),
				totalTokenAmount,
				stakeAmount
			),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bindConstant().annotatedWith(NetworkId.class).to(99);
				}
			}
		);
		injector.injectMembers(this);
	}

	private byte[] buildUnsignedTxn(REAddr from, REAddr to) throws Exception {
		var entityOperationGroups =
			List.of(List.of(
				EntityOperation.from(
					AccountVaultEntity.from(from),
					ResourceOperation.withdraw(
						TokenResource.from("xrd", REAddr.ofNativeToken()),
						UInt256.ONE
					)
				),
				EntityOperation.from(
					AccountVaultEntity.from(to),
					ResourceOperation.deposit(
						TokenResource.from("xrd", REAddr.ofNativeToken()),
						UInt256.ONE
					)
				)
			));
		var operationTxBuilder = new OperationTxBuilder(null, entityOperationGroups, forks);
		var builder = radixEngine.constructWithFees(
			operationTxBuilder, false, from, NotEnoughNativeTokensForFeesException::new
		);
		return builder.buildForExternalSign().blob();
	}

	@Test
	public void sign_should_work_on_correct_transaction() throws Exception {
		// Arrange
		runner.start();

		// Act
		var from = REAddr.ofPubKeyAccount(TEST_KEY.getPublicKey());
		var other = PrivateKeys.ofNumeric(2);
		var to = REAddr.ofPubKeyAccount(other.getPublicKey());
		var unsignedTxn = buildUnsignedTxn(from, to);
		var request = new KeySignRequest()
			.networkIdentifier(new NetworkIdentifier().network("localnet"))
			.publicKey(mapper.publicKey(TEST_KEY.getPublicKey()))
			.unsignedTransaction(Bytes.toHexString(unsignedTxn));
		var response = sut.handleRequest(request);

		// Assert
		assertThat(Bytes.fromHexString(response.getSignedTransaction())).isNotNull();
	}

	@Test
	public void sign_given_an_unsupported_public_key_should_fail() throws Exception {
		// Arrange
		runner.start();

		// Act
		// Assert
		var from = REAddr.ofPubKeyAccount(TEST_KEY.getPublicKey());
		var other = PrivateKeys.ofNumeric(2);
		var to = REAddr.ofPubKeyAccount(other.getPublicKey());
		var unsignedTxn = buildUnsignedTxn(from, to);
		var request = new KeySignRequest()
			.networkIdentifier(new NetworkIdentifier().network("localnet"))
			.publicKey(mapper.publicKey(other.getPublicKey()))
			.unsignedTransaction(Bytes.toHexString(unsignedTxn));

		assertThatThrownBy(() -> sut.handleRequest(request))
			.isInstanceOf(CoreApiException.class)
			.extracting("errorDetails")
			.isInstanceOf(PublicKeyNotSupportedError.class);
	}

	@Test
	public void sign_should_fail_given_an_invalid_transaction() {
		// Arrange
		runner.start();

		// Act
		// Assert
		var request = new KeySignRequest()
			.networkIdentifier(new NetworkIdentifier().network("localnet"))
			.publicKey(mapper.publicKey(TEST_KEY.getPublicKey()))
			.unsignedTransaction("badbadbadbad");

		assertThatThrownBy(() -> sut.handleRequest(request))
			.isInstanceOf(CoreApiException.class)
			.extracting("errorDetails")
			.isInstanceOf(InvalidTransactionError.class);
	}
}
