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

package com.radixdlt.harness.deterministic.actors;

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.api.core.openapitools.model.CommittedTransaction;
import com.radixdlt.api.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.openapitools.model.Operation;
import com.radixdlt.api.core.openapitools.model.OperationGroup;
import com.radixdlt.api.core.openapitools.model.ResourceAmount;
import com.radixdlt.api.core.openapitools.model.ResourceIdentifier;
import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.harness.deterministic.DeterministicActor;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reconciles the balances that are computed by reading the transaction stream and the /entity
 * endpoint
 */
public final class BalanceReconciler implements DeterministicActor {
  private final Map<EntityIdentifier, Map<ResourceIdentifier, BigInteger>> balances =
      new HashMap<>();
  private long currentStateVersion = 0L;

  private static Map<EntityIdentifier, Map<ResourceIdentifier, BigInteger>> balanceChanges(
      Stream<OperationGroup> operationGroups) {
    return operationGroups
        .flatMap(group -> group.getOperations().stream())
        .filter(op -> op.getAmount() != null)
        .collect(
            Collectors.groupingBy(
                Operation::getEntityIdentifier,
                Collectors.groupingBy(
                    op -> op.getAmount().getResourceIdentifier(),
                    Collectors.mapping(
                        op -> new BigInteger(op.getAmount().getValue()),
                        Collectors.reducing(BigInteger.ZERO, BigInteger::add)))));
  }

  @Override
  public String execute(MultiNodeDeterministicRunner runner, Random random) throws Exception {
    var injector = runner.getNode(0);
    var nodeApiClient = injector.getInstance(NodeApiClient.class);
    var transactions = new ArrayList<CommittedTransaction>();

    final long startingStateVersion = currentStateVersion;
    // Sync fully to ledger
    List<CommittedTransaction> loadedTransactions;
    do {
      loadedTransactions =
          nodeApiClient.getTransactions(currentStateVersion, random.nextLong(1, 10));
      transactions.addAll(loadedTransactions);
      currentStateVersion = currentStateVersion + loadedTransactions.size();
    } while (!loadedTransactions.isEmpty());

    var nodeStateVersion = nodeApiClient.getStateIdentifier().getStateVersion();
    assertThat(nodeStateVersion).isEqualTo(currentStateVersion);

    // Compute balance changes since last sync
    var balanceChanges =
        balanceChanges(transactions.stream().flatMap(txn -> txn.getOperationGroups().stream()));

    // Update balance states
    balanceChanges.forEach(
        (identifier, balanceMap) ->
            balanceMap.forEach(
                (resource, value) -> {
                  if (value.equals(BigInteger.ZERO)) {
                    return;
                  }

                  balances.merge(
                      identifier,
                      Map.of(resource, value),
                      (b0, b1) ->
                          Stream.concat(b0.entrySet().stream(), b1.entrySet().stream())
                              .collect(
                                  Collectors.groupingBy(
                                      Map.Entry::getKey,
                                      Collectors.mapping(
                                          Map.Entry::getValue,
                                          Collectors.reducing(BigInteger.ZERO, BigInteger::add))))
                              .entrySet()
                              .stream()
                              .filter(e -> !e.getValue().equals(BigInteger.ZERO))
                              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                }));

    // Verify that updated balance states match the node
    for (var entityIdentifier : balanceChanges.keySet()) {
      var myBalances = balances.getOrDefault(entityIdentifier, Map.of());
      var response = nodeApiClient.getEntity(entityIdentifier);
      assertThat(response.getStateIdentifier().getStateVersion()).isEqualTo(currentStateVersion);

      var nodeBalances =
          response.getBalances().stream()
              .collect(
                  Collectors.toMap(
                      ResourceAmount::getResourceIdentifier, r -> new BigInteger(r.getValue())));

      assertThat(nodeBalances)
          .describedAs("Balance of %s", entityIdentifier)
          .containsExactlyInAnyOrderEntriesOf(myBalances);
    }

    return String.format(
        "Okay{last_state_version=%s current_state_version=%s}",
        startingStateVersion, currentStateVersion);
  }
}
