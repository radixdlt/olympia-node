/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.engine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV1;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

/**
 * Tests BFT System verification logic
 */
public class SystemTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private ECPublicKey key = ECKeyPair.generateNew().getPublicKey();

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScryptV1());
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(parser, ActionConstructors.newBuilder().build(), cm, store);
	}

	@Test
	public void executing_system_update_without_permissions_should_fail() {
		// Arrange
		var systemParticle = new SystemParticle(0, 0, 0);
		var nextSystemParticle = new SystemParticle(0, 1, 1);
		var atom = TxLowLevelBuilder.newBuilder()
			.virtualDown(systemParticle)
			.up(nextSystemParticle)
			.end()
			.build();

		// Act
		// Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(atom)))
			.isInstanceOf(RadixEngineException.class)
			.extracting(Throwable::getCause)
			.extracting(e -> ((ConstraintMachineException) e).getErrorCode())
			.isEqualTo(CMErrorCode.PERMISSION_LEVEL_ERROR);
	}

	@Test
	public void executing_system_update_with_correct_permissions_should_succeed() throws RadixEngineException {
		// Arrange
		var systemParticle = new SystemParticle(0, 0, 0);
		var nextSystemParticle = new SystemParticle(0, 1, 1);
		var atom = TxLowLevelBuilder.newBuilder()
			.virtualDown(systemParticle)
			.up(nextSystemParticle)
			.end()
			.build();

		// Act
		// Assert
		this.engine.execute(List.of(atom), null, PermissionLevel.SUPER_USER);
	}

	@Test
	public void executing_system_update_with_bad_epoch_should_fail() {
		var systemParticle = new SystemParticle(0, 0, 0);
		var nextSystemParticle = new SystemParticle(-1, 1, 1);
		var atom = TxLowLevelBuilder.newBuilder()
			.virtualDown(systemParticle)
			.up(nextSystemParticle)
			.end()
			.build();

		assertThatThrownBy(() -> this.engine.execute(List.of(atom), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void executing_system_update_with_bad_view_should_fail() {
		var systemParticle = new SystemParticle(0, 0, 0);
		var nextSystemParticle = new SystemParticle(0, -1, 1);
		var atom = TxLowLevelBuilder.newBuilder()
			.virtualDown(systemParticle)
			.up(nextSystemParticle)
			.end()
			.build();

		assertThatThrownBy(() -> this.engine.execute(List.of(atom), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void executing_system_update_with_bad_timestamp_should_fail() {
		var systemParticle = new SystemParticle(0, 0, 0);
		var nextSystemParticle = new SystemParticle(0, 1, -1);
		var txn = TxLowLevelBuilder.newBuilder()
			.virtualDown(systemParticle)
			.up(nextSystemParticle)
			.end()
			.build();

		assertThatThrownBy(() -> this.engine.execute(List.of(txn), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void executing_system_update_with_non_increasing_view_should_fail() {
		preconditionFailure(0, 0);
	}

	@Test
	public void executing_system_update_with_overly_increasing_epoch_should_fail() {
		preconditionFailure(3, 0);
	}

	@Test
	public void executing_system_update_with_epoch_starting_at_view_1_should_fail() {
		preconditionFailure(1, 1);
	}

	@Test
	@Ignore("FIXME: Possibly reinstate view ceiling at some point")
	public void executing_system_update_with_view_ceiling_should_fail() {
		// Arrange
		var systemParticle = new SystemParticle(0, 0, 0);
		var nextSystemParticle = new SystemParticle(0, 10, 1);
		var txn = TxLowLevelBuilder.newBuilder()
			.virtualDown(systemParticle)
			.up(nextSystemParticle)
			.end()
			.build();

		// Act
		// Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(txn), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class)
			.extracting(Throwable::getCause)
			.extracting(e -> ((ConstraintMachineException) e).getErrorCode())
			.isEqualTo(CMErrorCode.INVALID_PARTICLE);
	}

	private void preconditionFailure(long epoch, long view) {
		var systemParticle = new SystemParticle(0, 0, 0);
		var nextSystemParticle = new SystemParticle(epoch, view, 1);
		var txn = TxLowLevelBuilder.newBuilder()
			.virtualDown(systemParticle)
			.up(nextSystemParticle)
			.end()
			.build();

		assertThatThrownBy(() -> this.engine.execute(List.of(txn), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class)
			.extracting(Throwable::getCause)
			.extracting(e -> ((ConstraintMachineException) e).getErrorCode())
			.isEqualTo(CMErrorCode.PROCEDURE_ERROR);
	}
}
