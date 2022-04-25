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

package com.radixdlt.api.system.prometheus;

import static com.radixdlt.api.system.prometheus.PrometheusService.JmxMetric.jmxMetric;
import static com.radixdlt.RadixNodeApplication.SYSTEM_VERSION_KEY;
import static com.radixdlt.RadixNodeApplication.VERSION_STRING_KEY;

import com.google.inject.Inject;
import com.radixdlt.api.service.EngineStatusService;
import com.radixdlt.api.system.health.HealthInfoService;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.REEvent.ValidatorBFTDataEvent;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.properties.RuntimeProperties;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.monitoring.InMemorySystemInfo;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeDataSupport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.radixdlt.RadixNodeApplication;

public class PrometheusService {
  private static final Logger log = LogManager.getLogger();

  private static final List<CounterType> EXPORT_LIST = List.of(CounterType.values());

  public static final String USAGE = "Usage";
  private static final List<JmxMetric> JMX_METRICS =
      List.of(
          jmxMetric("java.lang:type=MemoryPool,name=G1 Eden Space", USAGE),
          jmxMetric("java.lang:type=MemoryPool,name=G1 Survivor Space", USAGE),
          jmxMetric("java.lang:type=MemoryPool,name=G1 Old Gen", USAGE),
          jmxMetric("java.lang:type=MemoryPool,name=Metaspace", USAGE),
          jmxMetric("java.lang:type=GarbageCollector,name=G1 Old Generation", USAGE),
          jmxMetric("java.lang:type=GarbageCollector,name=G1 Young Generation", USAGE),
          jmxMetric(
              "java.lang:type=OperatingSystem",
              "SystemCpuLoad",
              "ProcessCpuLoad",
              "SystemLoadAverage"),
          jmxMetric("java.lang:type=Threading", "ThreadCount", "DaemonThreadCount"),
          jmxMetric("java.lang:type=Memory", "HeapMemoryUsage", "NonHeapMemoryUsage"),
          jmxMetric("java.lang:type=ClassLoading", "LoadedClassCount"));

  private static final String COUNTER = "counter";
  private static final String COUNTER_PREFIX = "info_counters_";
  private static final String COMPLETED_PROPOSALS =
      COUNTER_PREFIX + "radix_engine_cur_epoch_completed_proposals";
  private static final String MISSED_PROPOSALS =
      COUNTER_PREFIX + "radix_engine_cur_epoch_missed_proposals";

  private final SystemCounters systemCounters;
  private final HealthInfoService healthInfoService;
  private final Addressing addressing;
  private final InMemorySystemInfo inMemorySystemInfo;
  private final BFTNode self;
  private final Map<String, Boolean> endpointStatuses;
  private final PeersView peersView;
  private final CurrentForkView currentForkView;
  private final EngineStatusService engineStatusService;

  @Inject
  public PrometheusService(
      RuntimeProperties properties,
      SystemCounters systemCounters,
      PeersView peersView,
      HealthInfoService healthInfoService,
      InMemorySystemInfo inMemorySystemInfo,
      @Self BFTNode self,
      Addressing addressing,
      CurrentForkView currentForkView,
      EngineStatusService engineStatusService) {
    boolean enableTransactions = properties.get("api.transactions.enable", false);
    this.endpointStatuses = Map.of("transactions", enableTransactions);
    this.systemCounters = systemCounters;
    this.peersView = peersView;
    this.healthInfoService = healthInfoService;
    this.inMemorySystemInfo = inMemorySystemInfo;
    this.self = self;
    this.addressing = addressing;
    this.currentForkView = currentForkView;
    this.engineStatusService = engineStatusService;
  }

  public String getMetrics() {
    var builder = new StringBuilder();

    exportCounters(builder);
    exportSystemInfo(builder);

    return builder.append('\n').toString();
  }

  private void exportSystemInfo(StringBuilder builder) {
    var currentEpochView = inMemorySystemInfo.getCurrentView();

    appendCounter(
        builder, "info_epochmanager_currentview_view", currentEpochView.getView().number());
    appendCounter(builder, "info_epochmanager_currentview_epoch", currentEpochView.getEpoch());
    appendCounter(builder, "total_peers", peersView.peers().count());

    var totalValidators =
        inMemorySystemInfo
            .getEpochProof()
            .getNextValidatorSet()
            .map(BFTValidatorSet::getValidators)
            .map(AbstractCollection::size)
            .orElse(0);

    appendCounter(builder, "total_validators", totalValidators);

    appendCandidateForkRemainingEpochs(builder);
    appendCandidateForkVotingResult(builder);

    appendJMXCounters(builder);

    appendCounterExtended(
        builder,
        prepareNodeInfo(),
        "nodeinfo",
        "Special metric used to convey information about the current node using labels. Value will"
            + " always be 0.",
        0.0);
  }

  private void appendCandidateForkRemainingEpochs(StringBuilder builder) {
    final var candidateForkRemainingEpochs =
        engineStatusService.getCandidateForkRemainingEpochs().orElse(0L);

    appendCounter(builder, "candidate_fork_remaining_epochs", candidateForkRemainingEpochs);
  }

  private void appendCandidateForkVotingResult(StringBuilder builder) {
    appendCounter(
        builder,
        "candidate_fork_voting_result_percentage",
        engineStatusService.getCandidateForkVotingResultPercentage());
  }

  private String prepareNodeInfo() {
    var builder = new StringBuilder("nodeinfo{");
    addEndpontStatuses(builder);
    appendField(
        builder,
        "owner_address",
        addressing.forAccounts().of(REAddr.ofPubKeyAccount(self.getKey())));
    addBranchAndCommit(builder);
    addValidatorAddress(builder);
    addCurrentFork(builder);
    appendField(builder, "health", healthInfoService.nodeStatus().name());
    appendField(builder, "key", self.getKey().toHex());

    return builder.append("}").toString();
  }

  private void addValidatorAddress(StringBuilder builder) {
    appendField(builder, "own_validator_address", addressing.forValidators().of(self.getKey()));

    var inSet =
        inMemorySystemInfo
            .getEpochProof()
            .getNextValidatorSet()
            .map(set -> set.containsNode(self))
            .orElse(false);

    appendField(builder, "is_in_validator_set", inSet);
  }

  private void addBranchAndCommit(StringBuilder builder) {
    var branchAndCommit = RadixNodeApplication.systemVersionInfo().get(SYSTEM_VERSION_KEY).get(VERSION_STRING_KEY);
    appendField(builder, "branch_and_commit", branchAndCommit);
  }

  private void addCurrentFork(StringBuilder builder) {
    appendField(builder, "current_fork_name", currentForkView.currentForkConfig().name());
  }

  private void addEndpontStatuses(StringBuilder builder) {
    endpointStatuses.forEach((name, enabled) -> appendField(builder, name + "_enabled", enabled));
  }

  private void appendField(StringBuilder builder, String name, Object value) {
    builder.append(name).append("=\"").append(value).append("\",");
  }

  private void exportCounters(StringBuilder builder) {
    EXPORT_LIST.forEach(counterType -> generateCounterEntry(counterType, builder));

    inMemorySystemInfo
        .getValidatorBFTData()
        .ifPresent(proposals -> addProposalsCounters(builder, proposals));
  }

  private void addProposalsCounters(StringBuilder builder, ValidatorBFTDataEvent proposals) {
    appendCounter(builder, COMPLETED_PROPOSALS, proposals.completedProposals());
    appendCounter(builder, MISSED_PROPOSALS, proposals.missedProposals());
  }

  private void generateCounterEntry(CounterType counterType, StringBuilder builder) {
    var name = COUNTER_PREFIX + counterType.jsonPath().replace('.', '_');

    long value = systemCounters.get(counterType);

    appendCounter(builder, name, value);
  }

  private static void appendCounter(StringBuilder builder, String name, Number value) {
    appendCounterExtended(builder, name, name, name, value.doubleValue());
  }

  private static void appendCounterExtended(
      StringBuilder builder, String name, String type, String help, Object value) {
    builder
        .append("# HELP ")
        .append(help)
        .append('\n')
        .append("# TYPE ")
        .append(type)
        .append(' ')
        .append(COUNTER)
        .append('\n')
        .append(name)
        .append(' ')
        .append(value)
        .append('\n');
  }

  static class JmxMetric {
    private final String objectNameString;
    private final String[] metricAttributes;

    private JmxMetric(String objectNameString, String[] metricAttributes) {
      this.objectNameString = objectNameString;
      this.metricAttributes = metricAttributes;
    }

    static JmxMetric jmxMetric(String objectName, String... attributes) {
      return new JmxMetric(objectName, attributes);
    }

    void readCounter(MBeanServerConnection connection, StringBuilder builder) {
      try {
        var objectName =
            connection.queryNames(new ObjectName(objectNameString), null).iterator().next();

        var attributes = connection.getAttributes(objectName, metricAttributes).asList();

        for (var attribute : attributes) {
          var name = attribute.getName();

          if (name.equals(USAGE)) {
            name = objectName.getKeyProperty("name");
          }

          var outName = name.toLowerCase(Locale.US).replace('.', '_').replace(' ', '_');

          // this might break if more beans are parsed
          if (attribute.getValue() instanceof CompositeDataSupport cds) {
            appendCounter(builder, outName + "_init", (Number) cds.get("init"));
            appendCounter(builder, outName + "_max", (Number) cds.get("max"));
            appendCounter(builder, outName + "_committed", (Number) cds.get("committed"));
            appendCounter(builder, outName + "_used", (Number) cds.get("used"));
          } else {
            appendCounter(builder, outName, (Number) attribute.getValue());
          }
        }
      } catch (InstanceNotFoundException
          | ReflectionException
          | IOException
          | MalformedObjectNameException e) {
        log.error("Error while retrieving JMX metric " + objectNameString, e);
      }
    }
  }

  private void appendJMXCounters(StringBuilder builder) {
    var connection = ManagementFactory.getPlatformMBeanServer();
    JMX_METRICS.forEach(metric -> metric.readCounter(connection, builder));
  }
}
