/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus;

import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.stream.Collectors;

public final class BasicEpochChangeRx implements EpochChangeRx {
	private final ValidatorSet validatorSet;
	private final VertexMetadata ancestor;

	public BasicEpochChangeRx(VertexMetadata ancestor, List<ECPublicKey> nodes) {
		this.ancestor = ancestor;
		this.validatorSet = ValidatorSet.from(
			nodes.stream()
				.map(p -> Validator.from(p, UInt256.ONE))
				.collect(Collectors.toList())
		);
	}

	public BasicEpochChangeRx(List<ECPublicKey> nodes) {
		this(VertexMetadata.ofGenesisAncestor(), nodes);
	}

	@Override
	public Observable<EpochChange> epochChanges() {
		return Observable.just(new EpochChange(ancestor, validatorSet))
			.concatWith(Observable.never());
	}
}
