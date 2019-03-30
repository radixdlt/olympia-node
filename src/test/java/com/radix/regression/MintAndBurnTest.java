package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.Bootstrap;
import org.junit.Test;
import org.radix.utils.UInt256;

public class MintAndBurnTest {
	@Test
	public void given_an_account_owner_who_created_a_token__when_the_owner_mints_max_then_burns_max_then_mints_max__then_it_should_all_be_successful() throws Exception {

		// Given account owner listening to own messages
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, RadixIdentities.createNew());
		Result result = api.createMultiIssuanceToken("Joshy Token", "JOSH");
		result.toObservable().subscribe(System.out::println);
		result.blockUntilComplete();

		api.mintTokens("JOSH", TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE))
			.blockUntilComplete();

		api.burnTokens("JOSH", TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE))
			.blockUntilComplete();

		api.mintTokens("JOSH", TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE))
			.blockUntilComplete();
	}
}
