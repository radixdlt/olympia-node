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

package com.radixdlt.atommodel.validators;

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atommodel.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RegisterValidatorTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildStatelessSubstateVerifier());
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			ActionConstructors.newBuilder()
				.put(RegisterValidator.class, new RegisterValidatorConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void register_validator() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();

		// Act and Assert
		var registerTxn = this.engine.construct(new RegisterValidator(key.getPublicKey()))
			.signAndBuild(key::sign);
		this.engine.execute(List.of(registerTxn));
	}

	@Test
	public void register_other_validator_should_fail() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();

		// Act and Assert
		var registerTxn = this.engine.construct(new RegisterValidator(ECKeyPair.generateNew().getPublicKey()))
			.signAndBuild(key::sign);
		assertThatThrownBy(() -> this.engine.execute(List.of(registerTxn)))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cause.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}

	@Test
	public void changing_validator_key_should_fail() {
		// Arrange
		var key = ECKeyPair.generateNew();
		var builder = TxLowLevelBuilder.newBuilder()
			.virtualDown(new ValidatorParticle(key.getPublicKey(), false))
			.up(new ValidatorParticle(ECKeyPair.generateNew().getPublicKey(), true))
			.end();
		var sig = key.sign(builder.hashToSign().asBytes());
		var txn = builder.sig(sig).build();

		// Act and Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cause.errorCode")
			.containsExactly(CMErrorCode.PROCEDURE_ERROR);
	}
}
