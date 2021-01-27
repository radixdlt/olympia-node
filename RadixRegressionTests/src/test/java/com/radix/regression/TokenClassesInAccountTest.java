package com.radix.regression;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.BeforeClass;
import org.junit.Test;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenState;

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
		api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api);
	}

	private static CreateTokenAction buildCreateNewTokenAction(String symbol, BigDecimal initialSupply) {
		return CreateTokenAction.create(
			RRI.of(api.getAddress(), symbol),
			"Joshy Coin",
			"Ze best coin!",
			initialSupply,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.FIXED
		);
	}

	private Predicate<TokenDefinitionsState> getPredicateCheck(String symbol, BigDecimal initialSupply) {
		return tokens -> {
			TokenState tokenState = tokens.getState().get(RRI.of(api.getAddress(), symbol));
			return tokenState.getName().equals("Joshy Coin")
				&& tokenState.getIso().equals(symbol)
				&& tokenState.getDescription().equals("Ze best coin!")
				&& tokenState.getTotalSupply().compareTo(initialSupply) == 0
				&& tokenState.getGranularity().equals(TokenUnitConversions.getMinimumGranularity())
				&& tokenState.getTokenSupplyType().equals(TokenState.TokenSupplyType.FIXED);
		};
	}

	@Test
	public void given_an_account_with_two_tokens__when_the_account_is_subscribed_for_the_token_state__then_the_two_tokens_are_published() {
		// Given an account with two tokens
		Action[] givenActions = new Action[] {
			buildCreateNewTokenAction("JOSH", BigDecimal.valueOf(10000)),
			buildCreateNewTokenAction("JOSH2", BigDecimal.valueOf(100))
		};

		// When the account is subscribed for the token state
		Supplier<Observable<TokenDefinitionsState>> subscription = () -> api.observeTokenDefs(api.getAddress());

		// Then the two tokens are published
		List<Predicate<TokenDefinitionsState>> thenChecks = Arrays.asList(
			getPredicateCheck("JOSH", BigDecimal.valueOf(10000)),
			getPredicateCheck("JOSH2", BigDecimal.valueOf(100))
		);

		// Given setup execution
		for (Action action : givenActions) {
			api.execute(action).blockUntilComplete();
		}

		// When execution
		TestObserver<TokenDefinitionsState> testObserver = TestObserver.create(Util.loggingObserver("TokenClassListener"));
		subscription.get()
			.firstOrError()
			.subscribe(testObserver);

		// Then check
		testObserver.awaitTerminalEvent();
		thenChecks.forEach(testObserver::assertValue);
	}
}
