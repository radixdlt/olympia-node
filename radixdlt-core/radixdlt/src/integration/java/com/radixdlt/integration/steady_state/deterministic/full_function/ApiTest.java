package com.radixdlt.integration.steady_state.deterministic.full_function;

import com.google.inject.Module;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.harness.deterministic.ActorConfiguration;
import com.radixdlt.harness.deterministic.DeterministicActorsTest;
import com.radixdlt.harness.deterministic.actors.*;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;

import java.util.List;
import java.util.Map;

public abstract class ApiTest extends DeterministicActorsTest {
    private static final RERulesConfig config =
            RERulesConfig.testingDefault().overrideMaxSigsPerRound(2);
    private static final Amount PER_BYTE_FEE = Amount.ofMicroTokens(2);
    private static final List<ActorConfiguration> ACTOR_CONFIGURATIONS =
            List.of(
                    new ActorConfiguration(ApiTxnSubmitter::new, 1, 2),
                    new ActorConfiguration(BalanceReconciler::new, 1, 10),
                    new ActorConfiguration(RandomNodeRestarter::new, 1, 10),
                    new ActorConfiguration(NativeTokenRewardsChecker::new, 1, 100),
                    new ActorConfiguration(ApiBalanceToRadixEngineChecker::new, 1, 200));

    public ApiTest(Module forkModule, Module byzantineModule) {
        super(forkModule, byzantineModule);
        this.setActorConfigurations(ACTOR_CONFIGURATIONS);
    }

    // The following class is created as a workaround as gradle cannot run the tests inside a test
    // class in parallel. We can achieve some level of parallelism splitting the tests across
    // different test classes.

    public static class ApiTest2 extends ApiTest {
        public ApiTest2() {
            super(new RadixEngineForksLatestOnlyModule(config.overrideMaxRounds(98)), null);
        }
    }


    public static class ApiTest1 extends ApiTest {
        public ApiTest1() {
            super(new RadixEngineForksLatestOnlyModule(config), null);
        }
    }

    public static class ApiTest0 extends ApiTest {
        public ApiTest0() {
            super(
                    new ForkOverwritesWithShorterEpochsModule(config),
                    new ForkOverwritesWithShorterEpochsModule(config.removeSigsPerRoundLimit())
            );
        }
    }


    public static class ApiTest3 extends ApiTest {
        public ApiTest3() {
            super(
                    new RadixEngineForksLatestOnlyModule(
                            config
                                    .overrideMaxRounds(98)
                                    .overrideFeeTable(FeeTable.create(PER_BYTE_FEE, Map.of()))),
                    null);
        }
    }

    public static class ApiTest4 extends ApiTest {
        public ApiTest4() {
            super(
                    new ForkOverwritesWithShorterEpochsModule(
                            config.overrideFeeTable(FeeTable.create(PER_BYTE_FEE, Map.of()))),
                    null);
        }
    }
}
