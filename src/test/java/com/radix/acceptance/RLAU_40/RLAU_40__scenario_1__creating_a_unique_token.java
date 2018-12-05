package com.radix.acceptance.RLAU_40;

import java.math.BigDecimal;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction;
import com.radixdlt.client.application.translate.tokenclasses.TokenState;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;

import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

public class RLAU_40__scenario_1__creating_a_unique_token {
	public static void main(String[] args) throws Exception {
		try {
			// Given I have access to suitable development tools
			// => (Eclipse)
			// And I have included the radixdlt-java library
			// => (Inspect via project settings)
			// And I have access to a suitable Radix network
			// => {
			RadixUniverse.bootstrap(Bootstrap.BETANET);

			final RadixIdentity identity = RadixIdentities.createNew();
			System.out.println("Public Key: " + identity.getPublicKey());
			System.out.println("Key Hash: " + identity.getPublicKey().getUID());

			RadixApplicationAPI api = RadixApplicationAPI.create(identity);

			api.pull();
			//}

			// When I submit a unique token-creation request with the "name",
			// "symbol", "totalSupply" and "granularity" properties for a
			// single-issuance token class
			// (TODO: Note "granularity" functionality is missing here)
			// => {
			TestObserver<AtomSubmissionUpdate.AtomSubmissionState> testAtomSubmissionState = new TestObserver<>();
			TokenState tokenState = new TokenState(
					"Test Token 1 for Radix Launch Story",  // name
					"RLA01",                                // symbol (aka ISO)
					"Test token 1 for acceptance testing for Radix Launch Story RLAU-40",
					BigDecimal.valueOf(1_000_000_000L),     // total supply
					TokenState.TokenSupplyType.FIXED);      // fixed supply token
			api.createToken(
					tokenState.getName(),
					tokenState.getIso(),
					tokenState.getDescription(),
					TokenClassReference.unitsToSubunits(tokenState.getTotalSupply()),
					CreateTokenAction.TokenSupplyType.FIXED)
				.toObservable()
				.map(Utils::print)
				.map(AtomSubmissionUpdate::getState)
				.subscribe(testAtomSubmissionState);
			// Check token creation atom was stored
			testAtomSubmissionState
				.awaitCount(3, TestWaitStrategy.SLEEP_100MS, 10_000L)
				.assertNoErrors()
				.assertNoTimeout()
				.assertValues(AtomSubmissionState.SUBMITTING, AtomSubmissionState.SUBMITTED, AtomSubmissionState.STORED);
			//}

			// Then I can observe the atom being accepted by the network
			// => (Output is "Token(RLA01) name(Test Token 1 for Radix Launch Story)
			//     description(Test token 1 for acceptance testing for Radix Launch Story RLAU-40)
			//     totalSupply(1000000000.000000000000000000) maxSupply(1000000000.000000000000000000)")
			// => {
			TestObserver<TokenState> testTokenCreation = new TestObserver<>();
			api.getTokenClass(TokenClassReference.of(api.getMyAddress(), "RLA01"))
				.map(Utils::print)
				.subscribe(testTokenCreation);
			// Token was observed
			testTokenCreation
				.awaitCount(1, TestWaitStrategy.SLEEP_100MS, 10_000L)
				.assertNoErrors()
				.assertNoTimeout()
				.assertValue(tokenState);
			//}

			System.out.println("Success!");
		} catch (AssertionError ae) {
			ae.printStackTrace(System.err);
			System.err.println("Fail!");
		}

		// Do something better once API has a clean way to shutdown
		System.exit(0);
	}
}