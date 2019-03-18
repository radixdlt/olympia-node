package com.radix.regression;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenState;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;

/**
 * RLAU-97
 */
public class TokenClassesInAccountTest {
	private static RadixApplicationAPI api;

	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}

		api = RadixApplicationAPI.create(RadixIdentities.createNew());
	}

	private static CreateTokenAction buildCreateNewTokenAction(String iso, UInt256 initialSupply) {
		return new CreateTokenAction(
			api.getMyAddress(),
			"Joshy Coin",
			iso,
			"Ze best coin!",
			initialSupply,
			UInt256.ONE,
			TokenSupplyType.FIXED
		);
	}

	private Predicate<TokenDefinitionsState> getPredicateCheck(String iso, UInt256 initialSupply) {
		TokenState expectedTokenState = new TokenState(
			"Joshy Coin",
			iso,
			"Ze best coin!",
			TokenDefinitionReference.subunitsToUnits(initialSupply),
			TokenDefinitionReference.subunitsToUnits(1),
			TokenState.TokenSupplyType.FIXED
		);

		return tokens -> tokens.getState().get(TokenDefinitionReference.of(api.getMyAddress(), iso)).equals(expectedTokenState);
	}

	@Test
	public void given_an_account_with_two_tokens__when_the_account_is_subscribed_for_the_token_state__then_the_two_tokens_are_published() throws Exception {
		// Given an account with two tokens
		Action[] givenActions = new Action[] {
			buildCreateNewTokenAction("JOSH", UInt256.from(10000)),
			buildCreateNewTokenAction("JOSH2", UInt256.from(100))
		};

		// When the account is subscribed for the token state
		Supplier<Observable<TokenDefinitionsState>> subscription = () -> api.getTokenClasses(api.getMyAddress());

		// Then the two tokens are published
		List<Predicate<TokenDefinitionsState>> thenChecks = Arrays.asList(
			getPredicateCheck("JOSH", UInt256.from(10000)),
			getPredicateCheck("JOSH2", UInt256.from(100))
		);

		// Given setup execution
		Completable givenCompleted = api.executeSequentially(givenActions);
		givenCompleted.blockingAwait();

		// When execution
		TestObserver<TokenDefinitionsState> testObserver = TestObserver.create(Util.loggingObserver("TokenClassListener"));
		subscription.get()
			.debounce(15, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(testObserver);

		// Then check
		testObserver.awaitTerminalEvent();
		thenChecks.forEach(testObserver::assertValue);
	}
}
