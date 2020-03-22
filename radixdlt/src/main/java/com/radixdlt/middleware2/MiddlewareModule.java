/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.middleware2;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.radixdlt.atommodel.message.MessageParticleConstraintScrypt;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware.AtomCheckHook;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.processing.EngineAtomEventListener;
import com.radixdlt.middleware2.store.LedgerEngineStore;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;
import com.radixdlt.universe.Universe;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.Time;

import java.util.function.UnaryOperator;

public class MiddlewareModule extends AbstractModule {
	private static final Logger log = Logging.getLogger("Middleware");

	@Provides
	@Singleton
	private CMAtomOS buildCMAtomOS(Universe universe) {
		final CMAtomOS os = new CMAtomOS(addr -> {
			final int universeMagic = universe.getMagic() & 0xff;
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

	@Provides
	@Singleton
	private ConstraintMachine buildConstraintMachine(CMAtomOS os) {
		final ConstraintMachine constraintMachine = new ConstraintMachine.Builder()
				.setParticleTransitionProcedures(os.buildTransitionProcedures())
				.setParticleStaticCheck(os.buildParticleStaticCheck())
				.build();
		return constraintMachine;
	}

	@Provides
	private UnaryOperator<CMStore> buildVirtualLayer(CMAtomOS atomOS) {
		return atomOS.buildVirtualLayer();
	}

	@Provides
	@Singleton
	private RadixEngine getRadixEngine(
			ConstraintMachine constraintMachine,
			UnaryOperator<CMStore> virtualStoreLayer,
			EngineStore engineStore,
			Serialization serialization,
			RuntimeProperties properties,
			Universe universe
	) {
		RadixEngine radixEngine = new RadixEngine(
			constraintMachine,
			virtualStoreLayer,
			engineStore
		);

		final boolean skipAtomFeeCheck = properties.get("debug.nopow", false);

		radixEngine.addCMSuccessHook(
				new AtomCheckHook(
						() -> universe,
						Time::currentTimestamp,
						skipAtomFeeCheck,
						Time.MAXIMUM_DRIFT
				)
		);

		radixEngine.addAtomEventListener(new EngineAtomEventListener(serialization));

		return radixEngine;
	}

	@Override
	protected void configure() {
		bind(EngineStore.class).to(LedgerEngineStore.class).in(Scopes.SINGLETON);
		bind(AtomToBinaryConverter.class).toInstance(new AtomToBinaryConverter(Serialization.getDefault()));
	}
}
