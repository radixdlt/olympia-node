package com.radix.regression;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import java.math.BigDecimal;
import org.junit.Test;
import com.radixdlt.utils.UInt256;

public class MintAndBurnTest {
	@Test
	public void given_an_account_owner_who_created_a_token__when_the_owner_mints_max_then_burns_max_then_mints_max__then_it_should_all_be_successful() throws Exception {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api);
		api.discoverNodes();
		RadixNode originNode = api.getNetworkState()
			.map(RadixNetworkState::getNodes)
			.filter(s -> !s.isEmpty())
			.map(s -> s.iterator().next())
			.firstOrError()
			.blockingGet();
		RRI token = RRI.of(api.getAddress(), "JOSH");

		CreateTokenAction createTokenAction = CreateTokenAction.create(
			token,
			"Joshy Token",
			"Best token",
			BigDecimal.ZERO,
			TokenUnitConversions.subunitsToUnits(UInt256.ONE),
			TokenSupplyType.MUTABLE);
		Result result0 = api.execute(createTokenAction, originNode);
		result0.toObservable().subscribe(System.out::println);
		result0.blockUntilComplete();

		MintTokensAction mintTokensAction = MintTokensAction.create(token, api.getAddress(), TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE));
		Result result1 = api.execute(mintTokensAction, originNode);
		result1.toObservable().subscribe(System.out::println);
		result1.blockUntilComplete();

		BurnTokensAction burnTokensAction = BurnTokensAction.create(token, api.getAddress(), TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE));
		Result result2 = api.execute(burnTokensAction, originNode);
		result2.toObservable().subscribe(System.out::println);
		result2.blockUntilComplete();

		MintTokensAction mintTokensAction2 = MintTokensAction.create(token, api.getAddress(), TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE));
		Result result3 = api.execute(mintTokensAction2, originNode);
		result3.toObservable().subscribe(System.out::println);
		result3.blockUntilComplete();
	}
}
