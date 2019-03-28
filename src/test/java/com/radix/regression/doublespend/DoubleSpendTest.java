package com.radix.regression.doublespend;

import com.radixdlt.client.application.identity.RadixIdentities;
import org.junit.Ignore;
import org.junit.Test;

public class DoubleSpendTest {
	@Test
	public void given_an_account_with_a_josh_token_with_one_supply__when_the_account_executes_two_transfers_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_transfer() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(
			api -> new DoubleSpendTokenTransferTestConditions(
				api.getMyAddress(),
				api.getAddressFromKey(RadixIdentities.createNew().getPublicKey())
			)
		);
		testRunner.execute(10);
	}

	@Test
	public void given_an_account_with_a_josh_token_with_two_supply__when_the_account_executes_two_transfers_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_transfer() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(
			api -> new DoubleSpendTokenTransferIntraDependencyTestConditions(
				api.getMyAddress(),
				api.getAddressFromKey(RadixIdentities.createNew().getPublicKey())
			));
		testRunner.execute(10);
	}

	@Test
	public void given_an_account__when_the_account_executes_two_token_creation_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_token_creation() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(api -> new DoubleSpendCreateTokenTestConditions(api.getMyAddress()));
		testRunner.execute(10);
	}

	@Test
	@Ignore("Won't work until RLAU-953 is finished")
	public void given_an_account__when_the_account_executes_two_token_creation_and_mint_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_token_creation() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(api -> new DoubleSpendCreateAndMintTokenTestConditions(api.getMyAddress()));
		testRunner.execute(10);
	}
}
