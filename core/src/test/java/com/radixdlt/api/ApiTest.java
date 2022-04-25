/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.modules.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.api.core.openapitools.JSON;
import com.radixdlt.api.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.reconstruction.BerkeleyRecoverableProcessedTxnStore;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.deterministic.SingleNodeDeterministicRunner;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.network.p2p.addressbook.AddressBookPersistence;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.utils.properties.RuntimeProperties;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.util.HeaderMap;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class ApiTest {
  private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  @Inject private SingleNodeDeterministicRunner runner;
  private final Amount totalTokenAmount = Amount.ofTokens(110);
  private final Amount stakeAmount = Amount.ofTokens(10);
  private final Amount liquidAmount =
      Amount.ofSubunits(totalTokenAmount.toSubunits().subtract(stakeAmount.toSubunits()));
  private final int mempoolMaxSize;

  protected ApiTest(int mempoolMaxSize) {
    this.mempoolMaxSize = mempoolMaxSize;
  }

  protected ApiTest() {
    this.mempoolMaxSize = 10;
  }

  @Before
  public void setup() {
    var injector =
        Guice.createInjector(
            MempoolConfig.asModule(mempoolMaxSize, 10),
            new MainnetForksModule(),
            new RadixEngineForksLatestOnlyModule(
                RERulesConfig.testingDefault()
                    .overrideFeeTable(
                        FeeTable.create(
                            Amount.ofSubunits(UInt256.ONE),
                            Map.of(
                                ValidatorRegisteredCopy.class, Amount.ofSubunits(UInt256.ONE))))),
            new ForksModule(),
            new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY, 1),
            new MockedGenesisModule(Set.of(TEST_KEY.getPublicKey()), totalTokenAmount, stakeAmount),
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(BerkeleyRecoverableProcessedTxnStore.class).in(Scopes.SINGLETON);
                Multibinder.newSetBinder(binder(), BerkeleyAdditionalStore.class)
                    .addBinding()
                    .to(BerkeleyRecoverableProcessedTxnStore.class);
                bindConstant()
                    .annotatedWith(DatabaseLocation.class)
                    .to(folder.getRoot().getAbsolutePath());
                bindConstant().annotatedWith(NetworkId.class).to(99);
                bind(P2PConfig.class).toInstance(mock(P2PConfig.class));
                bind(AddressBook.class).in(Scopes.SINGLETON);
                var selfUri =
                    RadixNodeUri.fromPubKeyAndAddress(
                        99, TEST_KEY.getPublicKey(), "localhost", 23456);
                bind(RadixNodeUri.class).annotatedWith(Self.class).toInstance(selfUri);
                var addressBookPersistence = mock(AddressBookPersistence.class);
                when(addressBookPersistence.getAllEntries()).thenReturn(ImmutableList.of());
                bind(AddressBookPersistence.class).toInstance(addressBookPersistence);
                var runtimeProperties = mock(RuntimeProperties.class);
                when(runtimeProperties.get(eq("api.transactions.enable"), anyBoolean()))
                    .thenReturn(true);
                bind(RuntimeProperties.class).toInstance(runtimeProperties);
              }
            });
    injector.injectMembers(this);
  }

  protected Amount getStakeAmount() {
    return stakeAmount;
  }

  protected Amount getLiquidAmount() {
    return liquidAmount;
  }

  protected NetworkIdentifier networkIdentifier() {
    return new NetworkIdentifier().network("localnet");
  }

  protected ECPublicKey selfKey() {
    return TEST_KEY.getPublicKey();
  }

  protected final void start() {
    runner.start();
  }

  protected final void runUntilCommitted(AID txnId) {
    runner.runNextEventsThrough(
        LedgerUpdate.class,
        u -> {
          var output = u.getStateComputerOutput().getInstance(REOutput.class);
          return output.getProcessedTxns().stream()
              .anyMatch(txn -> txn.getTxn().getId().equals(txnId));
        });
  }

  private HttpServerExchange exchange(Exception e, Sender sender) {
    var httpServerExchange = mock(HttpServerExchange.class);
    when(httpServerExchange.getAttachment(ExceptionHandler.THROWABLE)).thenReturn(e);
    when(httpServerExchange.isInIoThread()).thenReturn(false);
    when(httpServerExchange.getResponseHeaders()).thenReturn(new HeaderMap());
    when(httpServerExchange.getResponseSender()).thenReturn(sender);
    return httpServerExchange;
  }

  private HttpServerExchange exchange(byte[] request, Sender sender) {
    var httpServerExchange = mock(HttpServerExchange.class);
    when(httpServerExchange.getInputStream()).thenReturn(new ByteArrayInputStream(request));
    when(httpServerExchange.isInIoThread()).thenReturn(false);
    when(httpServerExchange.getResponseHeaders()).thenReturn(new HeaderMap());
    when(httpServerExchange.getResponseSender()).thenReturn(sender);
    return httpServerExchange;
  }

  private HttpServerExchange exchange(Sender sender) {
    return exchange(new byte[0], sender);
  }

  protected String handleRequest(HttpHandler handler) throws Exception {
    var sender = mock(Sender.class);
    var response = new AtomicReference<String>();
    doAnswer(
            invocation -> {
              response.set(invocation.getArgument(0));
              return null;
            })
        .when(sender)
        .send(anyString());
    handler.handleRequest(exchange(sender));
    return response.get();
  }

  protected <T> T handleExceptionWithExpectedResponse(
      HttpHandler handler, Exception e, Class<T> responseClass) throws Exception {
    var objectMapper = JSON.getDefault().getMapper();
    var sender = mock(Sender.class);
    var response = new AtomicReference<String>();
    doAnswer(
            invocation -> {
              response.set(invocation.getArgument(0));
              return null;
            })
        .when(sender)
        .send(anyString());
    handler.handleRequest(exchange(e, sender));
    return objectMapper.readValue(response.get(), responseClass);
  }

  protected <T> T handleRequestWithExpectedResponse(
      HttpHandler handler, byte[] requestBytes, Class<T> responseClass) throws Exception {
    var objectMapper = JSON.getDefault().getMapper();
    var sender = mock(Sender.class);
    var response = new AtomicReference<String>();
    doAnswer(
            invocation -> {
              response.set(invocation.getArgument(0));
              return null;
            })
        .when(sender)
        .send(anyString());
    handler.handleRequest(exchange(requestBytes, sender));
    var deserializedResponse = objectMapper.readValue(response.get(), responseClass);
    if (deserializedResponse == null) {
      throw new IllegalStateException("Unexpected response: " + response.get());
    }
    return deserializedResponse;
  }

  protected <T> T handleRequestWithExpectedResponse(
      HttpHandler handler, Object request, Class<T> responseClass) throws Exception {
    var objectMapper = JSON.getDefault().getMapper();
    var requestBytes = objectMapper.writeValueAsBytes(request);
    return handleRequestWithExpectedResponse(handler, requestBytes, responseClass);
  }

  protected <T> T handleRequestWithExpectedResponse(HttpHandler handler, Class<T> responseClass)
      throws Exception {
    var sender = mock(Sender.class);
    var response = new AtomicReference<String>();
    doAnswer(
            invocation -> {
              response.set(invocation.getArgument(0));
              return null;
            })
        .when(sender)
        .send(anyString());
    handler.handleRequest(exchange(sender));
    return JSON.getDefault().getMapper().readValue(response.get(), responseClass);
  }
}
