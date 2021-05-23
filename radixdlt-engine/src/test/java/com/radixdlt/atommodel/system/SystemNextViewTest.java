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
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atommodel.system.construction.NextViewConstructorV1;
import com.radixdlt.atommodel.system.construction.NextViewConstructorV2;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV1;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public final class SystemNextViewTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{new SystemConstraintScryptV1(), new NextViewConstructorV1()},
			{new SystemConstraintScryptV2(), new NextViewConstructorV2()}
		});
	}

	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private final ConstraintScrypt scrypt;
	private final ActionConstructor<SystemNextView> nextViewConstructor;

	public SystemNextViewTest(ConstraintScrypt scrypt, ActionConstructor<SystemNextView> nextViewConstructor) {
		this.scrypt = scrypt;
		this.nextViewConstructor = nextViewConstructor;
	}

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(scrypt);
		var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.sut = new RadixEngine<>(
			ActionConstructors.newBuilder()
				.put(SystemNextView.class, nextViewConstructor)
				.build(),
			cm,
			store
		);
	}

	@Test
	public void system_update_should_succeed() throws Exception {
		// Arrange
		var txn = sut.construct(new SystemNextView(1, 1, ECKeyPair.generateNew().getPublicKey()))
			.buildWithoutSignature();

		// Act and Assert
		this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER);
	}

	@Test
	public void including_sigs_in_system_update_should_fail() throws Exception {
		// Arrange
		var keyPair = ECKeyPair.generateNew();
		var txn = sut.construct(new SystemNextView(1, 1, ECKeyPair.generateNew().getPublicKey()))
			.signAndBuild(keyPair::sign);

		// Act and Assert
		assertThatThrownBy(() -> this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cmError.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}
}
