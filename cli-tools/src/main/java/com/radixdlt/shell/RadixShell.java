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

package com.radixdlt.shell;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.radixdlt.modules.ModuleRunner;
import com.radixdlt.modules.RadixNodeModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.environment.rx.RxEnvironment;
import com.radixdlt.environment.rx.RxRemoteEnvironment;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageFromPeer;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.network.p2p.transport.PeerOutboundBootstrap;
import com.radixdlt.network.p2p.transport.PeerServerBootstrap;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.utils.properties.RuntimeProperties;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.sync.CommittedReader;
import io.reactivex.rxjava3.disposables.Disposable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import com.radixdlt.middleware2.network.Message;

@SuppressWarnings("unused")
public final class RadixShell {
  private static final Logger log = LogManager.getLogger();

  public static void log(String msg, Object... params) {
    log.info(msg, params);
  }

  public static NodeBuilder nodeBuilder() throws ParseException {
    return new NodeBuilder();
  }

  public static class NodeBuilder {
    private Network network = Network.LOCALNET;
    private RuntimeProperties properties;
    private int p2pServerPort = 0;
    private ImmutableSet.Builder<String> moduleRunnersBuilder = new ImmutableSet.Builder<>();
    private ImmutableMap.Builder<String, String> customProperties = new ImmutableMap.Builder<>();
    private Optional<String> dataDir = Optional.empty();
    private final String nodeKeyPass = System.getenv("RADIX_NODE_KEYSTORE_PASSWORD");

    public NodeBuilder() throws ParseException {
      properties = new RuntimeProperties(new JSONObject(), new String[] {});
    }

    public NodeBuilder p2pServer(int p2pServerPort) {
      this.p2pServerPort = p2pServerPort;
      moduleRunnersBuilder.add(Runners.P2P_NETWORK);
      properties.set("network.p2p.listen_port", p2pServerPort);
      properties.set("network.p2p.listen_address", "127.0.0.1");
      properties.set("network.p2p.seed_nodes", "");
      return this;
    }

    public NodeBuilder ledgerSync() {
      moduleRunnersBuilder.add(Runners.SYNC);
      return this;
    }

    public NodeBuilder network(Network network) {
      this.network = network;
      return this;
    }

    public NodeBuilder dataDir(String path) {
      this.dataDir = Optional.of(path);
      return this;
    }

    public NodeBuilder prop(String name, String value) {
      customProperties.put(name, value);
      return this;
    }

    public Node build() throws Exception {
      final File dataDir;
      if (this.dataDir.isPresent()) {
        dataDir = new File(this.dataDir.get());
        if (!dataDir.exists()) {
          dataDir.mkdirs();
        }
      } else {
        dataDir = new File(Files.createTempDirectory("radix-shell-node-").toString());
      }

      customProperties.build().forEach((k, v) -> properties.set(k, v));

      properties.set("db.location", dataDir.toString());

      if (properties.get("node.key.path", "").isEmpty()) {
        properties.set("node.key.path", new File(dataDir, "node-keystore.ks").getAbsolutePath());
      }

      if (properties.get("network.host_ip", "").isEmpty()) {
        properties.set("network.host_ip", "127.0.0.1");
      }

      log.info("Node data dir: {}", dataDir);

      final var nodeKeyFile = new File(properties.get("node.key.path"));
      if (!nodeKeyFile.exists()) {
        final var newKeyPair = ECKeyPair.generateNew();
        RadixKeyStore.fromFile(nodeKeyFile, nodeKeyPass.toCharArray(), true)
            .writeKeyPair("node", newKeyPair);
      }

      properties.set("network.id", network.getId());
      if (network.genesisTxn().isEmpty() && properties.get("network.genesis_txn", "").isEmpty()) {
        properties.set(
            "network.genesis_txn",
            Network.STOKENET.genesisTxn().get()); // default to stokenet genesis
      }

      final var injector = Guice.createInjector(new RadixNodeModule(properties));
      final var node = new Node(injector);

      moduleRunnersBuilder.build().forEach(node::startRunner);

      if (p2pServerPort > 0) {
        node.startP2PServer();
      }

      return node;
    }
  }

  public static final class Node {
    private final Injector injector;
    private final Map<String, ModuleRunner> moduleRunners;
    private final Set<Disposable> msgConsumers = new HashSet<>();
    private final Set<Disposable> eventConsumers = new HashSet<>();
    private final Set<Disposable> remoteEventConsumers = new HashSet<>();

    public Node(Injector injector) {
      this.injector = injector;
      this.moduleRunners =
          injector.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() {}));
    }

    public <T> T getInstance(Key<T> key) {
      return injector.getInstance(key);
    }

    public <T> T getInstance(Class<T> clz) {
      return injector.getInstance(clz);
    }

    public void startRunner(String runner) {
      moduleRunners.get(runner).start();
    }

    public void stopRunner(String runner) {
      moduleRunners.get(runner).stop();
    }

    public void startP2PServer() {
      final var peerServer = injector.getInstance(PeerServerBootstrap.class);
      try {
        peerServer.start();
        log.info("P2P server started: " + self());
      } catch (InterruptedException e) {
        log.error("Cannot start p2p server", e);
      }
    }

    public RadixNodeUri self() {
      return injector.getInstance(Key.get(RadixNodeUri.class, Self.class));
    }

    public void connectTo(String uri) throws DeserializeException {
      connectTo(RadixNodeUri.fromUri(URI.create(uri)));
    }

    public void connectTo(RadixNodeUri uri) {
      final var bootstrap = getInstance(PeerOutboundBootstrap.class);
      bootstrap.initOutboundConnection(uri);
    }

    public ImmutableList<PeerChannel> peers() {
      return ImmutableList.<PeerChannel>builder()
          .addAll(getInstance(PeerManager.class).activeChannels())
          .build();
    }

    @SuppressWarnings("unchecked")
    public <T> void dispatch(T t) {
      ((EventDispatcher<T>) injector.getInstance(Environment.class).getDispatcher(t.getClass()))
          .dispatch(t);
    }

    public <T> Disposable onEvent(Class<T> eventClass, Consumer<T> consumer) {
      final var disposable =
          injector
              .getInstance(RxEnvironment.class)
              .getObservable(eventClass)
              .subscribe(consumer::accept);

      eventConsumers.add(disposable);
      return disposable;
    }

    public void cleanEventConsumers() {
      eventConsumers.forEach(Disposable::dispose);
      eventConsumers.clear();
    }

    public <T> void dispatchRemote(PeerChannel receiver, T t) {
      dispatchRemote(BFTNode.create(receiver.getRemoteNodeId().getPublicKey()), t);
    }

    public <T> void dispatchRemote(RadixNodeUri receiver, T t) {
      dispatchRemote(BFTNode.create(receiver.getNodeId().getPublicKey()), t);
    }

    public <T> Disposable onRemoteEvent(Class<T> eventClass, Consumer<RemoteEvent<T>> consumer) {
      final var disposable =
          injector
              .getInstance(RxRemoteEnvironment.class)
              .remoteEvents(eventClass)
              .subscribe(consumer::accept);

      remoteEventConsumers.add(disposable);
      return disposable;
    }

    public void cleanRemoteEventConsumers() {
      remoteEventConsumers.forEach(Disposable::dispose);
      remoteEventConsumers.clear();
    }

    @SuppressWarnings("unchecked")
    public <T> void dispatchRemote(BFTNode receiver, T t) {
      ((RemoteEventDispatcher<T>)
              injector.getInstance(Environment.class).getRemoteDispatcher(t.getClass()))
          .dispatch(receiver, t);
    }

    public void sendMsg(RadixNodeUri uri, Message message) {
      sendMsg(uri.getNodeId(), message);
    }

    public void sendMsg(String nodeId, Message message) throws DeserializeException {
      final var addressing = injector.getInstance(Addressing.class);
      sendMsg(NodeId.fromPublicKey(addressing.forNodes().parse(nodeId)), message);
    }

    public void sendMsg(NodeId nodeId, Message message) {
      getInstance(MessageCentral.class).send(nodeId, message);
    }

    public <T extends Message> Disposable onMsg(
        Class<T> msgClass, Consumer<MessageFromPeer<T>> consumer) {
      final var disposable =
          getInstance(MessageCentral.class).messagesOf(msgClass).subscribe(consumer::accept);
      msgConsumers.add(disposable);
      return disposable;
    }

    public void cleanMsgConsumers() {
      msgConsumers.forEach(Disposable::dispose);
      msgConsumers.clear();
    }

    public void writeLedgerSyncToFile(String fileName) throws IOException {
      final var start = System.currentTimeMillis();
      LedgerFileSync.writeToFile(
          fileName, getInstance(Serialization.class), getInstance(CommittedReader.class));
      final var time = System.currentTimeMillis() - start;
      System.out.printf("Dump finished. Took %ss%n", time / 1000);
    }

    public void restoreLedgerFromFile(String fileName) throws IOException {
      final var start = System.currentTimeMillis();
      LedgerFileSync.restoreFromFile(
          fileName,
          getInstance(Serialization.class),
          getInstance(Key.get(new TypeLiteral<EventDispatcher<VerifiedTxnsAndProof>>() {})));
      final var time = System.currentTimeMillis() - start;
      System.out.printf("Restore finished. Took %ss%n", time / 1000);
    }

    @Override
    public String toString() {
      return self().toString();
    }
  }
}
