/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.api.gateway;

import com.google.inject.Inject;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.accounting.TwoActorEntry;
import com.radixdlt.api.gateway.openapitools.model.AccountIdentifier;
import com.radixdlt.api.gateway.openapitools.model.AccountTransaction;
import com.radixdlt.api.gateway.openapitools.model.AccountTransactionMetadata;
import com.radixdlt.api.gateway.openapitools.model.AccountTransactionStatus;
import com.radixdlt.api.gateway.openapitools.model.Action;
import com.radixdlt.api.gateway.openapitools.model.BurnTokens;
import com.radixdlt.api.gateway.openapitools.model.MintTokens;
import com.radixdlt.api.gateway.openapitools.model.StakeTokens;
import com.radixdlt.api.gateway.openapitools.model.TokenAmount;
import com.radixdlt.api.gateway.openapitools.model.TokenIdentifier;
import com.radixdlt.api.gateway.openapitools.model.TransactionIdentifier;
import com.radixdlt.api.gateway.openapitools.model.TransferTokens;
import com.radixdlt.api.gateway.openapitools.model.UnstakeTokens;
import com.radixdlt.api.gateway.openapitools.model.ValidatorIdentifier;
import com.radixdlt.application.system.state.StakeOwnershipBucket;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.json.JSONObject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GatewayModelMapper {
	private final Addressing addressing;

	@Inject
	public GatewayModelMapper(Addressing addressing) {
		this.addressing = addressing;
	}

	public List<Action> actions(
		REProcessedTxn txn,
		Function<REAddr, String> addressToSymbol,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		return txn.getGroupedStateUpdates()
			.stream()
			.flatMap(u -> inferAction(u, addressToSymbol, getValidatorStake).stream())
			.collect(Collectors.toList());
	}

	private Optional<Action> inferAction(
		List<REStateUpdate> stateUpdates,
		Function<REAddr, String> addressToSymbol,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		var accounting = REResourceAccounting.compute(stateUpdates.stream());
		var entry = TwoActorEntry.parse(accounting.bucketAccounting());
		return entry.map(e -> mapToAction(
			e,
			addressToSymbol,
			(k, ownership) -> {
				var stakeData = getValidatorStake.apply(k);
				return ownership.multiply(stakeData.getTotalStake()).divide(stakeData.getTotalOwnership());
			}
		));
	}

	public TokenIdentifier nativeTokenIdentifier() {
		var rri = addressing.forResources().of("xrd", REAddr.ofNativeToken());
		return new TokenIdentifier()
			.rri(rri);
	}

	public TransactionIdentifier transactionIdentifier(AID txnId) {
		return new TransactionIdentifier()
			.hash(txnId.toString());
	}

	public AccountIdentifier accountIdentifier(REAddr addr) {
		return new AccountIdentifier().address(addressing.forAccounts().of(addr));
	}

	public ValidatorIdentifier validatorIdentifier(ECPublicKey key) {
		return new ValidatorIdentifier().address(addressing.forValidators().of(key));
	}

	public TokenIdentifier tokenIdentifier(REAddr addr, Function<REAddr, String> addrToSymbol) {
		var symbol = addrToSymbol.apply(addr);
		var rri = addressing.forResources().of(symbol, addr);
		return new TokenIdentifier()
			.rri(rri);
	}

	public TokenAmount tokenAmount(
		UInt256 amount,
		Bucket fromBucket,
		REAddr tokenAddress,
		Function<REAddr, String> addrToSymbol,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		if (tokenAddress != null) {
			return new TokenAmount()
				.tokenIdentifier(tokenIdentifier(tokenAddress, addrToSymbol))
				.value(amount.toString());
		} else {
			var value = computeStakeFromOwnership.apply(fromBucket.getValidatorKey(), UInt384.from(amount)).getLow();
			return new TokenAmount()
				.tokenIdentifier(tokenIdentifier(REAddr.ofNativeToken(), addrToSymbol))
				.value(value.toString());
		}
	}

	private Action mapToAction(
		TwoActorEntry entry,
		Function<REAddr, String> addrToSymbol,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		var amtByteArray = entry.amount().toByteArray();
		var amount = UInt256.from(amtByteArray);
		var from = entry.from();
		var to = entry.to();
		var result = new JSONObject();

		var tokenAmount = tokenAmount(
			amount,
			from.orElse(null),
			entry.resourceAddr().orElse(null),
			addrToSymbol,
			computeStakeFromOwnership
		);

		if (from.isEmpty()) {
			var toBucket = to.orElseThrow();
			if (!(toBucket instanceof AccountBucket)) {
				throw new IllegalStateException();
			}
			return new MintTokens()
				.to(accountIdentifier(toBucket.getOwner()))
				.amount(tokenAmount)
				.type("MintTokens");
		} else if (to.isEmpty()) {
			return new BurnTokens()
				.from(accountIdentifier(from.get().getOwner()))
				.amount(tokenAmount)
				.type("BurnTokens");
		} else {
			var fromBucket = from.get();
			var toBucket = to.get();
			if (fromBucket instanceof AccountBucket) {
				var fromAccount = accountIdentifier(fromBucket.getOwner());
				if (toBucket instanceof AccountBucket) {
					var toAccount = accountIdentifier(toBucket.getOwner());
					return new TransferTokens()
						.amount(tokenAmount)
						.from(fromAccount)
						.to(toAccount)
						.type("TransferTokens");
				} else {
					var toValidator = validatorIdentifier(toBucket.getValidatorKey());
					return new StakeTokens()
						.amount(tokenAmount)
						.from(fromAccount)
						.to(toValidator)
						.type("StakeTokens");
				}
			} else if (fromBucket instanceof StakeOwnershipBucket) {
				var fromValidator = validatorIdentifier(toBucket.getValidatorKey());
				var toAccount = accountIdentifier(toBucket.getOwner());
				return new UnstakeTokens()
					.amount(tokenAmount)
					.from(fromValidator)
					.to(toAccount)
					.type("UnstakeTokens");
			} else {
				throw new IllegalStateException();
			}
		}
	}

	public AccountTransaction accountTransaction(
		REProcessedTxn processedTxn,
		Instant confirmedTimestamp,
		Function<REAddr, String> addressToSymbol,
		Function<ECPublicKey, ValidatorStakeData> getValidatorStake
	) {
		var accountTransaction = new AccountTransaction();
		actions(processedTxn, addressToSymbol, getValidatorStake)
			.forEach(accountTransaction::addActionsItem);
		accountTransaction.transactionIdentifier(transactionIdentifier(processedTxn.getTxnId()));
		if (confirmedTimestamp != null) {
			accountTransaction
				.transactionStatus(new AccountTransactionStatus()
					.status(AccountTransactionStatus.StatusEnum.CONFIRMED)
					.confirmedTime(confirmedTimestamp.toString())
				);
		} else {
			accountTransaction
				.transactionStatus(new AccountTransactionStatus()
					.status(AccountTransactionStatus.StatusEnum.PENDING)
				);
		}
		var metadata = new AccountTransactionMetadata()
			.hex(Bytes.toHexString(processedTxn.getTxn().getPayload()));
		processedTxn.getMsg().ifPresent(msg -> metadata.setMessage(Bytes.toHexString(msg)));
		accountTransaction.metadata(metadata);
		if (!processedTxn.getFeePaid().isZero()) {
			accountTransaction.feePaid(new TokenAmount()
				.tokenIdentifier(nativeTokenIdentifier())
				.value(processedTxn.getFeePaid().toString())
			);
		}

		return accountTransaction;
	}
}
