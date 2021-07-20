/* Copyright 2021 Radix DLT Ltd incorporated in England.
 * 
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 * 
 * radixfoundation.org/licenses/LICENSE-v1
 * 
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 * 
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 * 
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 * 
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system 
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 * 
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 * 
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 * 
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 * 
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 * 
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 * 
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 * 
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
