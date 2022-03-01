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

package com.radixdlt.application.tokens.scrypt;

import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.atomos.SubstateDefinition;
import com.radixdlt.constraintmachine.*;
import com.radixdlt.constraintmachine.REEvent.ResourceCreatedEvent;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.exceptions.ReservedSymbolException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.UInt256;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

public final class TokensConstraintScryptV3 implements ConstraintScrypt {
  private final Set<String> reservedSymbols;
  private final Pattern tokenSymbolPattern;

  public TokensConstraintScryptV3(Set<String> reservedSymbols, Pattern tokenSymbolPattern) {
    this.reservedSymbols = reservedSymbols;
    this.tokenSymbolPattern = tokenSymbolPattern;
  }

  @Override
  public void main(Loader os) {
    registerParticles(os);
    defineTokenCreation(os);
    defineMintTransferBurn(os);
  }

  private void registerParticles(Loader os) {
    os.substate(
        new SubstateDefinition<>(
            TokenResource.class,
            SubstateTypeId.TOKEN_RESOURCE.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var addr = REFieldSerialization.deserializeResourceAddr(buf);
              var granularity = REFieldSerialization.deserializeNonZeroUInt256(buf);
              if (!granularity.equals(UInt256.ONE)) {
                throw new DeserializeException("Granularity must be one.");
              }
              var isMutable = REFieldSerialization.deserializeBoolean(buf);
              var minter = REFieldSerialization.deserializeOptionalKey(buf);
              return new TokenResource(addr, granularity, isMutable, minter.orElse(null));
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeREAddr(buf, s.addr());
              REFieldSerialization.serializeUInt256(buf, UInt256.ONE);
              REFieldSerialization.serializeBoolean(buf, s.isMutable());
              REFieldSerialization.serializeOptionalKey(buf, s.optionalOwner());
            }));

    os.substate(
        new SubstateDefinition<>(
            TokenResourceMetadata.class,
            SubstateTypeId.TOKEN_RESOURCE_METADATA.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var addr = REFieldSerialization.deserializeResourceAddr(buf);
              var symbol = REFieldSerialization.deserializeString(buf);
              var name = REFieldSerialization.deserializeString(buf);
              var description = REFieldSerialization.deserializeString(buf);
              var url = REFieldSerialization.deserializeUrl(buf);
              var iconUrl = REFieldSerialization.deserializeUrl(buf);
              return new TokenResourceMetadata(addr, symbol, name, description, iconUrl, url);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeREAddr(buf, s.addr());
              REFieldSerialization.serializeString(buf, s.symbol());
              REFieldSerialization.serializeString(buf, s.name());
              REFieldSerialization.serializeString(buf, s.description());
              REFieldSerialization.serializeString(buf, s.url());
              REFieldSerialization.serializeString(buf, s.iconUrl());
            }));

    os.substate(
        new SubstateDefinition<>(
            TokensInAccount.class,
            SubstateTypeId.TOKENS.id(),
            buf -> {
              REFieldSerialization.deserializeReservedByte(buf);
              var holdingAddr = REFieldSerialization.deserializeAccountREAddr(buf);
              var addr = REFieldSerialization.deserializeResourceAddr(buf);
              var amount = REFieldSerialization.deserializeNonZeroUInt256(buf);
              return new TokensInAccount(holdingAddr, addr, amount);
            },
            (s, buf) -> {
              REFieldSerialization.serializeReservedByte(buf);
              REFieldSerialization.serializeREAddr(buf, s.holdingAddress());
              REFieldSerialization.serializeREAddr(buf, s.resourceAddr());
              buf.put(s.amount().toByteArray());
            }));
  }

  private static class NeedFixedTokenSupply implements ReducerState {
    private final byte[] arg;
    private final TokenResource tokenResource;

    private NeedFixedTokenSupply(byte[] arg, TokenResource tokenResource) {
      this.arg = arg;
      this.tokenResource = tokenResource;
    }
  }

  private static class NeedMetadata implements ReducerState {
    private final TokenResource tokenResource;
    private final byte[] arg;

    private NeedMetadata(byte[] arg, TokenResource tokenResource) {
      this.arg = arg;
      this.tokenResource = tokenResource;
    }

    void metadata(TokenResourceMetadata metadata, ExecutionContext context)
        throws ProcedureException {
      if (!metadata.addr().equals(tokenResource.addr())) {
        throw new ProcedureException("Addresses don't match.");
      }

      var symbol = new String(arg, StandardCharsets.UTF_8);
      if (!symbol.equals(metadata.symbol())) {
        throw new ProcedureException("Symbols don't match.");
      }
      context.emitEvent(new ResourceCreatedEvent(symbol, tokenResource, metadata));
    }
  }

  private void defineTokenCreation(Loader os) {
    os.procedure(
        new UpProcedure<>(
            SystemConstraintScrypt.REAddrClaim.class,
            TokenResource.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              if (!u.addr().equals(s.getAddr())) {
                throw new ProcedureException("Addresses don't match");
              }

              var str = new String(s.getArg());
              if (reservedSymbols.contains(str) && c.permissionLevel() != PermissionLevel.SYSTEM) {
                throw new ReservedSymbolException(str);
              }
              if (!tokenSymbolPattern.matcher(str).matches()) {
                throw new ProcedureException("invalid token symbol: " + str);
              }

              if (u.isMutable()) {
                return ReducerResult.incomplete(new NeedMetadata(s.getArg(), u));
              }

              if (!u.granularity().equals(UInt256.ONE)) {
                throw new ProcedureException("Granularity must be one.");
              }

              return ReducerResult.incomplete(new NeedFixedTokenSupply(s.getArg(), u));
            }));

    os.procedure(
        new UpProcedure<>(
            NeedFixedTokenSupply.class,
            TokensInAccount.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              if (!u.resourceAddr().equals(s.tokenResource.addr())) {
                throw new ProcedureException("Addresses don't match.");
              }
              return ReducerResult.incomplete(new NeedMetadata(s.arg, s.tokenResource));
            }));

    os.procedure(
        new UpProcedure<>(
            NeedMetadata.class,
            TokenResourceMetadata.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              s.metadata(u, c);
              return ReducerResult.complete();
            }));
  }

  private void defineMintTransferBurn(Loader os) {
    // Mint
    os.procedure(
        new UpProcedure<>(
            VoidReducerState.class,
            TokensInAccount.class,
            u -> {
              if (u.resourceAddr().isNativeToken()) {
                return new Authorization(PermissionLevel.SYSTEM, (r, c) -> {});
              }

              return new Authorization(
                  PermissionLevel.USER,
                  (r, c) -> {
                    var tokenResource = r.loadResource(u.resourceAddr());
                    tokenResource.verifyMintAuthorization(c.key());
                  });
            },
            (s, u, c, r) -> {
              c.verifyCanAllocAndDestroyResources();
              return ReducerResult.complete();
            }));

    // Burn
    os.procedure(
        new EndProcedure<>(
            TokenHoldingBucket.class,
            s ->
                new Authorization(
                    PermissionLevel.USER,
                    (r, c) -> {
                      if (s.isEmpty()) {
                        return;
                      }
                      var tokenResource = r.loadResource(s.getResourceAddr());
                      tokenResource.verifyBurnAuthorization(c.key());
                    }),
            TokenHoldingBucket::destroy));

    // Initial Withdraw
    os.procedure(
        new DownProcedure<>(
            VoidReducerState.class,
            TokensInAccount.class,
            d -> d.bucket().withdrawAuthorization(),
            (d, s, r, c) -> {
              var state = new TokenHoldingBucket(d.toTokens());
              return ReducerResult.incomplete(state);
            }));

    // More Withdraws
    os.procedure(
        new DownProcedure<>(
            TokenHoldingBucket.class,
            TokensInAccount.class,
            d -> d.bucket().withdrawAuthorization(),
            (d, s, r, c) -> {
              s.deposit(d.toTokens());
              return ReducerResult.incomplete(s);
            }));

    // Deposit
    os.procedure(
        new UpProcedure<>(
            TokenHoldingBucket.class,
            TokensInAccount.class,
            u -> new Authorization(PermissionLevel.USER, (r, c) -> {}),
            (s, u, c, r) -> {
              s.withdraw(u.resourceAddr(), u.amount());
              return ReducerResult.incomplete(s);
            }));
  }
}
