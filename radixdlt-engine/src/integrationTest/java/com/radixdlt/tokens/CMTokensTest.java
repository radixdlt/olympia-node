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

package com.radixdlt.tokens;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.application.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.application.tokens.TokenPermission;
import com.radixdlt.application.tokens.TokensConstraintScrypt;
import com.radixdlt.application.tokens.TransferrableTokensParticle;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class CMTokensTest {
	private ConstraintMachine cm;
	private UInt256 granularity;
	private RRI token;
	private Map<TokenTransition, TokenPermission> permissions;

	@Before
	public void setup() {
		// Build the engine based on the constraint machine configured by the AtomOS
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TokensConstraintScrypt());
		this.cm = new ConstraintMachine.Builder().setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures()).build();
		RadixAddress tokenAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		this.token = RRI.of(tokenAddress, "TEST");
		this.granularity = UInt256.ONE;
		this.permissions = ImmutableMap.of(
			TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY,
			TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
		);
	}

	@Test
	public void when_correct_1_to_1_token_send__then_should_not_error() {
		ECKeyPair sender = ECKeyPair.generateNew();
		RadixAddress senderAddress = new RadixAddress((byte) 0, sender.getPublicKey());
		RadixAddress receiverAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());

		TransferrableTokensParticle input = new TransferrableTokensParticle(
			senderAddress,
			UInt256.ONE,
			this.granularity,
			this.token,
			this.permissions
		);

		TransferrableTokensParticle output = new TransferrableTokensParticle(
			receiverAddress,
			UInt256.ONE,
			this.granularity,
			this.token,
			this.permissions
		);

		HashCode witness = HashUtils.random256();

		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(input, Spin.UP),
				CMMicroInstruction.checkSpinAndPush(output, Spin.NEUTRAL),
				CMMicroInstruction.particleGroup()
			),
			ImmutableMap.of(
				sender.euid(), sender.sign(witness)
			)
		);

		Optional<CMError> error = cm.validate(cmInstruction, witness, PermissionLevel.USER);
		assertThat(error).isEmpty();
	}

	@Test
	public void when_correct_2_inputs_to_1_output_token_send__then_should_not_error() {
		ECKeyPair sender = ECKeyPair.generateNew();
		RadixAddress senderAddress = new RadixAddress((byte) 0, sender.getPublicKey());
		RadixAddress receiverAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());

		TransferrableTokensParticle input0 = new TransferrableTokensParticle(
			senderAddress,
			UInt256.ONE,
			this.granularity,
			this.token,
			this.permissions
		);

		TransferrableTokensParticle input1 = new TransferrableTokensParticle(
			senderAddress,
			UInt256.TWO,
			UInt256.ONE,
			this.token,
			this.permissions
		);

		TransferrableTokensParticle output = new TransferrableTokensParticle(
			receiverAddress,
			UInt256.THREE,
			this.granularity,
			this.token,
			this.permissions
		);

		HashCode witness = HashUtils.random256();

		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(input0, Spin.UP),
				CMMicroInstruction.checkSpinAndPush(output, Spin.NEUTRAL),
				CMMicroInstruction.checkSpinAndPush(input1, Spin.UP),
				CMMicroInstruction.particleGroup()
			),
			ImmutableMap.of(
				sender.euid(), sender.sign(witness)
			)
		);

		Optional<CMError> error = cm.validate(cmInstruction, witness, PermissionLevel.USER);
		error.map(CMError::getCmValidationState).ifPresent(System.out::println);
		assertThat(error).isEmpty();
	}


	@Test
	public void when_another_correct_2_inputs_to_1_output_token_send__then_should_not_error() {
		ECKeyPair sender = ECKeyPair.generateNew();
		RadixAddress senderAddress = new RadixAddress((byte) 0, sender.getPublicKey());
		RadixAddress receiverAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());

		TransferrableTokensParticle input0 = new TransferrableTokensParticle(
			senderAddress,
			UInt256.ONE,
			this.granularity,
			this.token,
			this.permissions
		);

		TransferrableTokensParticle input1 = new TransferrableTokensParticle(
			senderAddress,
			UInt256.TWO,
			UInt256.ONE,
			this.token,
			this.permissions
		);

		TransferrableTokensParticle output = new TransferrableTokensParticle(
			receiverAddress,
			UInt256.THREE,
			this.granularity,
			this.token,
			this.permissions
		);

		HashCode witness = HashUtils.random256();

		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(output, Spin.NEUTRAL),
				CMMicroInstruction.checkSpinAndPush(input0, Spin.UP),
				CMMicroInstruction.checkSpinAndPush(input1, Spin.UP),
				CMMicroInstruction.particleGroup()
			),
			ImmutableMap.of(
				sender.euid(), sender.sign(witness)
			)
		);

		Optional<CMError> error = cm.validate(cmInstruction, witness, PermissionLevel.USER);
		error.map(CMError::getCmValidationState).ifPresent(System.out::println);
		assertThat(error).isEmpty();
	}

	@Test
	public void when_correct_1_input_to_2_outputs_token_send__then_should_not_error() {
		ECKeyPair sender = ECKeyPair.generateNew();
		RadixAddress senderAddress = new RadixAddress((byte) 0, sender.getPublicKey());
		RadixAddress receiverAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());

		TransferrableTokensParticle input = new TransferrableTokensParticle(
			senderAddress,
			UInt256.THREE,
			this.granularity,
			this.token,
			this.permissions
		);

		TransferrableTokensParticle output0 = new TransferrableTokensParticle(
			senderAddress,
			UInt256.TWO,
			UInt256.ONE,
			this.token,
			this.permissions
		);

		TransferrableTokensParticle output1 = new TransferrableTokensParticle(
			receiverAddress,
			UInt256.ONE,
			this.granularity,
			this.token,
			this.permissions
		);

		HashCode witness = HashUtils.random256();

		CMInstruction cmInstruction = new CMInstruction(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(input, Spin.UP),
				CMMicroInstruction.checkSpinAndPush(output0, Spin.NEUTRAL),
				CMMicroInstruction.checkSpinAndPush(output1, Spin.NEUTRAL),
				CMMicroInstruction.particleGroup()
			),
			ImmutableMap.of(
				sender.euid(), sender.sign(witness)
			)
		);

		Optional<CMError> error = cm.validate(cmInstruction, witness, PermissionLevel.USER);
		error.map(CMError::getCmValidationState).ifPresent(System.out::println);
		assertThat(error).isEmpty();
	}
}
