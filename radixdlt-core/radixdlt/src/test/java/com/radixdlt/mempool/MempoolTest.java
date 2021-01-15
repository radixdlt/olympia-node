package com.radixdlt.mempool;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.radixdlt.ConsensusModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.DispatcherModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.RadixEngineValidatorComputersModule;
import com.radixdlt.RecoveryModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.MockedCheckpointModule;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicEnvironmentModule;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.DeterministicSavedLastEvent;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.recovery.ModuleForRecoveryTests;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.RadixEngineStateComputer.CommittedAtomSender;
import com.radixdlt.sync.SyncPatienceMillis;
import java.util.List;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class MempoolTest {
	public static Module create() {
		final RadixAddress nativeTokenAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		final RRI nativeToken = RRI.of(nativeTokenAddress, "NOSUCHTOKEN");
		return Modules.combine(
			new AbstractModule() {
				@Override
				public void configure() {
					bindConstant().annotatedWith(Names.named("magic")).to(0);
					bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
					bind(Integer.class).annotatedWith(MaxValidators.class).toInstance(Integer.MAX_VALUE);
					bind(Long.class).annotatedWith(PacemakerTimeout.class).toInstance(1000L);
					bind(Double.class).annotatedWith(PacemakerRate.class).toInstance(2.0);
					bind(Integer.class).annotatedWith(PacemakerMaxExponent.class).toInstance(6);
					bind(RRI.class).annotatedWith(NativeToken.class).toInstance(nativeToken);

					// System
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

					// TODO: Move these into DeterministicSender
					bind(CommittedAtomSender.class).toInstance(atom -> {
					});
				}
			},
			new MockedCheckpointModule(),

			new DeterministicEnvironmentModule(),
			new DispatcherModule(),

			// Consensus
			new CryptoModule(),
			new ConsensusModule(),

			// Ledger
			new LedgerModule(),
			new LedgerCommandGeneratorModule(),

			// Mempool
			new LedgerLocalMempoolModule(1),

			// Sync
			new SyncServiceModule(),

			// Epochs - Consensus
			new EpochsConsensusModule(),
			// Epochs - Ledger
			new EpochsLedgerUpdateModule(),
			// Epochs - Sync
			new EpochsSyncModule(),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineStoreModule(),
			new RadixEngineValidatorComputersModule(),

			// Fees
			new NoFeeModule(),

			new PersistenceModule(),

			new RecoveryModule()
		);
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private DeterministicNetwork network;
	private Injector currentInjector;
	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

	public MempoolTest() {
		this.network = new DeterministicNetwork(
			List.of(BFTNode.create(ecKeyPair.getPublicKey())),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);
	}

	private Injector createRunner(ECKeyPair ecKeyPair) {
		final BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());

		return Guice.createInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(HashSigner.class).toInstance(ecKeyPair::sign);
					bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(ImmutableList.of(self));
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(1));

					final RuntimeProperties runtimeProperties;
					// TODO: this constructor/class/inheritance/dependency is horribly broken
					try {
						runtimeProperties = new RuntimeProperties(new JSONObject(), new String[0]);
						runtimeProperties.set("db.location", folder.getRoot().getAbsolutePath() + "/RADIXDB_RECOVERY_TEST_" + self);
					} catch (ParseException e) {
						throw new IllegalStateException();
					}
					bind(RuntimeProperties.class).toInstance(runtimeProperties);

					Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<Vote>>() { }, ProcessOnDispatch.class)
						.addBinding().to(new TypeLiteral<DeterministicSavedLastEvent<Vote>>() { });
					bind(new TypeLiteral<DeterministicSavedLastEvent<Vote>>() { }).in(Scopes.SINGLETON);
				}
			},
			ModuleForRecoveryTests.create()
		);
	}

	@Before
	public void setup() {
		this.currentInjector = createRunner(ecKeyPair);
		this.currentInjector.getInstance(DeterministicEpochsConsensusProcessor.class).start();
	}

}
