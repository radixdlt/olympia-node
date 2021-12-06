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

package com.radixdlt.integration.staking;

import com.google.common.collect.ClassToInstanceMap;
import com.radixdlt.api.core.core.CoreApiException;
import com.radixdlt.api.core.core.CoreModelMapper;
import com.radixdlt.api.core.core.handlers.EngineConfigurationHandler;
import com.radixdlt.api.core.core.handlers.EngineStatusHandler;
import com.radixdlt.api.core.core.handlers.EntityHandler;
import com.radixdlt.api.core.core.openapitools.model.EngineConfigurationRequest;
import com.radixdlt.api.core.core.openapitools.model.EngineStatusRequest;
import com.radixdlt.api.core.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.core.openapitools.model.EntityRequest;
import com.radixdlt.api.core.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.core.openapitools.model.ResourceAmount;
import com.radixdlt.api.core.core.openapitools.model.TokenResourceIdentifier;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.deterministic.LastEventsModule;
import com.radixdlt.integration.FailOnEvent;
import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.store.LastProof;
import com.radixdlt.utils.PrivateKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.util.Modules;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.ExitingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.InvalidProposedTxn;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.utils.UInt256;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class StakingUnstakingValidatorsTest {
	private static final int ACTION_ROUNDS = 2000;
	private static final Logger logger = LogManager.getLogger();
	private static final Amount REWARDS_PER_PROPOSAL = Amount.ofMicroTokens(2307700);
	private static final RERulesConfig config = RERulesConfig.testingDefault().overrideMaxSigsPerRound(2);
	private static final Amount PER_BYTE_FEE = Amount.ofMicroTokens(2);

	@Parameterized.Parameters
	public static Collection<Object[]> forksModule() {
		return List.of(new Object[][]{
			{new RadixEngineForksLatestOnlyModule(config.overrideMaxRounds(100)), 100, null},
			{new ForkOverwritesWithShorterEpochsModule(config), 10, null},
			{
				new ForkOverwritesWithShorterEpochsModule(config), 10,
				new ForkOverwritesWithShorterEpochsModule(config.removeSigsPerRoundLimit())
			},
			{
				new RadixEngineForksLatestOnlyModule(
					config.overrideMaxRounds(100).overrideFeeTable(
						FeeTable.create(
							PER_BYTE_FEE,
							Map.of()
						)
					)
				), 100, null},
			{
				new ForkOverwritesWithShorterEpochsModule(
					config.overrideFeeTable(
						FeeTable.create(
							PER_BYTE_FEE,
							Map.of()
						)
					)
				), 10, null},
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private DeterministicNetwork network;
	private final ImmutableList<ECKeyPair> nodeKeys;
	private final Module radixEngineConfiguration;
	private final Module byzantineModule;
	private final long maxRounds;
	private MultiNodeDeterministicRunner deterministicRunner;

	public StakingUnstakingValidatorsTest(Module forkModule, long maxRounds, Module byzantineModule) {
		this.nodeKeys = PrivateKeys.numeric(1)
			.limit(20)
			.collect(ImmutableList.toImmutableList());
		this.radixEngineConfiguration = Modules.combine(
			new MainnetForkConfigsModule(),
			new ForksModule(),
			forkModule
		);
		this.maxRounds = maxRounds;
		this.byzantineModule = byzantineModule;
	}

	@Before
	public void setup() {
		this.network = new DeterministicNetwork(
			nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList()),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		List<BFTNode> allNodes = nodeKeys.stream()
			.map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());
		var nodeCreators = Streams.mapWithIndex(nodeKeys.stream(), (k, i) ->
			(Supplier<Injector>) () -> createRunner(i == 1, k, allNodes)).collect(Collectors.toList());

		deterministicRunner = new MultiNodeDeterministicRunner(
			nodeCreators,
			this::stopDatabase,
			network
		);
		deterministicRunner.start();
	}

	private void stopDatabase(Injector injector) {
		injector.getInstance(BerkeleyLedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}

	@After
	public void teardown() {
		deterministicRunner.tearDown();
	}

	private Injector createRunner(boolean byzantine, ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		var reConfig = byzantine && byzantineModule != null
			? Modules.override(this.radixEngineConfiguration).with(byzantineModule)
			: this.radixEngineConfiguration;

		return Guice.createInjector(
			new MockedGenesisModule(
				nodeKeys.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet()),
				Amount.ofTokens(100000),
				Amount.ofTokens(1000)
			),
			MempoolConfig.asModule(10, 10),
			reConfig,
			new PersistedNodeForTestingModule(),
			new LastEventsModule(LedgerUpdate.class),
			FailOnEvent.asModule(InvalidProposedTxn.class),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(Environment.class).toInstance(network.createSender(BFTNode.create(ecKeyPair.getPublicKey())));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + ecKeyPair.getPublicKey().toHex());
					bindConstant().annotatedWith(NetworkId.class).to(Network.LOCALNET.getId());
				}

				@Provides
				private PeersView peersView(@Self BFTNode self) {
					return () -> allNodes.stream()
						.filter(n -> !self.equals(n))
						.map(PeersView.PeerInfo::fromBftNode);
				}
			}
		);
	}

	private static class NodeState {
		private final String self;
		private final LedgerProof lastLedgerProof;
		private final RadixEngine<LedgerAndBFTProof> radixEngine;
		private final ClassToInstanceMap<Object> lastEvents;
		private final Forks forks;
		private final EngineStatusHandler engineStatusHandler;
		private final EntityHandler entityHandler;
		private final EngineConfigurationHandler engineConfigurationHandler;
		private final Addressing addressing;
		private final CoreModelMapper coreModelMapper;

		@Inject
		private NodeState(
			@Self String self,
			ClassToInstanceMap<Object> lastEvents,
			@LastProof LedgerProof lastLedgerProof,
			RadixEngine<LedgerAndBFTProof> radixEngine,
			EntityHandler entityHandler,
			EngineStatusHandler engineStatusHandler,
			EngineConfigurationHandler engineConfigurationHandler,
			CoreModelMapper coreModelMapper,
			Addressing addressing,
			Forks forks
		) {
			this.self = self;
			this.lastEvents = lastEvents;
			this.lastLedgerProof = lastLedgerProof;
			this.radixEngine = radixEngine;
			this.entityHandler = entityHandler;
			this.engineStatusHandler = engineStatusHandler;
			this.engineConfigurationHandler = engineConfigurationHandler;
			this.coreModelMapper = coreModelMapper;
			this.addressing = addressing;
			this.forks = forks;
		}

		public String getSelf() {
			return self;
		}

		public long getExpectedNumberOfRounds() {
			var epochView = getEpochView();
			var curEpoch = getEpochView().getEpoch();
			return LongStream.range(1, curEpoch)
				.map(i -> forks.get(i).getMaxRounds().number())
				.sum() + epochView.getView().number();
		}

		public EpochView getEpochView() {
			var lastLedgerUpdate = lastEvents.getInstance(LedgerUpdate.class);
			if (lastLedgerUpdate == null) {
				return lastLedgerProof.getNextValidatorSet().isPresent()
					? EpochView.of(lastLedgerProof.getEpoch() + 1, View.genesis())
					: EpochView.of(lastLedgerProof.getEpoch(), lastLedgerProof.getView());
			}
			var epochChange = lastLedgerUpdate.getStateComputerOutput().getInstance(EpochChange.class);
			if (epochChange != null) {
				return EpochView.of(epochChange.getEpoch(), View.genesis());
			} else {
				var tail = lastLedgerUpdate.getTail();
				return EpochView.of(tail.getEpoch(), tail.getView());
			}
		}

		public Map<BFTNode, Map<String, String>> getValidators() {
			var map = radixEngine.read(reader -> reader.reduce(
				ValidatorStakeData.class,
				new HashMap<BFTNode, Map<String, String>>(),
				(u, s) -> {
					var data = new HashMap<String, String>();
					data.put("stake", Amount.ofSubunits(s.getAmount()).toString());
					data.put("rake", Integer.toString(s.getRakePercentage()));
					u.put(BFTNode.create(s.getValidatorKey()), data);
					return u;
				}
			));

			radixEngine.read(reader -> reader.reduce(
				AllowDelegationFlag.class,
				map,
				(u, flag) -> {
					var data = new HashMap<String, String>();
					data.put("allowDelegation", Boolean.toString(flag.allowsDelegation()));
					u.merge(BFTNode.create(flag.getValidatorKey()), data, (a, b) -> {
						a.putAll(b);
						return a;
					});
					return u;
				}
			));

			return map;
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
				).getNativeToken();
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
			logger.info("Total tokens: {}", Amount.ofSubunits(totalTokens));
			var totalStaked = radixEngine.read(reader -> reader.reduce(ValidatorStakeData.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
			logger.info("Total staked: {}", Amount.ofSubunits(totalStaked));
			var totalStakePrepared = radixEngine.read(reader -> reader.reduce(PreparedStake.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
			logger.info("Total preparing stake: {}", Amount.ofSubunits(totalStakePrepared));
			var totalStakeExitting = radixEngine.read(reader -> reader.reduce(ExitingStake.class, UInt256.ZERO, (u, t) -> u.add(t.getAmount())));
			logger.info("Total exitting stake: {}", Amount.ofSubunits(totalStakeExitting));
			var total = totalTokens.add(totalStaked).add(totalStakePrepared).add(totalStakeExitting);
			logger.info("Total: {}", Amount.ofSubunits(total));
			return total;
		}
	}

	private NodeState reloadNodeState() {
		return deterministicRunner.getNode(0).getInstance(NodeState.class);
	}

	/**
	 * TODO: Figure out why if run for long enough, # of validators
	 * trends to minimum.
	 */
	@Test
	public void stake_unstake_transfers_restarts() throws Exception {
		var initialCount = reloadNodeState().getTotalNativeTokens();

		var random = new Random(12345);

		var randomTransactionSubmitter = new RandomTransactionSubmitter(deterministicRunner, random);

		for (int i = 0; i < ACTION_ROUNDS; i++) {
			deterministicRunner.processForCount(100);

			var next = random.nextInt(16);
			if (next < 8) {
				randomTransactionSubmitter.execute();
			} else if (next < 9) {
				var nodeIndex = random.nextInt(nodeKeys.size());
				deterministicRunner.restartNode(nodeIndex);
				continue;
			}

			deterministicRunner.dispatchToAll(new Key<EventDispatcher<MempoolRelayTrigger>>() {}, MempoolRelayTrigger.create());
			deterministicRunner.dispatchToAll(new Key<EventDispatcher<SyncCheckTrigger>>() {}, SyncCheckTrigger.create());
		}

		var nodeState = reloadNodeState();
		logger.info("Node {}", nodeState.getSelf());
		logger.info("Initial {}", Amount.ofSubunits(initialCount));
		var epochView = nodeState.getEpochView();
		var epoch = epochView.getEpoch();
		var totalRounds = nodeState.getExpectedNumberOfRounds();
		logger.info("Epoch {} Round {} Total Rounds {}", epochView.getEpoch(), epochView.getView().number(), totalRounds);
		var maxEmissions = UInt256.from(maxRounds).multiply(REWARDS_PER_PROPOSAL.toSubunits()).multiply(UInt256.from(epoch - 1));
		logger.info("Max emissions {}", Amount.ofSubunits(maxEmissions));
		if (epoch > 1) {
			var finalCount = nodeState.getTotalNativeTokens();
			assertThat(finalCount).isGreaterThan(initialCount);
			var diff = finalCount.subtract(initialCount);
			logger.info("Difference {}", Amount.ofSubunits(diff));
			assertThat(diff).isLessThanOrEqualTo(maxEmissions);
		}

		var totalTokenBalance = PrivateKeys.numeric(1).limit(20)
			.map(ECKeyPair::getPublicKey)
			.map(REAddr::ofPubKeyAccount)
			.flatMap(addr -> nodeState.getAccountBalances(addr).stream())
			.filter(r -> r.getResourceIdentifier().equals(nodeState.nativeToken()))
			.map(r -> new BigInteger(r.getValue()))
			.reduce(BigInteger.ZERO, BigInteger::add);
		assertThat(totalTokenBalance).isEqualTo(nodeState.getTotalTokensInAccounts());

		var totalUnstakingBalance = PrivateKeys.numeric(1).limit(20)
			.map(ECKeyPair::getPublicKey)
			.map(REAddr::ofPubKeyAccount)
			.flatMap(addr -> nodeState.getAccountUnstakes(addr).stream())
			.filter(r -> r.getResourceIdentifier().equals(nodeState.nativeToken()))
			.map(r -> new BigInteger(r.getValue()))
			.reduce(BigInteger.ZERO, BigInteger::add);
		assertThat(totalUnstakingBalance).isEqualTo(nodeState.getTotalExittingStake());

		for (var e : nodeState.getValidators().entrySet()) {
			logger.info("{} {}", e.getKey(), e.getValue());
		}
	}

}
