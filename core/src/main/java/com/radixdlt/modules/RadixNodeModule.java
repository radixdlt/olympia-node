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

package com.radixdlt.modules;

import static org.apache.logging.log4j.util.Strings.isNotBlank;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.api.ApiModule;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.rx.RxEnvironmentModule;
import com.radixdlt.keys.PersistedBFTKeyModule;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolReceiverModule;
import com.radixdlt.mempool.MempoolRelayerModule;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.network.messaging.MessagingModule;
import com.radixdlt.network.p2p.P2PModule;
import com.radixdlt.network.p2p.PeerDiscoveryModule;
import com.radixdlt.network.p2p.PeerLivenessMonitorModule;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.utils.properties.RuntimeProperties;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineStateComputerModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisBuilder;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.statecomputer.forks.ForkOverwritesFromPropertiesModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.StokenetForksModule;
import com.radixdlt.statecomputer.forks.testing.TestingForksLoader;
import com.radixdlt.store.DatabasePropertiesModule;
import com.radixdlt.store.PersistenceModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.Bytes;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONObject;
import com.radixdlt.utils.IOUtils;

/** Module which manages everything in a single node */
public final class RadixNodeModule extends AbstractModule {
  private static final String TESTING_FORKS_VERSION_KEY = "testing_forks.version";
  private static final int DEFAULT_CORE_PORT = 3333;
  private static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
  private static final Logger log = LogManager.getLogger();

  private final RuntimeProperties properties;
  private final int networkId;

  public RadixNodeModule(RuntimeProperties properties) {
    this.properties = properties;
    this.networkId =
        Optional.ofNullable(properties.get("network.id"))
            .map(Integer::parseInt)
            .orElseThrow(() -> new IllegalStateException("Must specify network.id"));
  }

  @Provides
  @Genesis
  @Singleton
  VerifiedTxnsAndProof genesis(@Genesis Txn genesis, GenesisBuilder genesisBuilder)
      throws RadixEngineException {
    var proof = genesisBuilder.generateGenesisProof(genesis);
    return VerifiedTxnsAndProof.create(List.of(genesis), proof);
  }

  private Txn loadGenesisFile(String genesisFile) {
    try (var genesisJsonString = new FileInputStream(genesisFile)) {
      var genesisJson = new JSONObject(IOUtils.toString(genesisJsonString));
      var genesisHex = genesisJson.getString("genesis");
      return Txn.create(Bytes.fromHexString(genesisHex));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Txn loadGenesis(int networkId) {
    var genesisTxnHex = properties.get("network.genesis_txn");
    var genesisFile = properties.get("network.genesis_file");

    var network = Network.ofId(networkId);
    var networkGenesis =
        network.flatMap(Network::genesisTxn).map(Bytes::fromHexString).map(Txn::create);

    if (networkGenesis.isPresent()) {
      validateGenesisConfigIsMissing(genesisTxnHex, genesisFile, network.get());

      if (Strings.isNotBlank(genesisFile)) {
        throw new IllegalStateException(
            "Cannot provide genesis file for well-known network " + network.orElseThrow());
      }
      return networkGenesis.get();
    } else {
      validateGenesisConfigIsPresent(genesisTxnHex, genesisFile);

      return isNotBlank(genesisTxnHex)
          ? Txn.create(Bytes.fromHexString(genesisTxnHex))
          : loadGenesisFile(genesisFile);
    }
  }

  private void validateGenesisConfigIsPresent(String genesisTxnHex, String genesisFile) {
    var genesisCount = 0;
    genesisCount += isNotBlank(genesisTxnHex) ? 1 : 0;
    genesisCount += isNotBlank(genesisFile) ? 1 : 0;

    if (genesisCount > 1) {
      throw new IllegalStateException("Multiple genesis txn specified.");
    }

    if (genesisCount == 0) {
      throw new IllegalStateException("No genesis txn specified.");
    }
  }

  private void validateGenesisConfigIsMissing(
      String genesisTxnHex, String genesisFile, Network network) {
    if (isNotBlank(genesisTxnHex) || isNotBlank(genesisFile)) {
      throw new IllegalStateException(
          "Cannot provide genesis txn for well-known network " + network);
    }
  }

  @Override
  protected void configure() {
    if (this.networkId <= 0) {
      throw new IllegalStateException("Illegal networkId " + networkId);
    }

    var addressing = Addressing.ofNetworkId(networkId);
    bind(Addressing.class).toInstance(addressing);
    bindConstant().annotatedWith(NetworkId.class).to(networkId);
    bind(Txn.class).annotatedWith(Genesis.class).toInstance(loadGenesis(networkId));
    bind(RuntimeProperties.class).toInstance(properties);

    // Consensus configuration
    // These cannot be changed without introducing possibilities of
    // going out of sync with consensus.
    bindConstant()
        .annotatedWith(BFTSyncPatienceMillis.class)
        .to(properties.get("bft.sync.patience", 200));

    // Default values mean that pacemakers will sync if they are within 5 views of each other.
    // 5 consecutive failing views will take 1*(2^6)-1 seconds = 63 seconds.
    bindConstant().annotatedWith(PacemakerTimeout.class).to(3000L);
    bindConstant().annotatedWith(PacemakerRate.class).to(1.1);
    bindConstant().annotatedWith(PacemakerMaxExponent.class).to(0);

    // Mempool configuration
    var mempoolMaxSize = properties.get("mempool.maxSize", 10000);
    install(MempoolConfig.asModule(mempoolMaxSize, 5, 60000, 60000, 100));

    // Sync configuration
    final long syncPatience = properties.get("sync.patience", 5000L);
    bind(SyncConfig.class).toInstance(SyncConfig.of(syncPatience, 10, 3000L));

    // System (e.g. time, random)
    install(new SystemModule());

    install(new RxEnvironmentModule());

    install(new EventLoggerModule());
    install(new DispatcherModule());

    // Consensus
    install(new PersistedBFTKeyModule());
    install(new CryptoModule());
    install(new ConsensusModule());

    // Ledger
    install(new LedgerModule());
    install(new MempoolReceiverModule());

    // Mempool Relay
    install(new MempoolRelayerModule());

    // Sync
    install(new SyncServiceModule());

    // Epochs - Consensus
    install(new EpochsConsensusModule());
    // Epochs - Sync
    install(new EpochsSyncModule());

    // State Computer
    install(new ForksModule());

    if (networkId == Network.MAINNET.getId()) {
      log.info("Using mainnet forks");
      install(new MainnetForksModule());
    } else if (properties.get("testing_forks.enable", false)) {
      String testingForkConfigName =
          properties.get("testing_forks.fork_config_name", "TestingForksModuleV1");
      if (testingForkConfigName.isBlank()) {
        testingForkConfigName = "TestingForksModuleV1";
      }
      log.info("Using testing fork config '{}'", testingForkConfigName);
      install(
          new TestingForksLoader()
              .createTestingForksModuleConfigFromClassName(testingForkConfigName));
    } else {
      log.info("Using stokenet forks");
      install(new StokenetForksModule());
    }

    if (properties.get("overwrite_forks.enable", false)) {
      log.info("Enabling fork overwrites");
      install(new ForkOverwritesFromPropertiesModule());
    }
    install(new RadixEngineStateComputerModule());
    install(new RadixEngineModule());
    install(new RadixEngineStoreModule());

    // Checkpoints
    install(new RadixEngineCheckpointModule());

    // Storage
    install(new DatabasePropertiesModule());
    install(new PersistenceModule());
    install(new ConsensusRecoveryModule());
    install(new LedgerRecoveryModule());

    // System Info
    install(new SystemInfoModule());

    // Network
    install(new MessagingModule());
    install(new MessageCentralModule(properties));
    install(new HostIpModule(properties));
    install(new P2PModule(properties));
    install(new PeerDiscoveryModule());
    install(new PeerLivenessMonitorModule());

    // API
    var bindAddress = properties.get("api.bind.address", DEFAULT_BIND_ADDRESS);
    var port = properties.get("api.port", DEFAULT_CORE_PORT);
    var enableTransactions = properties.get("api.transactions.enable", false);
    var enableSign = properties.get("api.sign.enable", false);
    install(new ApiModule(bindAddress, port, enableTransactions, enableSign));
  }
}
