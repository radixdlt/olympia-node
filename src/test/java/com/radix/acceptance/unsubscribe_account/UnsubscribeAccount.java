package com.radix.acceptance.unsubscribe_account;

import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.STORED;
import static com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType.VALIDATION_ERROR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radix.acceptance.SpecificProperties;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.data.SendMessageToParticlesMapper;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokenclasses.MintTokensAction;
import com.radixdlt.client.application.translate.tokenclasses.TokenClassesState;
import com.radixdlt.client.application.translate.tokens.TokenClassReference;
import com.radixdlt.client.application.translate.tokens.UnknownTokenException;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.timestamp.TimestampParticle;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomReceivedAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import com.radixdlt.client.core.network.jsonrpc.AtomQuery;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient.NodeAtomSubmissionUpdate;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.Request;
import org.radix.utils.UInt256;

/**
 * See <a href="https://radixdlt.atlassian.net/browse/RLAU-94">RLAU-94</a>.
 */
public class UnsubscribeAccount {
	static {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	private static final String ADDRESS = "address";
	private static final String NAME = "name";
	private static final String SYMBOL = "symbol";
	private static final String DESCRIPTION = "description";
	private static final String INITIAL_SUPPLY = "initialSupply";
	private static final String NEW_SUPPLY = "newSupply";
	private static final String GRANULARITY = "granularity";

	private static final long TIMEOUT_MS = 10_000L; // Timeout in milliseconds

	private WebSocketClient webSocketClient;
	private ECKeyPair keyPair;
	private RadixJsonRpcClient jsonRpcClient;
	private TestObserver<AtomObservation> observer;
	private RadixApplicationAPI api;
	private RadixIdentity identity;
	private String uuid;

	private final SpecificProperties properties = SpecificProperties.of(
		ADDRESS,        "unknown",
		NAME,           "RLAU-40 Test token",
		SYMBOL,			"RLAU",
		DESCRIPTION,	"RLAU-40 Test token",
		GRANULARITY,	"1"
	);
	private final List<TestObserver<SubmitAtomAction>> observers = Lists.newArrayList();
	private final List<Disposable> disposables = Lists.newArrayList();

	@After
	public void after() {
		this.observer.dispose();
		this.webSocketClient.close();
	}

	@Given("^a node connected websocket client who has an atom subscription to an empty account$")
	public void a_websocket_client_who_has_an_atom_subscription() throws Throwable {
		setupWebSocket();

		this.jsonRpcClient = new RadixJsonRpcClient(this.webSocketClient);
		this.uuid = UUID.randomUUID().toString();
		this.observer = TestObserver.create();
		this.jsonRpcClient.observeAtoms(this.uuid)
			.doOnNext(System.out::println)
			.subscribe(observer);

		TestObserver completionObserver = TestObserver.create();
		this.jsonRpcClient.sendAtomsSubscribe(this.uuid, new AtomQuery(this.api.getMyAddress()))
			.subscribe(completionObserver);
		completionObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
		completionObserver.assertComplete();

		observer.awaitCount(1);
		observer.assertValue(AtomObservation::isHead);
	}

	@When("the client sends a cancel subscription request followed by a message to this account$")
	public void the_client_sends_a_cancel_subscription_request_followed_by_a_message() throws Throwable {
		TestObserver completionObserver = TestObserver.create();
		this.jsonRpcClient.cancelAtomsSubscribe(this.uuid).subscribe(completionObserver);
		completionObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
		completionObserver.assertComplete();

		TestObserver<NodeAtomSubmissionUpdate> atomSubmission = TestObserver.create();
		this.api.buildAtom(new SendMessageAction(new byte[]{1, 2, 3, 4}, this.api.getMyAddress(), this.api.getMyAddress(), false))
			.flatMap(this.identity::sign)
			.flatMapObservable(this.jsonRpcClient::submitAtom)
			.subscribe(atomSubmission);
		atomSubmission.awaitTerminalEvent(5, TimeUnit.SECONDS);
		atomSubmission.assertNoErrors();
		atomSubmission.assertComplete();
	}

	@Then("^the client should not receive any atom notification$")
	public void the_client_should_not_receive_any_atom_notifications() throws Throwable {
		observer.await(5, TimeUnit.SECONDS);
		observer.assertValue(AtomObservation::isHead);
		observer.assertNotComplete();
	}

	private void setupWebSocket() {
		this.identity = RadixIdentities.createNew();
		this.api = RadixApplicationAPI.create(this.identity);

		Request localhost = new Request.Builder().url("ws://localhost:8080/rpc").build();
		this.webSocketClient = new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(localhost, listener));
		this.webSocketClient.connect();
		this.webSocketClient.getState()
			.filter(WebSocketStatus.CONNECTED::equals)
			.blockingFirst();

	}
}
