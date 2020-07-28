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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.WitnessData;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

public class TokensConstraintScryptTest {
	private static Function<Particle, Result> staticCheck;

	@BeforeClass
	public static void initializeConstraintScrypt() {
		TokensConstraintScrypt tokensConstraintScrypt = new TokensConstraintScrypt();
		CMAtomOS cmAtomOS = new CMAtomOS();
		cmAtomOS.load(tokensConstraintScrypt);
		staticCheck = cmAtomOS.buildParticleStaticCheck();
	}

	@Test
	public void when_validating_mutable_token_def_particle_with_no_rri__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = mock(MutableSupplyTokenDefinitionParticle.class);
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("rri cannot be null");
	}

	@Test
	public void when_validating_fixed_token_def_particle_with_no_rri__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = mock(FixedSupplyTokenDefinitionParticle.class);
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("rri cannot be null");
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEEEEEEEEEEEEEEEEEEEEEEEEEEST"));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_with_too_short_symbol__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), ""));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_fixed_token_class_particle_with_too_long_symbol__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = PowerMockito.mock(FixedSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEEEEEEEEEEEEEEEEEEEEEEEEEEST"));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_fixed_token_class_particle_with_too_short_symbol__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = PowerMockito.mock(FixedSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), ""));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Symbol: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_without_description__result_is_success() {
		RadixAddress addr = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> perms = ImmutableMap.of(
			MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.ALL,
			MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL
		);
		MutableSupplyTokenDefinitionParticle token = new MutableSupplyTokenDefinitionParticle(
			addr,
			"TOK",
			null,
			null,
			UInt256.ONE,
			null,
			null,
			perms
		);
		assertTrue(staticCheck.apply(token).isSuccess());
	}

	@Test
	public void when_validating_token_class_particle_with_empty_description__result_is_success() {
		RadixAddress addr = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
		ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> perms = ImmutableMap.of(
			MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.ALL,
			MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL
		);
		MutableSupplyTokenDefinitionParticle token = new MutableSupplyTokenDefinitionParticle(
			addr,
			"TOK",
			"",
			"",
			UInt256.ONE,
			null,
			null,
			perms
		);
		assertTrue(staticCheck.apply(token).isSuccess());
	}

	@Test
	public void when_validating_token_class_particle_with_too_long_description__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEST"));
		when(token.getDescription()).thenReturn(
			IntStream.range(0, TokenDefinitionUtils.MAX_DESCRIPTION_LENGTH + 1).mapToObj(i -> "c").collect(Collectors.joining()));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Description: invalid length");
	}

	@Test
	public void when_validating_token_class_particle_without_permissions_set__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getTokenPermissions()).thenReturn(new HashMap<>());
		when(token.getDescription()).thenReturn("Hello");
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Permissions: must be set");
	}

	@Test
	public void when_validating_token_class_particle_with_invalid_icon_url__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getDescription()).thenReturn("Hello");
		when(token.getTokenPermissions()).thenReturn(ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.ALL,
			TokenTransition.BURN, TokenPermission.ALL
		));
		when(token.getIconUrl()).thenReturn("this is not a url");
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Icon: not a valid URL");
	}

	@Test
	public void when_validating_token_class_particle_with_invalid_url__result_has_error() {
		MutableSupplyTokenDefinitionParticle token = PowerMockito.mock(MutableSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getDescription()).thenReturn("Hello");
		when(token.getTokenPermissions()).thenReturn(ImmutableMap.of(
			TokenTransition.MINT, TokenPermission.ALL,
			TokenTransition.BURN, TokenPermission.ALL
		));
		when(token.getUrl()).thenReturn("this is not a url");
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("not a valid URL");
	}

	@Test
	public void when_validating_fixed_token_class_particle_with_too_long_description__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = PowerMockito.mock(FixedSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TEST"));
		when(token.getDescription()).thenReturn(
			IntStream.range(0, TokenDefinitionUtils.MAX_DESCRIPTION_LENGTH + 1).mapToObj(i -> "c").collect(Collectors.joining()));
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Description: invalid length");
	}

	@Test
	public void when_validating_fixed_token_class_particle_with_invalid_icon_url__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = PowerMockito.mock(FixedSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getDescription()).thenReturn("Hello");
		when(token.getIconUrl()).thenReturn("this is not a url");
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("Icon: not a valid URL");
	}

	@Test
	public void when_validating_fixed_token_class_particle_with_invalid_url__result_has_error() {
		FixedSupplyTokenDefinitionParticle token = PowerMockito.mock(FixedSupplyTokenDefinitionParticle.class);
		when(token.getRRI()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(token.getDescription()).thenReturn("Hello");
		when(token.getUrl()).thenReturn("this is not a url");
		assertThat(staticCheck.apply(token).getErrorMessage())
			.contains("not a valid URL");
	}

	@Test
	public void when_validating_token_instance_with_null_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(null);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("null");
	}

	@Test
	public void when_validating_unallocated_token_with_null_amount__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(null);
		assertThat(staticCheck.apply(unallocated).getErrorMessage())
			.contains("null");
	}

	@Test
	public void when_validating_staked_token_with_null_amount__result_has_error() {
		StakedTokensParticle staked = mock(StakedTokensParticle.class);
		when(staked.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(staked.getAmount()).thenReturn(null);
		assertThat(staticCheck.apply(staked).getErrorMessage())
			.contains("null");
	}

	@Test
	public void when_validating_token_instance_with_zero_amount__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("zero");
	}

	@Test
	public void when_validating_unallocated_token_with_zero_amount__result_has_error() {
		UnallocatedTokensParticle burnedTokensParticle = mock(UnallocatedTokensParticle.class);
		when(burnedTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(burnedTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(burnedTokensParticle).getErrorMessage())
			.contains("zero");
	}

	@Test
	public void when_validating_staked_token_with_zero_amount__result_has_error() {
		StakedTokensParticle stakedTokensParticle = mock(StakedTokensParticle.class);
		when(stakedTokensParticle.getDelegateAddress()).thenReturn(mock(RadixAddress.class));
		when(stakedTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(stakedTokensParticle.getAmount()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(stakedTokensParticle).getErrorMessage())
			.contains("zero");
	}

	@Test
	public void when_validating_token_instance_with_null_granularity__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.ONE);
		when(transferrableTokensParticle.getGranularity()).thenReturn(null);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_unallocated_token_with_null_granularity__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(UInt256.ONE);
		when(unallocated.getGranularity()).thenReturn(null);
		assertThat(staticCheck.apply(unallocated).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_staked_token_with_null_granularity__result_has_error() {
		StakedTokensParticle staked = mock(StakedTokensParticle.class);
		when(staked.getDelegateAddress()).thenReturn(mock(RadixAddress.class));
		when(staked.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(staked.getAmount()).thenReturn(UInt256.ONE);
		when(staked.getGranularity()).thenReturn(null);
		assertThat(staticCheck.apply(staked).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_unallocated_token_with_zero_granularity__result_has_error() {
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(unallocated.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(unallocated.getAmount()).thenReturn(UInt256.ONE);
		when(unallocated.getGranularity()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(unallocated).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_token_instance_with_amount_not_divisible_by_granularity__result_has_error() {
		TransferrableTokensParticle transferrableTokensParticle = mock(TransferrableTokensParticle.class);
		when(transferrableTokensParticle.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(transferrableTokensParticle.getAmount()).thenReturn(UInt256.THREE);
		when(transferrableTokensParticle.getGranularity()).thenReturn(UInt256.TWO);
		assertThat(staticCheck.apply(transferrableTokensParticle).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_staked_token_with_zero_granularity__result_has_error() {
		StakedTokensParticle staked = mock(StakedTokensParticle.class);
		when(staked.getDelegateAddress()).thenReturn(mock(RadixAddress.class));
		when(staked.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		when(staked.getAmount()).thenReturn(UInt256.ONE);
		when(staked.getGranularity()).thenReturn(UInt256.ZERO);
		assertThat(staticCheck.apply(staked).getErrorMessage())
			.contains("granularity");
	}

	@Test
	public void when_validating_staked_token_with_null_delegate_address__result_has_error() {
		StakedTokensParticle staked = mock(StakedTokensParticle.class);
		when(staked.getDelegateAddress()).thenReturn(null);
		when(staked.getTokDefRef()).thenReturn(RRI.of(mock(RadixAddress.class), "TOK"));
		assertThat(staticCheck.apply(staked).getErrorMessage())
			.contains("delegateAddress");
	}

	@Test
	public void when_validating_create_transferrable_with_mismatching_granularities__result_has_error() {
		FixedSupplyTokenDefinitionParticle tokDef = mock(FixedSupplyTokenDefinitionParticle.class);
		TransferrableTokensParticle transferrable = mock(TransferrableTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(transferrable.getGranularity()).thenReturn(UInt256.FOUR);
		assertThat(TokensConstraintScrypt.checkCreateTransferrable(tokDef, transferrable).isError()).isTrue();
	}

	@Test
	public void when_validating_create_transferrable_with_mismatching_supply__result_has_error() {
		FixedSupplyTokenDefinitionParticle tokDef = mock(FixedSupplyTokenDefinitionParticle.class);
		TransferrableTokensParticle transferrable = mock(TransferrableTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(transferrable.getGranularity()).thenReturn(UInt256.FIVE);
		when(tokDef.getSupply()).thenReturn(UInt256.FIVE);
		when(transferrable.getAmount()).thenReturn(UInt256.FOUR);
		assertThat(TokensConstraintScrypt.checkCreateTransferrable(tokDef, transferrable).isError()).isTrue();
	}

	@Test
	public void when_validating_create_transferrable_with_non_empty_permissions__result_has_error() {
		FixedSupplyTokenDefinitionParticle tokDef = mock(FixedSupplyTokenDefinitionParticle.class);
		TransferrableTokensParticle transferrable = mock(TransferrableTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(transferrable.getGranularity()).thenReturn(UInt256.FIVE);
		when(tokDef.getSupply()).thenReturn(UInt256.FIVE);
		when(transferrable.getAmount()).thenReturn(UInt256.FIVE);
		when(transferrable.getTokenPermissions()).thenReturn(ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL));
		assertThat(TokensConstraintScrypt.checkCreateTransferrable(tokDef, transferrable).isError()).isTrue();
	}

	@Test
	public void when_validating_create_transferrable__result_has_no_error() {
		FixedSupplyTokenDefinitionParticle tokDef = mock(FixedSupplyTokenDefinitionParticle.class);
		TransferrableTokensParticle transferrable = mock(TransferrableTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(transferrable.getGranularity()).thenReturn(UInt256.FIVE);
		when(tokDef.getSupply()).thenReturn(UInt256.FIVE);
		when(transferrable.getAmount()).thenReturn(UInt256.FIVE);
		when(transferrable.getTokenPermissions()).thenReturn(ImmutableMap.of());
		assertThat(TokensConstraintScrypt.checkCreateTransferrable(tokDef, transferrable).isSuccess()).isTrue();
	}

	@Test
	public void when_validating_create_unallocated_with_mismatching_granularities__result_has_error() {
		MutableSupplyTokenDefinitionParticle tokDef = mock(MutableSupplyTokenDefinitionParticle.class);
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(unallocated.getGranularity()).thenReturn(UInt256.FOUR);
		assertThat(TokensConstraintScrypt.checkCreateUnallocated(tokDef, unallocated).isError()).isTrue();
	}

	@Test
	public void when_validating_create_unallocated_with_mismatching_permissions__result_has_error() {
		MutableSupplyTokenDefinitionParticle tokDef = mock(MutableSupplyTokenDefinitionParticle.class);
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(unallocated.getGranularity()).thenReturn(UInt256.FIVE);
		when(tokDef.getTokenPermissions()).thenReturn(ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL));
		when(unallocated.getTokenPermissions()).thenReturn(ImmutableMap.of(TokenTransition.MINT, TokenPermission.NONE));
		assertThat(TokensConstraintScrypt.checkCreateUnallocated(tokDef, unallocated).isError()).isTrue();
	}

	@Test
	public void when_validating_create_unallocated_with_non_max_unallocated__result_has_error() {
		MutableSupplyTokenDefinitionParticle tokDef = mock(MutableSupplyTokenDefinitionParticle.class);
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(unallocated.getGranularity()).thenReturn(UInt256.FIVE);
		when(tokDef.getTokenPermissions()).thenReturn(ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL));
		when(unallocated.getTokenPermissions()).thenReturn(ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL));
		when(unallocated.getAmount()).thenReturn(UInt256.MAX_VALUE.decrement());
		assertThat(TokensConstraintScrypt.checkCreateUnallocated(tokDef, unallocated).isError()).isTrue();
	}

	@Test
	public void when_validating_create_unallocated__result_has_no_error() {
		MutableSupplyTokenDefinitionParticle tokDef = mock(MutableSupplyTokenDefinitionParticle.class);
		UnallocatedTokensParticle unallocated = mock(UnallocatedTokensParticle.class);
		when(tokDef.getGranularity()).thenReturn(UInt256.FIVE);
		when(unallocated.getGranularity()).thenReturn(UInt256.FIVE);
		when(tokDef.getTokenPermissions()).thenReturn(ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL));
		when(unallocated.getTokenPermissions()).thenReturn(ImmutableMap.of(TokenTransition.MINT, TokenPermission.ALL));
		when(unallocated.getAmount()).thenReturn(UInt256.MAX_VALUE);
		assertThat(TokensConstraintScrypt.checkCreateUnallocated(tokDef, unallocated).isSuccess()).isTrue();
	}

	@Test
	public void when_checking_token_permission_allowed_and_allowed__result_has_no_error() {
		WitnessData witnessData = mock(WitnessData.class);
		TokenPermission tokenPermission = mock(TokenPermission.class);
		RRI token = mock(RRI.class);
		when(tokenPermission.check(token, witnessData)).thenReturn(Result.success());
		assertThat(TokensConstraintScrypt.checkTokenActionAllowed(witnessData, tokenPermission, token)
			.isSuccess()).isTrue();
	}

	@Test
	public void when_checking_token_permission_allowed_and_not_allowed__result_has_error() {
		WitnessData witnessData = mock(WitnessData.class);
		TokenPermission tokenPermission = mock(TokenPermission.class);
		RRI token = mock(RRI.class);
		when(tokenPermission.check(token, witnessData)).thenReturn(Result.error(""));
		assertThat(TokensConstraintScrypt.checkTokenActionAllowed(witnessData, tokenPermission, token)
			.isError()).isTrue();
	}

	@Test
	public void when_checking_signed_by_and_signed__result_has_no_error() {
		WitnessData witnessData = mock(WitnessData.class);
		RadixAddress address = mock(RadixAddress.class);
		when(witnessData.isSignedBy(address.getPublicKey())).thenReturn(true);
		assertThat(TokensConstraintScrypt.checkSignedBy(witnessData, address).isSuccess()).isTrue();
	}

	@Test
	public void when_checking_signed_by_and_not_signed__result_has_error() {
		WitnessData witnessData = mock(WitnessData.class);
		RadixAddress address = mock(RadixAddress.class);
		when(witnessData.isSignedBy(address.getPublicKey())).thenReturn(false);
		assertThat(TokensConstraintScrypt.checkSignedBy(witnessData, address).isError()).isTrue();
	}
}