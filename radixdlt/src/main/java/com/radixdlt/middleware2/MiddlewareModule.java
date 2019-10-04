package com.radixdlt.middleware2;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.shards.ShardSpace;
import org.radix.time.Time;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware.AtomCheckHook;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import com.radixdlt.middleware2.processing.EngineAtomEventListener;
import com.radixdlt.middleware2.store.LedgerEngineStore;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;

public class MiddlewareModule extends AbstractModule {
	private CMAtomOS buildCMAtomOS() {
		final CMAtomOS os = new CMAtomOS(addr -> {
			final int universeMagic = Modules.get(Universe.class).getMagic() & 0xff;
			if (addr.getMagic() != universeMagic) {
				return Result.error("Address magic " + addr.getMagic() + " does not match universe " + universeMagic);
			}
			return Result.success();
		});
		os.load(new TokensConstraintScrypt());
		os.load(new UniqueParticleConstraintScrypt());
		os.load(new MessageParticleConstraintScrypt());
		return os;
	}

	private ConstraintMachine buildConstraintMachine(CMAtomOS os) {
		final ConstraintMachine constraintMachine = new ConstraintMachine.Builder()
				.setParticleTransitionProcedures(os.buildTransitionProcedures())
				.setParticleStaticCheck(os.buildParticleStaticCheck())
				.build();
		return constraintMachine;
	}

	@Provides
	@Singleton
	private RadixEngine<SimpleRadixEngineAtom> getRadixEngine(
			ConstraintMachine constraintMachine,
			UnaryOperator<CMStore> virtualStoreLayer,
			EngineStore<SimpleRadixEngineAtom> engineStore,
			SimpleRadixEngineAtomToEngineAtom converter
	) {
		RadixEngine<SimpleRadixEngineAtom> radixEngine = new RadixEngine<>(
				constraintMachine,
				virtualStoreLayer,
				engineStore
		);

		final boolean skipAtomFeeCheck = Modules.isAvailable(RuntimeProperties.class)
				&& Modules.get(RuntimeProperties.class).get("debug.nopow", false);

		radixEngine.addCMSuccessHook(
				new AtomCheckHook(
						() -> Modules.get(Universe.class),
						Time::currentTimestamp,
						skipAtomFeeCheck,
						Time.MAXIMUM_DRIFT
				)
		);

		radixEngine.addAtomEventListener(new EngineAtomEventListener());
		radixEngine.start();
		return radixEngine;
	}

	@Override
	protected void configure() {
		CMAtomOS os = buildCMAtomOS();
		ConstraintMachine constraintMachine = buildConstraintMachine(os);

		bind(ConstraintMachine.class).toInstance(constraintMachine);
		bind(new TypeLiteral<UnaryOperator<CMStore>>() {
		}).toInstance(os.buildVirtualLayer());
		bind(new TypeLiteral<EngineStore<SimpleRadixEngineAtom>>() {
		}).to(LedgerEngineStore.class).in(Scopes.SINGLETON);
		bind(new TypeLiteral<Supplier<ShardSpace>>() {
		}).toInstance(() -> LocalSystem.getInstance().getShards());
	}
}
