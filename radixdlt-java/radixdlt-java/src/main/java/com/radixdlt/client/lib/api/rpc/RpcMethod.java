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

package com.radixdlt.client.lib.api.rpc;

import static com.radixdlt.client.lib.api.rpc.EndPoint.*;

public enum RpcMethod {
  TOKEN_NATIVE("get_native_token", TOKENS),
  TOKEN_INFO("get_info", TOKENS),

  ACCOUNT_BALANCES("get_balances", ACCOUNTS),
  ACCOUNT_HISTORY("get_transaction_history", ACCOUNTS),
  ACCOUNT_STAKES("get_stake_positions", ACCOUNTS),
  ACCOUNT_UNSTAKES("get_unstake_positions", ACCOUNTS),

  TRANSACTION_LOOKUP("lookup_transaction", TRANSACTIONS),
  TRANSACTION_STATUS("get_transaction_status", TRANSACTIONS),

  NETWORK_ID("get_id", NETWORK),
  NETWORK_THROUGHPUT("get_throughput", NETWORK),
  NETWORK_DEMAND("get_demand", NETWORK),

  VALIDATORS_LIST("get_next_epoch_set", ACCOUNTS),
  VALIDATORS_LOOKUP("lookup_validator", ACCOUNTS),

  CONSTRUCTION_BUILD("build_transaction", CONSTRUCTION),
  CONSTRUCTION_FINALIZE("finalize_transaction", CONSTRUCTION),
  CONSTRUCTION_SUBMIT("submit_transaction", CONSTRUCTION),

  NETWORK_CONFIG("networking.get_configuration", SYSTEM_NODE),
  NETWORK_PEERS("networking.get_peers", SYSTEM_NODE),
  NETWORK_DATA("networking.get_data", SYSTEM_NODE),
  NETWORK_ADDRESS_BOOK("networking.get_address_book", SYSTEM_NODE),

  TRANSACTION_LIST("get_transactions", TRANSACTIONS_NODE),

  API_CONFIGURATION("api.get_configuration", SYSTEM_NODE),
  API_DATA("api.get_data", SYSTEM_NODE),

  BFT_CONFIGURATION("bft.get_configuration", SYSTEM_NODE),
  BFT_DATA("bft.get_data", SYSTEM_NODE),

  MEMPOOL_CONFIGURATION("mempool.get_configuration", SYSTEM_NODE),
  MEMPOOL_DATA("mempool.get_data", SYSTEM_NODE),

  LEDGER_PROOF("ledger.get_latest_proof", SYSTEM_NODE),
  LEDGER_EPOCH_PROOF("ledger.get_latest_epoch_proof", SYSTEM_NODE),
  LEDGER_CHECKPOINTS("checkpoints.get_checkpoints", SYSTEM_NODE),

  RADIX_ENGINE_CONFIGURATION("radix_engine.get_configuration", SYSTEM_NODE),
  RADIX_ENGINE_DATA("radix_engine.get_data", SYSTEM_NODE),

  SYNC_CONFIGURATION("sync.get_configuration", SYSTEM_NODE),
  SYNC_DATA("sync.get_data", SYSTEM_NODE),

  VALIDATION_NODE_INFO("get_node_info", VALIDATION_NODE),
  VALIDATION_CURRENT_EPOCH("get_current_epoch_data", VALIDATION_NODE),

  ACCOUNT_INFO("get_info", ACCOUNT_NODE),
  ACCOUNT_SUBMIT_SINGLE_STEP("submit_transaction_single_step", ACCOUNT_NODE);

  private final String method;
  private final EndPoint endPoint;

  RpcMethod(String method, EndPoint endPoint) {
    this.method = method;
    this.endPoint = endPoint;
  }

  public String method() {
    return method;
  }

  public EndPoint endPoint() {
    return endPoint;
  }
}
