package com.radix.acceptance.keepalive_endpoint;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.reactivex.observers.TestObserver;
import java.util.Set;
import okhttp3.Request;

import java.util.concurrent.TimeUnit;

public class KeepaliveEndpoint {
    private static final BootstrapConfig BOOTSTRAP_CONFIG;
    static {
        String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
        if (bootstrapConfigName != null) {
            BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
        } else {
            BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST_SINGLENODE;
        }
    }

    private WebSocketClient webSocketClient;
    private RadixJsonRpcClient jsonRpcClient;

    private TestObserver<RadixJsonRpcClient.JsonRpcResponse> observer;


    @After
    public void after() {
        this.webSocketClient.close();
        this.webSocketClient.getState()
                .doOnNext(System.out::println)
                .filter(WebSocketStatus.DISCONNECTED::equals)
                .firstOrError()
                .blockingGet();
        this.webSocketClient = null;
    }


    @Given("^that I have established a websocket connection to a node$")
    public void that_i_have_established_a_websocket_connection_to_a_node() throws Throwable {
        setupWebSocket();

        this.jsonRpcClient = new RadixJsonRpcClient(this.webSocketClient);
    }

    @When("^I call the keep-alive endpoint$")
    public void i_call_the_keepalive_endpoint() throws Throwable {
        this.observer =  TestObserver.create();

        this.jsonRpcClient.jsonRpcCall("Ping").subscribe(this.observer);
    }

    @Then("^I should receive a small reply confirming that the connection is still active$")
    public void i_should_receive_pong() throws Throwable {
        observer.awaitTerminalEvent(5, TimeUnit.SECONDS);

        observer.assertNoErrors();
        observer.assertComplete();
        observer.assertValueAt(0, RadixJsonRpcClient.JsonRpcResponse::isSuccess);
        observer.assertValueAt(0, response -> response.getResult().getAsJsonObject().get("response").getAsString().equals("pong"));
    }


    private void setupWebSocket() {
        RadixApplicationAPI api = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, RadixIdentities.createNew());
        api.discoverNodes();
        RadixNode node = api.getNetworkState()
            .filter(state -> !state.getNodes().isEmpty())
            .map(state -> state.getNodes().keySet().iterator().next())
            .blockingFirst();

        Request localhost = new Request.Builder().url(node.toString()).build();
        this.webSocketClient = new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(localhost, listener));
        this.webSocketClient.connect();
        this.webSocketClient.getState()
                .filter(WebSocketStatus.CONNECTED::equals)
                .blockingFirst();
    }
}
