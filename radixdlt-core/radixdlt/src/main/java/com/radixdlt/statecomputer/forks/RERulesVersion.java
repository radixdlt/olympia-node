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

package com.radixdlt.statecomputer.forks;

import static com.radixdlt.atom.TxAction.*;

import com.radixdlt.application.misc.SplitTokenConstructor;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.construction.FeeReserveCompleteConstructor;
import com.radixdlt.application.system.construction.FeeReservePutConstructor;
import com.radixdlt.application.system.construction.NextEpochConstructorV3;
import com.radixdlt.application.system.construction.NextViewConstructorV3;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.construction.BurnTokenConstructor;
import com.radixdlt.application.tokens.construction.CreateFixedTokenConstructor;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.construction.UnstakeOwnershipConstructor;
import com.radixdlt.application.tokens.construction.UnstakeTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.unique.scrypt.MutexConstraintScrypt;
import com.radixdlt.application.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.application.validators.construction.UnregisterValidatorConstructor;
import com.radixdlt.application.validators.construction.UpdateAllowDelegationFlagConstructor;
import com.radixdlt.application.validators.construction.UpdateRakeConstructor;
import com.radixdlt.application.validators.construction.UpdateValidatorMetadataConstructor;
import com.radixdlt.application.validators.construction.UpdateValidatorOwnerConstructor;
import com.radixdlt.application.validators.construction.UpdateValidatorSystemMetadataConstructor;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.application.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateOwnerConstraintScrypt;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.meter.Meter;
import com.radixdlt.constraintmachine.meter.Meters;
import com.radixdlt.constraintmachine.meter.SigsPerRoundMeter;
import com.radixdlt.constraintmachine.meter.TxnSizeFeeMeter;
import com.radixdlt.constraintmachine.meter.UpSubstateFeeMeter;
import com.radixdlt.engine.PostProcessor;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.CandidateForkVotesPostProcessor;
import com.radixdlt.statecomputer.EpochProofVerifierV2;

public enum RERulesVersion {
  OLYMPIA_V1 {
    @Override
    public RERules create(RERulesConfig config) {
      var maxRounds = config.maxRounds();
      var perByteFee = config.feeTable().getPerByteFee();
      var perUpSubstateFee = config.feeTable().getPerUpSubstateFee();
      var rakeIncreaseDebouncerEpochLength = config.rakeIncreaseDebouncerEpochLength();
      var tokenSymbolPattern = config.tokenSymbolPattern();

      final CMAtomOS v4 = new CMAtomOS();
      v4.load(new ValidatorConstraintScryptV2());
      v4.load(new ValidatorUpdateRakeConstraintScrypt(rakeIncreaseDebouncerEpochLength));
      v4.load(new ValidatorRegisterConstraintScrypt());
      v4.load(new ValidatorUpdateOwnerConstraintScrypt());
      v4.load(new SystemConstraintScrypt());
      v4.load(new TokensConstraintScryptV3(config.reservedSymbols(), tokenSymbolPattern));
      v4.load(new StakingConstraintScryptV4(config.minimumStake().toSubunits()));
      v4.load(new MutexConstraintScrypt());
      v4.load(new RoundUpdateConstraintScrypt(maxRounds));
      v4.load(
          new EpochUpdateConstraintScrypt(
              maxRounds,
              config.rewardsPerProposal().toSubunits(),
              config.minimumCompletedProposalsPercentage(),
              config.unstakingEpochDelay(),
              config.maxValidators()));
      var meter =
          Meters.combine(
              config.maxSigsPerRound().stream()
                  .<Meter>mapToObj(SigsPerRoundMeter::create)
                  .findAny()
                  .orElse(Meter.EMPTY),
              Meters.combine(
                  TxnSizeFeeMeter.create(perByteFee, config.maxTxnSize()),
                  UpSubstateFeeMeter.create(perUpSubstateFee)));
      var constraintMachineConfig =
          new ConstraintMachineConfig(
              v4.getProcedures(),
              v4.buildSubstateDeserialization(),
              v4.buildVirtualSubstateDeserialization(),
              meter);
      var parser = new REParser(v4.buildSubstateDeserialization());
      var serialization = v4.buildSubstateSerialization();
      var actionConstructors =
          REConstructor.newBuilder()
              .perByteFee(perByteFee)
              .put(CreateSystem.class, new CreateSystemConstructorV2())
              .put(BurnToken.class, new BurnTokenConstructor())
              .put(
                  CreateFixedToken.class,
                  new CreateFixedTokenConstructor(SystemConstraintScrypt.MAX_SYMBOL_LENGTH))
              .put(
                  CreateMutableToken.class,
                  new CreateMutableTokenConstructor(SystemConstraintScrypt.MAX_SYMBOL_LENGTH))
              .put(MintToken.class, new MintTokenConstructor())
              .put(
                  NextEpoch.class,
                  new NextEpochConstructorV3(
                      config.rewardsPerProposal().toSubunits(),
                      config.minimumCompletedProposalsPercentage(),
                      config.unstakingEpochDelay(),
                      config.maxValidators()))
              .put(NextRound.class, new NextViewConstructorV3())
              .put(RegisterValidator.class, new RegisterValidatorConstructor())
              .put(SplitToken.class, new SplitTokenConstructor())
              .put(
                  StakeTokens.class,
                  new StakeTokensConstructorV3(config.minimumStake().toSubunits()))
              .put(UnstakeTokens.class, new UnstakeTokensConstructorV2())
              .put(UnstakeOwnership.class, new UnstakeOwnershipConstructor())
              .put(TransferToken.class, new TransferTokensConstructorV2())
              .put(UnregisterValidator.class, new UnregisterValidatorConstructor())
              .put(UpdateValidatorMetadata.class, new UpdateValidatorMetadataConstructor())
              .put(FeeReservePut.class, new FeeReservePutConstructor())
              .put(FeeReserveComplete.class, new FeeReserveCompleteConstructor(config.feeTable()))
              .put(
                  UpdateValidatorFee.class,
                  new UpdateRakeConstructor(
                      rakeIncreaseDebouncerEpochLength,
                      ValidatorUpdateRakeConstraintScrypt.MAX_RAKE_INCREASE))
              .put(UpdateValidatorOwner.class, new UpdateValidatorOwnerConstructor())
              .put(UpdateAllowDelegationFlag.class, new UpdateAllowDelegationFlagConstructor())
              .put(
                  UpdateValidatorSystemMetadata.class,
                  new UpdateValidatorSystemMetadataConstructor())
              .build();

      return new RERules(
          this,
          parser,
          serialization,
          constraintMachineConfig,
          actionConstructors,
          PostProcessor.append(
              new EpochProofVerifierV2(),
              new CandidateForkVotesPostProcessor(parser.getSubstateDeserialization())),
          config);
    }
  };

  public abstract RERules create(RERulesConfig config);
}
