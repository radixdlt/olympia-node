package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokenclasses.TokenState;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.utils.UInt256;

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

	private CreateTokenAction buildCreateNewTokenAction(String iso, UInt256 initialSupply) {
		return new CreateTokenAction(
			api.getMyAddress(),
			"Joshy Coin",
			iso,
			"Ze best coin!",
			initialSupply,
			TokenSupplyType.FIXED
		);
	}

	private Predicate<Map<TokenClassReference, TokenState>> getPredicateCheck(String iso, UInt256 initialSupply) {
		TokenState expectedTokenState = new TokenState(
			"Joshy Coin",
			iso,
			"Ze best coin!",
			TokenClassReference.subunitsToUnits(initialSupply),
			TokenState.TokenSupplyType.FIXED
		);

		return tokens -> tokens.get(TokenClassReference.of(api.getMyAddress(), iso)).equals(expectedTokenState);
	}

	@Test
	public void given_an_account_with_two_tokens__when_the_account_is_subscribed_for_the_token_state__then_the_two_tokens_are_published() throws Exception {
		// Given an account with two tokens
		Action[] givenActions = new Action[] {
			buildCreateNewTokenAction("JOSH", UInt256.from(10000)),
			buildCreateNewTokenAction("JOSH2", UInt256.from(100))
		};

		// When the account is subscribed for the token state
		Supplier<Observable<Map<TokenClassReference, TokenState>>> subscription = () -> api.getTokenClasses(api.getMyAddress());

		// Then the two tokens are published
		List<Predicate> thenChecks = Arrays.asList(
			getPredicateCheck("JOSH", UInt256.from(10000)),
			getPredicateCheck("JOSH2", UInt256.from(100))
		);

		// Given setup execution
		Completable givenCompleted = api.executeSequentially(givenActions);
		givenCompleted.blockingAwait();

		// When execution
		TestObserver<Map<TokenClassReference, TokenState>> testObserver = TestObserver.create(Util.loggingObserver("TokenClassListener"));
		subscription.get()
			.debounce(15, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(testObserver);

		// Then check
		testObserver.awaitTerminalEvent();
		thenChecks.forEach(testObserver::assertValue);
	}
}
