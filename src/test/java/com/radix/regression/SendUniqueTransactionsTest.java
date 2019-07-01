package com.radix.regression;

import com.radix.TestEnv;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.core.atoms.particles.RRI;
import org.junit.Test;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.ActionExecutionException;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.unique.AlreadyUsedUniqueIdReason;
import com.radixdlt.client.application.translate.unique.PutUniqueIdAction;
import com.radixdlt.client.application.translate.unique.UniqueId;

import io.reactivex.Completable;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;

/**
 * RLAU-372
 */
public class SendUniqueTransactionsTest {

	@Test
	public void given_an_account_owner_which_has_performed_an_action_with_a_unique_id__when_the_client_attempts_to_use_same_id__then_client_should_be_notified_that_unique_id_is_already_used() throws Exception {

		// Given account owner which has performed an action with a unique id
		RadixApplicationAPI api = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		final String uniqueId = "thisisauniquestring";
		Transaction transaction = api.createTransaction();
		transaction.stage(SendMessageAction.create(new byte[] {0}, api.getMyAddress(), api.getMyAddress(), false));
		RRI rriUnique = RRI.of(api.getMyAddress(), uniqueId);
		transaction.stage(PutUniqueIdAction.create(rriUnique));
		Completable initialUniqueStatus = transaction.commitAndPush().toCompletable();
		initialUniqueStatus.blockingAwait();

		// When client attempts to use same id
		TestObserver<Object> submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		Transaction transaction1 = api.createTransaction();
		transaction1.stage(SendMessageAction.create(new byte[] {1}, api.getMyAddress(), api.getMyAddress(), false));
		transaction1.stage(PutUniqueIdAction.create(rriUnique));
		Completable conflictingUniqueStatus = transaction1.commitAndPush().toCompletable();
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
		RadixApplicationAPI api = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		RRI unique = RRI.of(api.getMyAddress(), "thisisauniquestring");

		// When client attempts to use id
		TestObserver<Object> submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		Transaction transaction = api.createTransaction();
		transaction.stage(SendMessageAction.create(new byte[] {1}, api.getMyAddress(), api.getMyAddress(), false));
		transaction.stage(PutUniqueIdAction.create(unique));
		Completable conflictingUniqueStatus = transaction.commitAndPush().toCompletable();
		conflictingUniqueStatus.subscribe(submissionObserver);

		// Then client should be notified of success
		submissionObserver.awaitTerminalEvent();
		submissionObserver.assertComplete();
	}

	@Test
	public void given_an_account_owner_which_has_not_used_a_unique_id__when_the_client_attempts_to_use_id_in_another_account__then_client_should_be_notified_of_error() throws Exception {

		// Given account owner which has NOT performed an action with a unique id
		RadixApplicationAPI api1 = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(),  RadixIdentities.createNew());
		RadixApplicationAPI api2 = RadixApplicationAPI.create(TestEnv.getBootstrapConfig(), RadixIdentities.createNew());
		RRI unique = RRI.of(api2.getMyAddress(), "thisisauniquestring");

		// When client attempts to use id in ANOTHER account
		TestObserver<Object> submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		Transaction transaction = api1.createTransaction();
		transaction.stage(SendMessageAction.create(new byte[] {1}, api1.getMyAddress(), api1.getMyAddress(), false));
		transaction.stage(PutUniqueIdAction.create(unique));
		Completable conflictingUniqueStatus = transaction.commitAndPush().toCompletable();
		conflictingUniqueStatus.subscribe(submissionObserver);

		// Then client should be notified of error
		submissionObserver.awaitTerminalEvent();
		submissionObserver.assertError(ActionExecutionException.class);
	}
}
