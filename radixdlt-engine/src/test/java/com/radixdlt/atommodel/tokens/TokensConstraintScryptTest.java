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

package com.radixdlt.atommodel.tokens;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Optional;
import java.util.function.Function;
import org.junit.BeforeClass;
import org.junit.Test;

public class TokensConstraintScryptTest {
	private static Function<Particle, Result> staticCheck;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());
		cmAtomOS.load(new StakingConstraintScryptV1());
		staticCheck = cmAtomOS.buildParticleStaticCheck();
	}

	@Test
	public void when_validating_token_instance_with_null_amount__result_has_error() {
		TokensParticle tokensParticle = mock(TokensParticle.class);
		when(tokensParticle.getResourceAddr()).thenReturn(mock(REAddr.class));
		when(tokensParticle.getAmount()).thenReturn(null);
		assertThat(staticCheck.apply(tokensParticle).getErrorMessage())
			.contains("null");
	}

	@Test
	public void when_validating_staked_token_with_null_amount__result_has_error() {
		StakedTokensParticle staked = mock(StakedTokensParticle.class);
		when(staked.getAmount()).thenReturn(null);
		assertThat(staticCheck.apply(staked).getErrorMessage())
			.contains("null");
	}

	@Test
	public void when_validating_token_instance_with_zero_amount__result_has_error() {
		TokensParticle tokensParticle = mock(TokensParticle.class);
		when(tokensParticle.getResourceAddr()).thenReturn(mock(REAddr.class));
		when(tokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(tokensParticle).getErrorMessage())
			.contains("zero");
	}

	@Test
	public void when_validating_staked_token_with_zero_amount__result_has_error() {
		StakedTokensParticle stakedTokensParticle = mock(StakedTokensParticle.class);
		when(stakedTokensParticle.getDelegateKey()).thenReturn(mock(ECPublicKey.class));
		when(stakedTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(stakedTokensParticle).getErrorMessage())
			.contains("zero");
	}

	@Test
	public void when_validating_staked_token_with_null_delegate_address__result_has_error() {
		StakedTokensParticle staked = mock(StakedTokensParticle.class);
		when(staked.getDelegateKey()).thenReturn(null);
		assertThat(staticCheck.apply(staked).getErrorMessage())
			.contains("delegateAddress");
	}

	@Test
	public void when_validating_create_transferrable_with_mismatching_supply__result_has_error() {
		TokenDefinitionParticle tokDef = mock(TokenDefinitionParticle.class);
		TokensParticle transferrable = mock(TokensParticle.class);
		when(tokDef.getSupply()).thenReturn(Optional.of(UInt256.FIVE));
		when(transferrable.getAmount()).thenReturn(UInt256.FOUR);
		assertThat(TokensConstraintScrypt.checkCreateTransferrable(tokDef, transferrable).isError()).isTrue();
	}

	@Test
	public void when_validating_create_transferrable__result_has_no_error() {
		TokenDefinitionParticle tokDef = mock(TokenDefinitionParticle.class);
		TokensParticle transferrable = mock(TokensParticle.class);
		when(tokDef.getSupply()).thenReturn(Optional.of(UInt256.FIVE));
		when(transferrable.getAmount()).thenReturn(UInt256.FIVE);
		assertThat(TokensConstraintScrypt.checkCreateTransferrable(tokDef, transferrable).isSuccess()).isTrue();
	}
}