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

package org.radix;

import hu.akarnokd.rxjava3.operators.FlowableTransformers;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.Test;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Pair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RadixUniverseBuilderTest {
	private void startPublisher(String name, PublishProcessor<String> pub, long sleep) {
		new Thread(() -> {
			while (true) {
				pub.onNext(name);
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e) {
				}
			}
		}).start();
	}

	@Test
	public void testFlowableMerge() throws Exception {
		final var publisher1 = PublishProcessor.<String>create();
		final var publisher2 = PublishProcessor.<String>create();
		final var publisher3 = PublishProcessor.<String>create();
		PublishProcessor<Flowable<String>> channels = PublishProcessor.create();
		Flowable<String> merged = channels
			.compose(FlowableTransformers.flatMapAsync(v -> v, Schedulers.single(), false));

		merged
			.subscribe(next -> {
				Thread.sleep(1000);
				System.out.println("next: " + next);
			});
		channels.onNext(publisher1.onBackpressureBuffer(1, () -> {}, BackpressureOverflowStrategy.DROP_LATEST));
		channels.onNext(publisher2.onBackpressureBuffer(1, () -> {}, BackpressureOverflowStrategy.DROP_LATEST));
		channels.onNext(publisher3.onBackpressureBuffer(1, () -> {}, BackpressureOverflowStrategy.DROP_LATEST));
		startPublisher("p1", publisher1, 10);
		startPublisher("p2", publisher2, 1000);
		startPublisher("p3", publisher3, 1000);
		Thread.sleep(1000000);
	}

	@Test
	public void testDevUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.development().build();

		assertTrue(p.getSecond().isDevelopment());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testTestUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.test().build();

		assertTrue(p.getSecond().isTest());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testProdUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.production().build();

		assertTrue(p.getSecond().isProduction());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testSpecifiedTypeUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.forType(UniverseType.PRODUCTION)
			.withNewKey()
			.build();

		assertTrue(p.getSecond().isProduction());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testSpecificKeyAndTimestampUniverse() {
		ECKeyPair newKey = ECKeyPair.generateNew();
		long timestamp = System.currentTimeMillis();
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.test()
			.withKey(newKey)
			.withTimestamp(timestamp)
			.build();

		assertTrue(p.getSecond().isTest());
		assertEquals(1, p.getSecond().getGenesis().size());
		assertEquals(newKey.getPublicKey(), p.getSecond().getCreator());
		assertEquals(timestamp, p.getSecond().getTimestamp());
	}
}
