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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.utils.SenderToRx;
import io.reactivex.rxjava3.core.Observable;

/**
 * Module which routes system info related messages
 */
public final class SystemInfoRxModule extends AbstractModule {

	@Override
	protected void configure() {
		SenderToRx<BFTCommittedUpdate, BFTCommittedUpdate> committed = new SenderToRx<>(i -> i);
		SenderToRx<QuorumCertificate, QuorumCertificate> highQCs = new SenderToRx<>(i -> i);
		VertexStoreEventSender eventSender = new VertexStoreEventSender() {
			@Override
			public void sendCommitted(BFTCommittedUpdate committedUpdate) {
				committed.send(committedUpdate);
			}

			@Override
			public void highQC(QuorumCertificate qc) {
				highQCs.send(qc);
			}
		};
		Multibinder.newSetBinder(binder(), VertexStoreEventSender.class).addBinding()
			.toInstance(eventSender);

		bind(new TypeLiteral<Observable<QuorumCertificate>>() { }).toInstance(highQCs.rx());
		bind(new TypeLiteral<Observable<BFTCommittedUpdate>>() { }).toInstance(committed.rx());
	}
}
