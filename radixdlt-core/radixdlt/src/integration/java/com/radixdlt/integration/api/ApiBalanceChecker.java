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

package com.radixdlt.integration.api;

import com.google.inject.Inject;
import com.radixdlt.api.core.core.CoreApiException;
import com.radixdlt.api.core.core.CoreModelMapper;
import com.radixdlt.api.core.core.handlers.EngineConfigurationHandler;
import com.radixdlt.api.core.core.handlers.EngineStatusHandler;
import com.radixdlt.api.core.core.handlers.EntityHandler;
import com.radixdlt.api.core.core.handlers.NetworkConfigurationHandler;
import com.radixdlt.api.core.core.openapitools.model.EngineConfigurationRequest;
import com.radixdlt.api.core.core.openapitools.model.EngineStatusRequest;
import com.radixdlt.api.core.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.core.openapitools.model.EntityRequest;
import com.radixdlt.api.core.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.core.openapitools.model.ResourceAmount;
import com.radixdlt.api.core.core.openapitools.model.TokenResourceIdentifier;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.ExitingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiBalanceChecker implements DeterministicActor {
	private static final Logger logger = LogManager.getLogger();

	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;
	@Inject
	private NetworkConfigurationHandler networkConfigurationHandler;
	@Inject
	private EngineStatusHandler engineStatusHandler;
	@Inject
	private EntityHandler entityHandler;
	@Inject
	private EngineConfigurationHandler engineConfigurationHandler;
	@Inject
	private Addressing addressing;
	@Inject
	private CoreModelMapper coreModelMapper;

	private final MultiNodeDeterministicRunner runner;
	private UInt256 lastNativeTokenCount;
	private Long lastEpoch;

	public ApiBalanceChecker(MultiNodeDeterministicRunner runner) {
		this.runner = runner;
	}

	public NetworkIdentifier networkIdentifier() {
		return networkConfigurationHandler.handleRequest((Void) null).getNetworkIdentifier();
	}

	public EpochView getEpochView() throws Exception {
		var statusResponse = engineStatusHandler
			.handleRequest(new EngineStatusRequest().networkIdentifier(networkIdentifier()));
		var epoch = statusResponse.getEngineStateIdentifier().getEpoch();
		var round = statusResponse.getEngineStateIdentifier().getRound();
		return EpochView.of(epoch, View.of(round));
	}

	public UInt256 getRewardsPerProposal() throws Exception {
		var response = this.engineConfigurationHandler.handleRequest(new EngineConfigurationRequest()
			.networkIdentifier(networkIdentifier())
		);
		return UInt256.from(response.getForks().get(0).getEngineConfiguration().getRewardsPerProposal().getValue());
	}

	public long getRoundsPerEpoch() throws Exception {
		var response = this.engineConfigurationHandler.handleRequest(new EngineConfigurationRequest()
			.networkIdentifier(networkIdentifier())
		);
		return response.getForks().get(0).getEngineConfiguration().getMaximumRoundsPerEpoch();
	}

	public long unstakingDelayEpochLength() {
		try {
			return engineConfigurationHandler.handleRequest(new EngineConfigurationRequest()
				.networkIdentifier(new NetworkIdentifier().network("localnet"))
			).getForks().get(0).getEngineConfiguration().getUnstakingDelayEpochLength();
		} catch (CoreApiException e) {
			throw new IllegalStateException(e);
		}
	}

	public TokenResourceIdentifier nativeToken() {
		try {
			return engineConfigurationHandler.handleRequest(new EngineConfigurationRequest()
				.networkIdentifier(new NetworkIdentifier().network("localnet"))
			).getForks().get(0).getEngineConfiguration().getNativeToken();
		} catch (CoreApiException e) {
			throw new IllegalStateException(e);
		}
	}

	public List<ResourceAmount> getAccountBalances(REAddr addr) {
		try {
			var response = entityHandler.handleRequest(new EntityRequest()
				.networkIdentifier(new NetworkIdentifier().network("localnet"))
				.entityIdentifier(new EntityIdentifier().address(addressing.forAccounts().of(addr)))
			);
			return response.getBalances();
		} catch (CoreApiException e) {
			throw new IllegalStateException(e);
		}
	}

	private List<ResourceAmount> getUnstakes(REAddr addr, ECPublicKey validatorKey) {
		var networkIdentifier = new NetworkIdentifier().network("localnet");
		var unstakingDelayEpochLength = unstakingDelayEpochLength();
		var unstakes = new ArrayList<ResourceAmount>();
		try {
			var statusResponse = engineStatusHandler
				.handleRequest(new EngineStatusRequest().networkIdentifier(networkIdentifier));
			var curEpoch = statusResponse.getEngineStateIdentifier().getEpoch();
			var maxEpoch = curEpoch + unstakingDelayEpochLength + 1;

			for (long epochUnstake = curEpoch; epochUnstake <= maxEpoch; epochUnstake++) {
				var response = entityHandler.handleRequest(new EntityRequest()
					.networkIdentifier(networkIdentifier)
					.entityIdentifier(coreModelMapper.entityIdentifierExitingStake(addr, validatorKey, epochUnstake))
				);
				unstakes.addAll(response.getBalances());
			}
		} catch (CoreApiException e) {
			throw new IllegalStateException(e);
		}

		return unstakes;
	}

	public List<ResourceAmount> getAccountUnstakes(REAddr addr) {
		return PrivateKeys.numeric(1).limit(20)
			.map(ECKeyPair::getPublicKey)
			.flatMap(validatorKey -> getUnstakes(addr, validatorKey).stream())
			.collect(Collectors.toList());
	}

	public BigInteger getTotalExittingStake() {
		var totalStakeExitting = radixEngine.read(reader -> reader.reduce(ExitingStake.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
		return new BigInteger(1, totalStakeExitting.toByteArray());
	}

	public BigInteger getTotalTokensInAccounts() {
		var totalTokens = radixEngine.read(reader -> reader.reduce(TokensInAccount.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
		return new BigInteger(1, totalTokens.toByteArray());
	}

	public UInt256 getTotalNativeTokens() {
		var totalTokens = radixEngine.read(reader -> reader.reduce(TokensInAccount.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
		var totalStaked = radixEngine.read(reader -> reader.reduce(ValidatorStakeData.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
		var totalStakePrepared = radixEngine.read(reader -> reader.reduce(PreparedStake.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
		var totalStakeExitting = radixEngine.read(reader -> reader.reduce(ExitingStake.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
		return totalTokens.add(totalStaked).add(totalStakePrepared).add(totalStakeExitting);
	}

	@Override
	public void execute() throws Exception {
		this.runner.getNode(0).injectMembers(this);

		var epochView = getEpochView();
		var epoch = epochView.getEpoch();
		var totalNativeTokenCount = getTotalNativeTokens();
		if (lastEpoch != null) {
			logger.info("total_xrd: {} last_check: {}", Amount.ofSubunits(totalNativeTokenCount), Amount.ofSubunits(lastNativeTokenCount));
			if (epoch - lastEpoch > 1) {
				var numEpochs = epoch - lastEpoch;
				var maxEmissions = UInt256.from(getRoundsPerEpoch())
					.multiply(getRewardsPerProposal())
					.multiply(UInt256.from(numEpochs));
				assertThat(totalNativeTokenCount).isGreaterThan(lastNativeTokenCount);
				var diff = totalNativeTokenCount.subtract(lastNativeTokenCount);
				assertThat(diff).isLessThanOrEqualTo(maxEmissions);
			}
		} else {
			logger.info("total_xrd: {}", Amount.ofSubunits(totalNativeTokenCount));
		}

		lastEpoch = epoch;
		lastNativeTokenCount = totalNativeTokenCount;

		// Check that sum of api balances matches radixEngine numbers
		var totalTokenBalance = PrivateKeys.numeric(1).limit(20)
			.map(ECKeyPair::getPublicKey)
			.map(REAddr::ofPubKeyAccount)
			.flatMap(addr -> getAccountBalances(addr).stream())
			.filter(r -> r.getResourceIdentifier().equals(nativeToken()))
			.map(r -> new BigInteger(r.getValue()))
			.reduce(BigInteger.ZERO, BigInteger::add);
		assertThat(totalTokenBalance).isEqualTo(getTotalTokensInAccounts());

		// Check that sum of api exiting stake balances matches radixEngine numbers
		var totalUnstakingBalance = PrivateKeys.numeric(1).limit(20)
			.map(ECKeyPair::getPublicKey)
			.map(REAddr::ofPubKeyAccount)
			.flatMap(addr -> getAccountUnstakes(addr).stream())
			.filter(r -> r.getResourceIdentifier().equals(nativeToken()))
			.map(r -> new BigInteger(r.getValue()))
			.reduce(BigInteger.ZERO, BigInteger::add);
		assertThat(totalUnstakingBalance).isEqualTo(getTotalExittingStake());
	}
}
