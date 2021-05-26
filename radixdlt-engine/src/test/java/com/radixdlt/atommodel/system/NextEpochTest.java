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

package com.radixdlt.atommodel.system;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV1;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV1;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV2;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV1;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.unique.scrypt.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class NextEpochTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{new SystemConstraintScryptV1(), new NextEpochConstructorV1(), new CreateSystemConstructorV1()},
			{new SystemConstraintScryptV2(), new NextEpochConstructorV2(), new CreateSystemConstructorV2()}
		});
	}

	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private final ConstraintScrypt scrypt;
	private final ActionConstructor<SystemNextEpoch> nextEpochConstructor;
	private final ActionConstructor<CreateSystem> createSystemConstructor;

	public NextEpochTest(
		ConstraintScrypt scrypt,
		ActionConstructor<SystemNextEpoch> nextEpochConstructor,
		ActionConstructor<CreateSystem> createSystemConstructor
	) {
		this.scrypt = scrypt;
		this.nextEpochConstructor = nextEpochConstructor;
		this.createSystemConstructor = createSystemConstructor;
	}

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(scrypt);
		cmAtomOS.load(new UniqueParticleConstraintScrypt()); // For v1 start
		var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.sut = new RadixEngine<>(
			ActionConstructors.newBuilder()
				.put(SystemNextEpoch.class, nextEpochConstructor)
				.put(CreateSystem.class, createSystemConstructor)
				.build(),
			cm,
			store
		);
	}

	@Test
	public void next_epoch_should_succeed() throws Exception {
		// Arrange
		var start = sut.construct(new CreateSystem()).buildWithoutSignature();
		sut.execute(List.of(start), null, PermissionLevel.SYSTEM);

		// Act and Assert
		var txn = sut.construct(new SystemNextEpoch(1))
			.buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER);
	}
}
