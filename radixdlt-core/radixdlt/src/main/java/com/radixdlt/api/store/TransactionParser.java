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

package com.radixdlt.api.store;

import com.google.inject.Inject;
import com.radixdlt.accounting.TwoActorEntry;
import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.ActionType;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.application.system.state.StakeOwnershipBucket;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.application.tokens.state.ExittingOwnershipBucket;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TransactionParser {
	private final Addressing addressing;

	@Inject
	public TransactionParser(Addressing addressing) {
		this.addressing = addressing;
	}

	private String bucketToString(Bucket bucket) {
		if (bucket.getValidatorKey() != null && !(bucket instanceof ExittingOwnershipBucket)) {
			return addressing.forValidators().of(bucket.getValidatorKey());
		}

		return addressing.forAccounts().of(bucket.getOwner());
	}

	private ActionEntry mapToActionEntry(
		Optional<TwoActorEntry> maybeEntry,
		Function<REAddr, String> addrToRri,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		if (maybeEntry.isEmpty()) {
			return ActionEntry.unknown();
		}

		var entry = maybeEntry.get();
		var amtByteArray = entry.amount().toByteArray();
		var amt = UInt256.from(amtByteArray);
		var from = entry.from();
		var to = entry.to();
		final ActionType type;
		if (from.isEmpty()) {
			type = ActionType.MINT;
		} else if (to.isEmpty()) {
			type = ActionType.BURN;
		} else {
			var fromBucket = from.get();
			var toBucket = to.get();
			if (fromBucket instanceof AccountBucket) {
				if (toBucket instanceof AccountBucket) {
					type = ActionType.TRANSFER;
				} else {
					type = ActionType.STAKE;
				}
			} else if (fromBucket instanceof StakeOwnershipBucket) {
				type = ActionType.UNSTAKE;
				amt = computeStakeFromOwnership.apply(fromBucket.getValidatorKey(), UInt384.from(amt)).getLow();
			} else {
				type = ActionType.UNKNOWN;
			}
		}

		return ActionEntry.create(
			type,
			from.map(this::bucketToString).orElse(null),
			to.map(this::bucketToString).orElse(null),
			amt,
			addrToRri.apply(entry.resourceAddr().orElse(REAddr.ofNativeToken()))
		);
	}

	public TxHistoryEntry parse(
		REProcessedTxn processedTxn,
		List<Optional<TwoActorEntry>> actionEntries,
		Instant txDate,
		Function<REAddr, String> addrToRri,
		BiFunction<ECPublicKey, UInt384, UInt384> computeStakeFromOwnership
	) {
		var txnId = processedTxn.getTxnId();
		var fee = processedTxn.getFeePaid();
		var message = processedTxn.getMsg()
			.map(bytes -> new String(bytes, RadixConstants.STANDARD_CHARSET));

		var actions = actionEntries.stream()
			.filter(e -> e.map(a -> !a.isFee()).orElse(true))
			.map(a -> mapToActionEntry(a, addrToRri, computeStakeFromOwnership))
			.collect(Collectors.toList());

		return TxHistoryEntry.create(txnId, txDate, fee, message.orElse(null), actions);
	}
}
