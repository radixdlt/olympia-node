package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.Result;
import com.radixdlt.crypto.Hash;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;

public class ConstraintMachineTest {
	@Test
	public void test_invalid_instruction_sequence() {
		ConstraintMachine cm = new ConstraintMachine(
			particle -> Result.success(),
			tt -> null
		);
		ConstraintMachine.CMValidationState validationState = new ConstraintMachine.CMValidationState(
			Hash.ZERO_HASH,
			ImmutableMap.of()
		);
		Assert.assertEquals(Optional.of(CMErrorCode.INVALID_INSTRUCTION_SEQUENCE), cm.validateMicroInstructions(validationState, ImmutableList.of(
			CMMicroInstruction.push(mock(Particle.class))
		)).map(CMError::getErrorCode));
	}
}