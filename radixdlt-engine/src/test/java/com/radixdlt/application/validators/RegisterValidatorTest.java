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

package com.radixdlt.application.validators;

import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.application.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.application.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RegisterValidatorTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private SubstateSerialization serialization;

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		cmAtomOS.load(new RoundUpdateConstraintScrypt(2));
		cmAtomOS.load(new EpochUpdateConstraintScrypt(2, UInt256.NINE, 1, 1, 100));
		cmAtomOS.load(new ValidatorConstraintScryptV2());
		cmAtomOS.load(new ValidatorRegisterConstraintScrypt());
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		this.serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(RegisterValidator.class, new RegisterValidatorConstructor())
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.build(),
			cm,
			store
		);
		var txn = this.engine.construct(new CreateSystem(0)).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
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
			.hasRootCauseInstanceOf(AuthorizationException.class);
	}
}
