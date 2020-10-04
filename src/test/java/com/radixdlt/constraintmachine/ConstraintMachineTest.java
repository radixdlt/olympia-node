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

package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.Result;
import com.radixdlt.crypto.HashUtils;
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
			HashUtils.zero256(),
			ImmutableMap.of()
		);
		Assert.assertEquals(Optional.of(CMErrorCode.INVALID_INSTRUCTION_SEQUENCE), cm.validateMicroInstructions(validationState, ImmutableList.of(
			CMMicroInstruction.push(mock(Particle.class))
		)).map(CMError::getErrorCode));
	}
}