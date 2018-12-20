package com.radix.regression.unique;

import com.radix.regression.Util;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.ActionExecutionException;
import com.radixdlt.client.application.translate.atomic.AtomicAction;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.unique.AlreadyUsedUniqueIdReason;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.application.translate.unique.UniqueId;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import io.reactivex.Completable;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * RLAU-372
 */
public class SendUniqueTransactionsTest {

	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	@Test
	public void given_an_account_owner_which_has_performed_an_action_with_a_unique_id__when_the_client_attempts_to_use_same_id__then_client_should_be_notified_that_unique_id_is_already_used() throws Exception {

		// Given account owner which has performed an action with a unique id
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixIdentities.createNew());
		final String uniqueId = "thisisauniquestring";
		Completable initialUniqueStatus = api.execute(
			new AtomicAction(
				new SendMessageAction(new byte[] {0}, api.getMyAddress(), api.getMyAddress(), false),
				new PutUniqueIdAction(api.getMyAddress(), uniqueId)
			)
		).toCompletable();
		initialUniqueStatus.blockingAwait();

		// When client attempts to use same id
		TestObserver submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		Completable conflictingUniqueStatus = api.execute(
			new AtomicAction(
				new SendMessageAction(new byte[] {1}, api.getMyAddress(), api.getMyAddress(), false),
				new PutUniqueIdAction(api.getMyAddress(), uniqueId)
			)
		).toCompletable();
		conflictingUniqueStatus.subscribe(submissionObserver);

		// Then client should be notified that unique id is already used
		submissionObserver.awaitTerminalEvent();
		final AlreadyUsedUniqueIdReason expectedReason = new AlreadyUsedUniqueIdReason(new UniqueId(api.getMyAddress(), uniqueId));
		final Predicate<ActionExecutionException> hasExpectedUniqueIdCollision = e -> e.getReasons().stream().anyMatch(expectedReason::equals);
		submissionObserver.assertError(e -> hasExpectedUniqueIdCollision.test((ActionExecutionException) e));
		submissionObserver.assertError(ActionExecutionException.class);
	}

	@Test
	public void given_an_account_owner_which_has_not_used_a_unique_id__when_the_client_attempts_to_use_id__then_client_should_be_notified_of_success() throws Exception {

		// Given account owner which has NOT performed an action with a unique id
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixIdentities.createNew());
		final String uniqueId = "thisisauniquestring";

		// When client attempts to use id
		TestObserver submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		Completable conflictingUniqueStatus = api.execute(
			new AtomicAction(
				new SendMessageAction(new byte[] {1}, api.getMyAddress(), api.getMyAddress(), false),
				new PutUniqueIdAction(api.getMyAddress(), uniqueId)
			)
		).toCompletable();
		conflictingUniqueStatus.subscribe(submissionObserver);

		// Then client should be notified of success
		submissionObserver.awaitTerminalEvent();
		submissionObserver.assertComplete();
	}
}
