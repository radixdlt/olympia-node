/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atom.actions;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxErrorCode;
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;

public class UpdateValidator implements TxAction {
	private final ECPublicKey validatorKey;
	private final String name;
	private final String url;

	public UpdateValidator(
		ECPublicKey validatorKey,
		String name,
		String url
	) {
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.name = name;
		this.url = url;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swap(
			ValidatorParticle.class,
			p -> p.getKey().equals(validatorKey),
			TxErrorCode.INVALID_STATE,
			"Invalid state."
		).with(
			substateDown -> new ValidatorParticle(
				validatorKey,
				substateDown.isRegisteredForNextEpoch(),
				name == null ? substateDown.getName() : name,
				url == null ? substateDown.getUrl() : url
			)
		);
	}
}
