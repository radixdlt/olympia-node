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

import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.UnclaimedREAddr;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.system.state.VirtualParent;
import com.radixdlt.application.tokens.state.ExitingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorFeeCopy;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.constraintmachine.Particle;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum SubstateTypeId {
  VIRTUAL_PARENT((byte) 0x0, VirtualParent.class),
  UNCLAIMED_READDR((byte) 0x1, UnclaimedREAddr.class),
  ROUND_DATA((byte) 0x2, RoundData.class),
  EPOCH_DATA((byte) 0x3, EpochData.class),
  TOKEN_RESOURCE((byte) 0x4, TokenResource.class),
  TOKEN_RESOURCE_METADATA((byte) 0x5, TokenResourceMetadata.class),
  TOKENS((byte) 0x6, TokensInAccount.class),
  PREPARED_STAKE((byte) 0x7, PreparedStake.class),
  STAKE_OWNERSHIP((byte) 0x8, StakeOwnership.class),
  PREPARED_UNSTAKE((byte) 0x9, PreparedUnstakeOwnership.class),
  EXITING_STAKE((byte) 0xa, ExitingStake.class),
  VALIDATOR_META_DATA((byte) 0xb, ValidatorMetaData.class),
  VALIDATOR_STAKE_DATA((byte) 0xc, ValidatorStakeData.class),
  VALIDATOR_BFT_DATA((byte) 0xd, ValidatorBFTData.class),
  VALIDATOR_ALLOW_DELEGATION_FLAG((byte) 0xe, AllowDelegationFlag.class),
  VALIDATOR_REGISTERED_FLAG_COPY((byte) 0xf, ValidatorRegisteredCopy.class),
  VALIDATOR_RAKE_COPY((byte) 0x10, ValidatorFeeCopy.class),
  VALIDATOR_OWNER_COPY((byte) 0x11, ValidatorOwnerCopy.class),
  VALIDATOR_SYSTEM_META_DATA((byte) 0x12, ValidatorSystemMetadata.class);

  private final byte id;
  private final Class<? extends Particle> substateClass;

  private static final Map<Byte, SubstateTypeId> substateTypes;

  static {
    substateTypes =
        Arrays.stream(SubstateTypeId.values()).collect(Collectors.toMap(e -> e.id, e -> e));
  }

  SubstateTypeId(byte id, Class<? extends Particle> substateClass) {
    this.id = id;
    this.substateClass = substateClass;
  }

  public static SubstateTypeId valueOf(byte typeId) {
    var substateType = substateTypes.get(typeId);
    if (substateType == null) {
      throw new IllegalArgumentException("Unknown typeId " + typeId);
    }
    return substateType;
  }

  public static SubstateTypeId valueOf(Class<? extends Particle> substateClass) {
    for (var substateType : SubstateTypeId.values()) {
      if (substateType.substateClass.equals(substateClass)) {
        return substateType;
      }
    }
    throw new IllegalArgumentException("Unknown substate class " + substateClass);
  }

  public byte id() {
    return id;
  }

  public Class<? extends Particle> getSubstateClass() {
    return substateClass;
  }
}
