/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Streams;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeersAddedEvent;

import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.radix.events.EventListener;
import org.radix.events.Events;
import org.radix.universe.system.RadixSystem;

/**
 * Temporary epoch management which given a fixed quorum size retrieves the first set of peers which
 * matches the size and used as the validator set.
 */
public class AddressBookValidatorSetProvider {
	private final Single<ImmutableList<BFTValidator>> validatorList;

	public AddressBookValidatorSetProvider(
		ECPublicKey selfKey,
		AddressBook addressBook,
		int fixedNodeCount
	) {
		if (fixedNodeCount <= 0) {
			throw new IllegalArgumentException("Quorum size must be > 0 but was " + fixedNodeCount);
		}

		this.validatorList = Observable.<List<Peer>>create(emitter -> {
			emitter.onNext(addressBook.peers().collect(Collectors.toList()));
			// Race condition here but ignore as this is a temporary class
			EventListener<PeersAddedEvent> eventListener = e -> emitter.onNext(e.peers());
			emitter.setCancellable(() -> Events.getInstance().deregister(PeersAddedEvent.class, eventListener));
			Events.getInstance().register(PeersAddedEvent.class, eventListener);
		})
			.map(peers -> Streams.concat(
				addressBook.peers().filter(Peer::hasSystem).map(Peer::getSystem).map(RadixSystem::getKey),
				Stream.of(selfKey)
			).distinct().collect(Collectors.toList()))
			.filter(peers -> peers.size() == fixedNodeCount)
			.firstOrError()
			.map(peers ->
				peers.stream()
					.sorted(Comparator.comparing(ECPublicKey::euid))
					.map(BFTNode::create)
					.map(p -> BFTValidator.from(p, UInt256.ONE))
					.collect(ImmutableList.toImmutableList())
			)
			.cache();
	}

	public BFTValidatorSet getValidatorSet(long epoch) {
		ImmutableList<BFTValidator> validators = validatorList.blockingGet();

		Builder<BFTValidator> validatorSetBuilder = ImmutableList.builder();
		Random random = new Random(epoch);
		List<Integer> indices = IntStream.range(0, validators.size()).boxed().collect(Collectors.toList());
		// Temporary mechanism to get some deterministic random set of validators
		for (long i = 0; i < epoch; i++) {
			random.nextInt(validators.size());
		}
		int randInt = random.nextInt(validators.size());
		int validatorSetSize = randInt + 1;

		for (int i = 0; i < validatorSetSize; i++) {
			int index = indices.remove(random.nextInt(indices.size()));
			BFTValidator validator = validators.get(index);
			validatorSetBuilder.add(validator);
		}

		ImmutableList<BFTValidator> validatorList = validatorSetBuilder.build();

		return BFTValidatorSet.from(validatorList);
	}
}
