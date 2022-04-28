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

package com.radixdlt.atom;

import static com.radixdlt.atom.TxAction.*;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TxnConstructionRequest {
  private boolean disableResourceAllocAndDestroy = false;
  private final List<TxAction> actions = new ArrayList<>();
  private byte[] msg = null;
  private Set<SubstateId> toAvoid;
  private REAddr feePayer;

  private TxnConstructionRequest() {}

  public static TxnConstructionRequest create() {
    return new TxnConstructionRequest();
  }

  public TxnConstructionRequest feePayer(REAddr feePayer) {
    this.feePayer = feePayer;
    return this;
  }

  public Optional<REAddr> getFeePayer() {
    return Optional.ofNullable(feePayer);
  }

  public Optional<byte[]> getMsg() {
    return Optional.ofNullable(msg);
  }

  public TxnConstructionRequest msg(byte[] msg) {
    this.msg = msg;
    return this;
  }

  public boolean isDisableResourceAllocAndDestroy() {
    return disableResourceAllocAndDestroy;
  }

  public TxnConstructionRequest disableResourceAllocAndDestroy() {
    this.disableResourceAllocAndDestroy = true;
    return this;
  }

  public TxnConstructionRequest action(TxAction txAction) {
    actions.add(txAction);
    return this;
  }

  public TxnConstructionRequest actions(List<TxAction> actions) {
    this.actions.addAll(actions);
    return this;
  }

  public TxnConstructionRequest registerAsValidator(ECPublicKey validatorKey) {
    var action = new RegisterValidator(validatorKey);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest updateValidatorMetadata(
      ECPublicKey validatorKey, String name, String uri) {
    var action = new UpdateValidatorMetadata(validatorKey, name, uri);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest updateValidatorSystemMetadata(
      ECPublicKey validatorKey, HashCode bytes) {
    final var action = new UpdateValidatorSystemMetadata(validatorKey, bytes.asBytes());
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest unregisterAsValidator(ECPublicKey validatorKey) {
    var action = new UnregisterValidator(validatorKey);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest splitNative(REAddr rri, REAddr userAcct, UInt256 minSize) {
    var action = new SplitToken(rri, userAcct, minSize);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest transfer(REAddr rri, REAddr from, REAddr to, UInt256 amount) {
    var action = new TransferToken(rri, from, to, amount);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest mint(REAddr rri, REAddr to, UInt256 amount) {
    var action = new MintToken(rri, to, amount);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest payFee(REAddr from, UInt256 amount) {
    var action = new FeeReservePut(from, amount);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest burn(REAddr rri, REAddr from, UInt256 amount) {
    var action = new BurnToken(rri, from, amount);
    actions.add(action);
    return this;
  }

  public TxnConstructionRequest avoidSubstates(Set<SubstateId> toAvoid) {
    this.toAvoid = toAvoid;
    return this;
  }

  public Set<SubstateId> getSubstatesToAvoid() {
    return this.toAvoid == null ? Set.of() : this.toAvoid;
  }

  public List<TxAction> getActions() {
    return actions;
  }

  @Override
  public String toString() {
    return String.format("%s{actions=%s}", this.getClass().getSimpleName(), actions);
  }
}
