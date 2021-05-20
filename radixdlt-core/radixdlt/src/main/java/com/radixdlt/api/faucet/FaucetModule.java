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

package com.radixdlt.api.faucet;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.atom.Substate;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.api.Controller;

import java.util.Set;
import java.util.stream.Collectors;

public class FaucetModule extends AbstractModule {
	@Override
	public void configure() {
		var controllers = Multibinder.newSetBinder(binder(), Controller.class);
		controllers.addBinding().to(FaucetController.class).in(Scopes.SINGLETON);
	}

	@Provides
	@FaucetToken
	@Singleton
	public Set<REAddr> tokens(
		ConstraintMachine cm,
		@Genesis VerifiedTxnsAndProof genesis
	) {
		return genesis.getTxns().stream()
			.flatMap(txn -> {
				try {
					var parsed = cm.statelessVerify(txn);
					return parsed.instructionsParsed().stream()
						.map(REInstruction::getData)
						.filter(Substate.class::isInstance)
						.map(s -> ((Substate) s).getParticle())
						.filter(TokenDefinitionParticle.class::isInstance)
						.map(TokenDefinitionParticle.class::cast)
						.map(TokenDefinitionParticle::getAddr);
				} catch (RadixEngineException e) {
					throw new IllegalStateException(e);
				}
			}).collect(Collectors.toSet());
	}
}
