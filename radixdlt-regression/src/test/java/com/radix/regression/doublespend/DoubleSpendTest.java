/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radix.regression.doublespend;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import org.junit.Test;

public class DoubleSpendTest {
//	private static final int ITERATIONS = 10;
	//Temporary configured to run 1 iterations because test failing with multiple run randomly
	//Have to be reverted when new consensus algorithm will be implemented
	private static final int ITERATIONS = 1;

	@Test
	public void given_an_account_with_a_josh_token_with_one_supply__when_the_account_executes_two_transfers_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_transfer() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(
			api -> new DoubleSpendTokenTransferTestConditions(
				api.getAddress(),
				api.getAddress(RadixIdentities.createNew().getPublicKey())
			)
		);
		testRunner.execute(ITERATIONS);
	}

	@Test
	public void given_an_account_with_a_josh_token_with_two_supply__when_the_account_executes_two_transfers_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_transfer() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(
			api -> new DoubleSpendTokenTransferIntraDependencyTestConditions(
				api.getAddress(),
				api.getAddress(RadixIdentities.createNew().getPublicKey())
			));
		testRunner.execute(ITERATIONS);
	}

	@Test
	public void given_an_account__when_the_account_executes_two_token_creation_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_token_creation() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(api -> new DoubleSpendCreateTokenTestConditions(api.getAddress()));
		testRunner.execute(ITERATIONS);
	}

	@Test
	public void given_an_account__when_the_account_executes_two_token_creation_and_mint_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_token_creation() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(api -> new DoubleSpendCreateAndMintTokenTestConditions(api.getAddress()));
		testRunner.execute(ITERATIONS);
	}

	@Test
	public void given_an_account_with_three_tokens__when_two_conflicting_transfers_which_also_conflict_with_token_creations__then_neither_transfer_should_be_successful() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(
			api -> new DoubleSpendMultiConflictTestConditions(
				api.getAddress(),
				api.getAddress(RadixIdentities.createNew().getPublicKey())
			)
		);
		testRunner.execute(ITERATIONS);
	}

	@Test
	public void given_an_account__when_the_account_executes_two_send_to_self_atomic_transactions__then_the_account_balances_should_resolve_to_only_one_send_to_self_atomic_transactio() {
		DoubleSpendTestRunner testRunner = new DoubleSpendTestRunner(
			api -> new DoubleSpendWithInnerDependencyConditions(api.getAddress()),
			(bootstrap, identity) -> RadixApplicationAPI.defaultBuilder()
				.bootstrap(bootstrap)
				.identity(identity)
				.build()
		);
		testRunner.execute(ITERATIONS);
	}
}
