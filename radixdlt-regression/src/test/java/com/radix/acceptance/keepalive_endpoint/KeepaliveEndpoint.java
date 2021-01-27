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

package com.radix.acceptance.keepalive_endpoint;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.reactivex.observers.TestObserver;

import java.util.concurrent.TimeUnit;

public class KeepaliveEndpoint {
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
        RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
        api.discoverNodes();
        RadixNode node = api.getNetworkState()
            .filter(state -> !state.getNodes().isEmpty())
            .map(state -> state.getNodes().iterator().next())
            .blockingFirst();

        this.webSocketClient = new WebSocketClient(listener ->
            HttpClients.getSslAllTrustingClient().newWebSocket(node.getWebSocketEndpoint(), listener)
        );
        this.webSocketClient.connect();
        this.webSocketClient.getState()
                .filter(WebSocketStatus.CONNECTED::equals)
                .blockingFirst();
    }
}
