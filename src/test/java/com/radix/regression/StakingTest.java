package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import java.math.BigDecimal;

public class StakingTest {
	@Test
	public void given_an_account_owner_who_created_a_token__when_the_owner_stakes_then_redelegates_then_unstakes__then_it_should_all_be_successful() throws Exception {
		RadixAddress address1 = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
		RadixAddress address2 = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		api.discoverNodes();
		RadixNode originNode = api.getNetworkState()
			.map(RadixNetworkState::getNodes)
			.filter(s -> !s.isEmpty())
			.map(s -> s.iterator().next())
			.firstOrError()
			.blockingGet();
		RRI token = RRI.of(api.getAddress(), "COOKIE");

		CreateTokenAction createTokenAction = CreateTokenAction.create(
			token,
			"Cookie Token",
			"Cookiemonster approved",
			BigDecimal.valueOf(10000.0),
			TokenUnitConversions.subunitsToUnits(UInt256.ONE),
			TokenSupplyType.MUTABLE);
		Result result0 = api.execute(createTokenAction, originNode);
		result0.toObservable().subscribe(System.out::println);
		result0.blockUntilComplete();

		// Stake tokens
		Result result1 = api.stakeTokens(BigDecimal.valueOf(10000.0), token, address1);
		result1.toObservable().subscribe(System.out::println);
		result1.blockUntilComplete();

		// Redelegate staked tokens
		Result result2 = api.redelegateStakedTokens(BigDecimal.valueOf(5000.0), token, address1, address2);
		result2.toObservable().subscribe(System.out::println);
		result2.blockUntilComplete();

		// Unstake tokens
		Result result3 = api.unstakeTokens(BigDecimal.valueOf(5000.0), token, address1);
		result3.toObservable().subscribe(System.out::println, Throwable::printStackTrace);
		result3.blockUntilComplete();
		Result result4 = api.unstakeTokens(BigDecimal.valueOf(5000.0), token, address2);
		result4.toObservable().subscribe(System.out::println, Throwable::printStackTrace);
		result4.blockUntilComplete();
	}
}
