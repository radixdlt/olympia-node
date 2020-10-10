package com.radixdlt.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.system.SystemConstraintScrypt;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.Hash;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;

public class SystemTest {
	private RadixEngine<RadixEngineAtom> engine;
	private EngineStore<RadixEngineAtom> store;

	@Before
	public void setup() {
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt());
		ConstraintMachine cm = new ConstraintMachine.Builder()
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			cm,
			cmAtomOS.buildVirtualLayer(),
			store
		);
	}

	@Test
	public void executing_system_update_without_permissions_should_fail() {
		// Arrange
		SystemParticle systemParticle = new SystemParticle(0, 0, 0);
		SystemParticle nextSystemParticle = new SystemParticle(0, 1, 1);
		ImmutableList<CMMicroInstruction> instructions = ImmutableList.of(
			CMMicroInstruction.checkSpin(systemParticle, Spin.UP),
			CMMicroInstruction.push(systemParticle),
			CMMicroInstruction.checkSpin(nextSystemParticle, Spin.NEUTRAL),
			CMMicroInstruction.push(nextSystemParticle),
			CMMicroInstruction.particleGroup()
		);
		CMInstruction instruction = new CMInstruction(
			instructions,
			ImmutableMap.of()
		);

		// Act
		// Assert
		assertThatThrownBy(() -> this.engine.checkAndStore(new BaseAtom(instruction, Hash.ZERO_HASH)))
			.isInstanceOf(RadixEngineException.class)
			.extracting(e -> ((RadixEngineException) e).getCmError().getErrorCode())
			.isEqualTo(CMErrorCode.INVALID_EXECUTION_PERMISSION);
	}

	@Test
	public void executing_system_update_with_correct_permissions_should_succeed() throws RadixEngineException {
		// Arrange
		SystemParticle systemParticle = new SystemParticle(0, 0, 0);
		SystemParticle nextSystemParticle = new SystemParticle(0, 1, 1);
		ImmutableList<CMMicroInstruction> instructions = ImmutableList.of(
			CMMicroInstruction.checkSpin(systemParticle, Spin.UP),
			CMMicroInstruction.push(systemParticle),
			CMMicroInstruction.checkSpin(nextSystemParticle, Spin.NEUTRAL),
			CMMicroInstruction.push(nextSystemParticle),
			CMMicroInstruction.particleGroup()
		);
		CMInstruction instruction = new CMInstruction(
			instructions,
			ImmutableMap.of()
		);

		// Act
		this.engine.checkAndStore(new BaseAtom(instruction, Hash.ZERO_HASH), PermissionLevel.SYSTEM);

		// Assert
		assertThat(this.store.getSpin(nextSystemParticle)).isEqualTo(Spin.UP);
	}
}
