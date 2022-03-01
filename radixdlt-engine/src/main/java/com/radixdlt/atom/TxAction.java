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

import static com.radixdlt.utils.Strings.asEmptyIfNull;
import static java.util.Objects.requireNonNull;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongFunction;

/** RadixEngine actions. */
public sealed interface TxAction {
  record BurnToken(REAddr resourceAddr, REAddr fromAddr, UInt256 amount) implements TxAction {
    public BurnToken {
      ensureNonZeroAmount(amount);
    }
  }

  record CreateFixedToken(
      REAddr resourceAddr,
      REAddr accountAddr,
      String symbol,
      String name,
      String description,
      String iconUrl,
      String tokenUrl,
      UInt256 supply)
      implements TxAction {
    public CreateFixedToken {
      ensureResourceAddressIsHashedKey(resourceAddr);
      ensureNonZeroSupply(supply);
      requireNonNull(accountAddr);
      requireNonNull(symbol);
      requireNonNull(name);
      requireNonNull(description);
      requireNonNull(iconUrl);
      requireNonNull(tokenUrl);
    }
  }

  record CreateMutableToken(
      REAddr resourceAddress,
      String symbol,
      String name,
      String description,
      String iconUrl,
      String tokenUrl,
      ECPublicKey owner)
      implements TxAction {
    public CreateMutableToken(
        REAddr resourceAddress,
        String symbol,
        String name,
        String description,
        String iconUrl,
        String tokenUrl,
        ECPublicKey owner) {
      this.resourceAddress = resourceAddress;
      this.symbol = symbol.toLowerCase();
      this.owner = owner;

      this.name = asEmptyIfNull(name);
      this.description = asEmptyIfNull(description);
      this.iconUrl = asEmptyIfNull(iconUrl);
      this.tokenUrl = asEmptyIfNull(tokenUrl);
    }
  }

  record CreateSystem(long timestamp) implements TxAction {}

  record FeeReserveComplete(REAddr toAddr) implements TxAction {
    public FeeReserveComplete {
      ensureAddressIsAccount(toAddr);
    }
  }

  record FeeReservePut(REAddr fromAddr, UInt256 amount) implements TxAction {}

  record FeeReserveTake(REAddr toAddr, UInt256 amount) implements TxAction {}

  record MintToken(REAddr resourceAddr, REAddr toAddr, UInt256 amount) implements TxAction {
    public MintToken {
      ensureNonZeroAmount(amount);
    }
  }

  record NextEpoch(long timestamp) implements TxAction {}

  record NextRound(
      long view, boolean isTimeout, long timestamp, LongFunction<ECPublicKey> leaderMapping)
      implements TxAction {}

  record RegisterValidator(ECPublicKey validatorKey) implements TxAction {
    @Override
    public String toString() {
      return String.format("%s{key=%s}", this.getClass().getSimpleName(), validatorKey.toHex());
    }
  }

  record SplitToken(REAddr rri, REAddr userAcct, UInt256 minSize) implements TxAction {}

  record StakeTokens(REAddr fromAddr, ECPublicKey toDelegate, UInt256 amount) implements TxAction {
    public StakeTokens {
      ensureNonZeroAmount(amount);
    }
  }

  record TransferToken(REAddr resourceAddr, REAddr fromAddr, REAddr toAddr, UInt256 amount)
      implements TxAction {
    public TransferToken {
      ensureNonZeroAmount(amount);
    }
  }

  record UnregisterValidator(ECPublicKey validatorKey) implements TxAction {
    public UnregisterValidator {
      requireNonNull(validatorKey);
    }
  }

  record UnstakeOwnership(REAddr accountAddr, ECPublicKey fromDelegate, UInt256 amount)
      implements TxAction {
    public UnstakeOwnership {
      ensureNonZeroAmount(amount);
    }
  }

  record UnstakeTokens(ECPublicKey fromDelegate, REAddr accountAddr, UInt256 amount)
      implements TxAction {
    public UnstakeTokens {
      ensureNonZeroAmount(amount);
    }
  }

  record UpdateAllowDelegationFlag(ECPublicKey validatorKey, boolean allowDelegation)
      implements TxAction {}

  /**
   * @see com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt for details
   */
  record UpdateValidatorFee(ECPublicKey validatorKey, int feePercentage) implements TxAction {}

  record UpdateValidatorMetadata(ECPublicKey validatorKey, String name, String url)
      implements TxAction {
    public UpdateValidatorMetadata {
      requireNonNull(validatorKey);
    }
  }

  record UpdateValidatorOwner(ECPublicKey validatorKey, REAddr ownerAddress) implements TxAction {}

  record UpdateValidatorSystemMetadata(ECPublicKey validatorKey, byte[] bytes) implements TxAction {
    public UpdateValidatorSystemMetadata {
      requireNonNull(validatorKey);
    }

    // Special case - class contains array,
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      return (o instanceof UpdateValidatorSystemMetadata that)
          && validatorKey.equals(that.validatorKey)
          && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(validatorKey);
      result = 31 * result + Arrays.hashCode(bytes);
      return result;
    }

    @Override
    public String toString() {
      return String.format(
          "%s{key=%s, bytes=%s}",
          this.getClass().getSimpleName(),
          validatorKey.toHex(),
          bytes == null ? "null" : Bytes.toHexString(bytes));
    }
  }

  static void ensureAddressIsAccount(REAddr toAddr) {
    if (!toAddr.isAccount()) {
      throw new IllegalArgumentException("Address must be an account");
    }
  }

  static void ensureResourceAddressIsHashedKey(REAddr resourceAddr) {
    if (resourceAddr.getType() != REAddr.REAddrType.HASHED_KEY) {
      throw new IllegalArgumentException("Invalid resource address.");
    }
  }

  static void ensureNonZeroAmount(UInt256 amount) {
    ensureNonZeroValue(amount, "Amount");
  }

  static void ensureNonZeroSupply(UInt256 supply) {
    ensureNonZeroValue(supply, "Supply");
  }

  static void ensureNonZeroValue(UInt256 value, String name) {
    if (value.isZero()) {
      throw new IllegalArgumentException(name + " must be > 0.");
    }
  }
}
