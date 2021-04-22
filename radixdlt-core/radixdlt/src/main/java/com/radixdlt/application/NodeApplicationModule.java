/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.application;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.chaos.mempoolfiller.MempoolFiller;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.engine.SubstateCacheRegister;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;

/**
 * Module which manages different applications a node can run with
 * it's node key.
 */
public final class NodeApplicationModule extends AbstractModule {
	@Override
	public void configure() {
		bind(MempoolFiller.class).in(Scopes.SINGLETON);
		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?, ?>>() { })
			.addBinding().to(BalanceReducer.class).in(Scopes.SINGLETON);
		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?, ?>>() { })
			.addBinding().to(StakedBalanceReducer.class).in(Scopes.SINGLETON);
		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?, ?>>() { })
			.addBinding().to(StakeReceivedReducer.class).in(Scopes.SINGLETON);
		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?, ?>>() { })
			.addBinding().to(ValidatorInfoReducer.class).in(Scopes.SINGLETON);

		bind(NodeApplication.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(NodeApplicationRequest.class);
		eventBinder.addBinding().toInstance(MempoolAddSuccess.class);
		eventBinder.addBinding().toInstance(MempoolAddFailure.class);
	}

	@ProvidesIntoSet
	private SubstateCacheRegister<?> registeredValidator(@Self ECPublicKey self) {
		return new SubstateCacheRegister<>(ValidatorParticle.class, p -> p.getKey().equals(self));
	}

	@ProvidesIntoSet
	private SubstateCacheRegister<?> registeredSubstate(@Self ECPublicKey self) {
		return new SubstateCacheRegister<>(
			TokensParticle.class,
			p -> p.getAddress().equals(self) && p.getRri().isSystem()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> nodeApplication(NodeApplication nodeApplication) {
		return new EventProcessorOnRunner<>(
			"application",
			NodeApplicationRequest.class,
			nodeApplication.requestEventProcessor()
		);
	}
}
