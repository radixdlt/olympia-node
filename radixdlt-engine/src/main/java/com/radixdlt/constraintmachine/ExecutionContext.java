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

package com.radixdlt.constraintmachine;

import com.radixdlt.application.tokens.scrypt.TokenHoldingBucket;
import com.radixdlt.application.tokens.scrypt.Tokens;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.DefaultedSystemLoanException;
import com.radixdlt.constraintmachine.exceptions.DepletedFeeReserveException;
import com.radixdlt.constraintmachine.exceptions.ExecutionContextDestroyException;
import com.radixdlt.constraintmachine.exceptions.InvalidPermissionException;
import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.MultipleFeeReserveDepositException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.exceptions.ResourceAllocationAndDestructionException;
import com.radixdlt.constraintmachine.exceptions.SignedSystemException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// TODO: Cleanup permissions to access to these methods
public final class ExecutionContext {
  private final Txn txn;
  private final PermissionLevel level;
  private final boolean skipAuthorization;
  private final TokenHoldingBucket reserve;
  private ECPublicKey key;
  private boolean disableResourceAllocAndDestroy;
  private UInt256 feeDeposit;
  private UInt256 systemLoan = UInt256.ZERO;
  private int sigsLeft;
  private boolean chargedOneTimeFee = false;
  private List<REEvent> events = new ArrayList<>();

  public ExecutionContext(Txn txn, PermissionLevel level, boolean skipAuthorization, int sigsLeft) {
    this.txn = txn;
    this.level = level;
    this.skipAuthorization = skipAuthorization;
    this.sigsLeft = sigsLeft;
    this.reserve = new TokenHoldingBucket(Tokens.create(REAddr.ofNativeToken(), UInt256.ZERO));
  }

  public boolean skipAuthorization() {
    return skipAuthorization;
  }

  public void addSystemLoan(UInt256 loan) {
    this.systemLoan = this.systemLoan.add(loan);
    try {
      this.reserve.deposit(Tokens.create(REAddr.ofNativeToken(), loan));
    } catch (InvalidResourceException e) {
      throw new IllegalStateException(e);
    }
  }

  public List<REEvent> getEvents() {
    return events;
  }

  public void emitEvent(REEvent event) {
    this.events.add(event);
  }

  public void resetSigs(int sigs) {
    this.sigsLeft = sigs;
  }

  public void sig() throws AuthorizationException {
    if (this.sigsLeft == 0) {
      throw new AuthorizationException("Used up all signatures allowed");
    }
    this.sigsLeft--;
  }

  public int sigsLeft() {
    return sigsLeft;
  }

  public Tokens withdrawFeeReserve(UInt256 amount)
      throws InvalidResourceException, NotEnoughResourcesException {
    return reserve.withdraw(REAddr.ofNativeToken(), amount);
  }

  public void depositFeeReserve(Tokens tokens)
      throws InvalidResourceException, MultipleFeeReserveDepositException {
    if (feeDeposit != null) {
      throw new MultipleFeeReserveDepositException();
    }
    reserve.deposit(tokens);
    feeDeposit = tokens.getAmount().getLow();
  }

  public void chargeOneTimeTransactionFee(Function<Txn, UInt256> feeComputer)
      throws DepletedFeeReserveException {
    if (chargedOneTimeFee) {
      return;
    }

    var fee = feeComputer.apply(txn);
    charge(fee);
    chargedOneTimeFee = true;
  }

  public void charge(UInt256 amount) throws DepletedFeeReserveException {
    try {
      reserve.withdraw(REAddr.ofNativeToken(), amount);
    } catch (InvalidResourceException e) {
      throw new IllegalStateException("Should not get here", e);
    } catch (NotEnoughResourcesException e) {
      throw new DepletedFeeReserveException(e);
    }
  }

  public void payOffLoan() throws DefaultedSystemLoanException {
    if (systemLoan.isZero()) {
      return;
    }

    try {
      charge(systemLoan);
    } catch (DepletedFeeReserveException e) {
      throw new DefaultedSystemLoanException(e, feeDeposit);
    }
    systemLoan = UInt256.ZERO;
  }

  public void verifyCanAllocAndDestroyResources() throws ResourceAllocationAndDestructionException {
    if (disableResourceAllocAndDestroy) {
      throw new ResourceAllocationAndDestructionException();
    }
  }

  public void setDisableResourceAllocAndDestroy(boolean disableResourceAllocAndDestroy) {
    this.disableResourceAllocAndDestroy = disableResourceAllocAndDestroy;
  }

  public void setKey(ECPublicKey key) {
    this.key = key;
  }

  public Optional<ECPublicKey> key() {
    return Optional.ofNullable(key);
  }

  public PermissionLevel permissionLevel() {
    return level;
  }

  public void verifyPermissionLevel(PermissionLevel requiredLevel)
      throws SignedSystemException, InvalidPermissionException {
    if (this.level.compareTo(requiredLevel) < 0) {
      throw new InvalidPermissionException(requiredLevel, level);
    }

    if (requiredLevel.compareTo(PermissionLevel.SUPER_USER) >= 0 && key != null) {
      throw new SignedSystemException();
    }
  }

  public void destroy() throws DefaultedSystemLoanException, ExecutionContextDestroyException {
    payOffLoan();

    if (!reserve.isEmpty()) {
      throw new ExecutionContextDestroyException(reserve);
    }
  }
}
