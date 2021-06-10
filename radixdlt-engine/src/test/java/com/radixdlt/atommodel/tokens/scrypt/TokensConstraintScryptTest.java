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

package com.radixdlt.atommodel.tokens.scrypt;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.StatelessSubstateVerifier;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import org.junit.BeforeClass;
import org.junit.Test;

public class TokensConstraintScryptTest {
	private static StatelessSubstateVerifier<Particle> staticCheck;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScryptV1());
		cmAtomOS.load(new StakingConstraintScryptV1());
		staticCheck = cmAtomOS.buildStatelessSubstateVerifier();
	}

	@Test
	public void when_validating_token_instance_with_null_amount__result_has_error() {
		TokensInAccount tokensInAccount = mock(TokensInAccount.class);
		when(tokensInAccount.getResourceAddr()).thenReturn(mock(REAddr.class));
		when(tokensInAccount.getAmount()).thenReturn(null);
		assertThatThrownBy(() -> staticCheck.verify(tokensInAccount))
			.hasMessageContaining("null");
	}

	@Test
	public void when_validating_staked_token_with_null_amount__result_has_error() {
		PreparedStake staked = mock(PreparedStake.class);
		when(staked.getAmount()).thenReturn(null);
		assertThatThrownBy(() -> staticCheck.verify(staked))
			.hasMessageContaining("null");
	}

	@Test
	public void when_validating_token_instance_with_zero_amount__result_has_error() {
		TokensInAccount tokensInAccount = mock(TokensInAccount.class);
		when(tokensInAccount.getResourceAddr()).thenReturn(mock(REAddr.class));
		when(tokensInAccount.getAmount()).thenReturn(UInt256.ZERO);
		assertThatThrownBy(() -> staticCheck.verify(tokensInAccount))
			.hasMessageContaining("zero");
	}

	@Test
	public void when_validating_staked_token_with_zero_amount__result_has_error() {
		PreparedStake delegatedStake = mock(PreparedStake.class);
		when(delegatedStake.getDelegateKey()).thenReturn(mock(ECPublicKey.class));
		when(delegatedStake.getAmount()).thenReturn(UInt256.ZERO);
		assertThatThrownBy(() -> staticCheck.verify(delegatedStake))
			.hasMessageContaining("zero");
	}

	@Test
	public void when_validating_staked_token_with_null_delegate_address__result_has_error() {
		PreparedStake staked = mock(PreparedStake.class);
		when(staked.getDelegateKey()).thenReturn(null);
		assertThatThrownBy(() -> staticCheck.verify(staked))
			.hasMessageContaining("delegateAddress");
	}
}