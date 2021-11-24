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

package com.radixdlt.api.core.core.construction;

import com.google.common.base.Suppliers;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.UInt256;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class OperationTxBuilder implements RadixEngine.TxBuilderExecutable {
	private final BuildTransactionRequest request;
	private final Forks forks;

	private OperationTxBuilder(BuildTransactionRequest request, Forks forks) {
		this.request = request;
		this.forks = forks;
	}

	public static OperationTxBuilder from(BuildTransactionRequest request, Forks forks) {
		return new OperationTxBuilder(request, forks);
	}

	private void execute(
		Operation operation,
		List<Operation> relatedOperations,
		TxBuilder txBuilder,
		Supplier<RERulesConfig> config
	) throws TxBuilderException {
		var amountMaybe = operation.getAmount();
		if (amountMaybe.isPresent()) {
			var amount = amountMaybe.get();
			var entityIdentifier = operation.getEntityIdentifier();
			var resourceIdentifier = amount.getResourceIdentifier();
			var compare = amount.getValue().compareTo(BigInteger.ZERO);
			if (compare > 0) {
				var actionAmount = UInt256.from(amount.getValue().toString());
				entityIdentifier.bootUp(txBuilder, actionAmount, resourceIdentifier, config);
			} else if (compare < 0) {
				var accountAddress = entityIdentifier.getAccountAddress()
					.orElseThrow(() -> new InvalidAddressIdentifierException("Spending resources can only occur from account addresses."));
				var actionAmount = UInt256.from(amount.getValue().toString().substring(1));
				var retrieval = resourceIdentifier.substateRetrieval(accountAddress);
				var change = txBuilder.downFungible(
					retrieval.getFirst(),
					retrieval.getSecond(),
					actionAmount
				);
				if (!change.isZero()) {
					entityIdentifier.bootUp(txBuilder, change, resourceIdentifier, config);
				}
			}
		}

		var dataUpdateMaybe = operation.getDataUpdate();
		if (dataUpdateMaybe.isPresent()) {
			var dataUpdate = dataUpdateMaybe.get();
			var fetcher = new DataObject.RelatedOperationFetcher() {
				@Override
				public <T extends DataObject> T get(Class<T> dataObjectClass) {
					return relatedOperations.stream()
						.map(o -> o.getDataUpdate().map(DataUpdate::getDataObject).orElse(null))
						.filter(Objects::nonNull)
						.filter(dataObjectClass::isInstance)
						.map(dataObjectClass::cast)
						.findFirst()
						.orElseThrow();
				}
			};
			dataUpdate.getDataObject().bootUp(
				txBuilder,
				operation.getEntityIdentifier(),
				fetcher,
				config
			);
		}
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		var configSupplier = Suppliers.memoize(() -> {
			var epochData = txBuilder.findSystem(EpochData.class);
			return forks.get(epochData.getEpoch()).getConfig();
		});

		for (var operationGroup : request.getOperationGroups()) {
			for (var operation : operationGroup.getOperations()) {
				execute(operation, operationGroup.getOperations(), txBuilder, configSupplier);
			}
			txBuilder.end();
		}

		var msg = request.getMessage();
		if (msg.isPresent()) {
			txBuilder.message(msg.get());
		}
	}
}
