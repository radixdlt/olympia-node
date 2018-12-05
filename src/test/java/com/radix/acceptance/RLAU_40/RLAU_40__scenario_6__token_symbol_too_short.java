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

import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTED;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.SUBMITTING;
import static com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState.VALIDATION_ERROR;

import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;

public class RLAU_40__scenario_6__token_symbol_too_short {
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

			// When I submit a unique token-creation request with the
			// "name", "symbol", "totalSupply" and "granularity" properties
			// for a single-issuance token class
			// And the "symbol" for the token is less than the minimum length (3)
			// (TODO: Note "granularity" functionality is missing here)
			// => {
			TokenState tokenState = new TokenState(
					"Test Token 1 for Radix Launch Story",  // name
					"X",                                    // symbol (aka ISO)
					"Test token 1 for acceptance testing for Radix Launch Story RLAU-40",
					BigDecimal.valueOf(1_000_000_000L),     // total supply
					TokenState.TokenSupplyType.FIXED);      // fixed supply token

			// Create initial token
			TestObserver<AtomSubmissionUpdate.AtomSubmissionState> failedAtomSubmissionState = new TestObserver<>();
			api.createToken(
					tokenState.getName(),
					tokenState.getIso(),
					tokenState.getDescription(),
					TokenClassReference.unitsToSubunits(tokenState.getTotalSupply()),
					CreateTokenAction.TokenSupplyType.FIXED)
				.toObservable()
				.map(Utils::print)
				.map(AtomSubmissionUpdate::getState)
				.subscribe(failedAtomSubmissionState);
			//}

			// Then I can observe the atom being rejected by the network
			// => {
			failedAtomSubmissionState
				.awaitCount(3, TestWaitStrategy.SLEEP_100MS, 10_000L)
				.assertNoErrors()
				.assertNoTimeout()
				.assertValues(SUBMITTING, SUBMITTED, VALIDATION_ERROR);
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
