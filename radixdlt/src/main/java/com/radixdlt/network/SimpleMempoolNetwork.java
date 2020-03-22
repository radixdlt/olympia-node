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

package com.radixdlt.network;

import com.radixdlt.consensus.MempoolNetworkRx;
import com.radixdlt.consensus.MempoolNetworkTx;
import java.util.Objects;

import javax.inject.Inject;

import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.addressbook.PeerWithSystem;
import org.radix.network2.messaging.MessageCentral;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.mempool.messages.MempoolAtomAddedMessage;
import com.radixdlt.universe.Universe;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * Overly simplistic network implementation that does absolutely nothing right now.
 */
public class SimpleMempoolNetwork implements MempoolNetworkRx, MempoolNetworkTx {
	private final PeerWithSystem localPeer;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;

	private final PublishSubject<Atom> atoms;

	@Inject
	public SimpleMempoolNetwork(
		LocalSystem system,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.localPeer = new PeerWithSystem(system);

		this.atoms = PublishSubject.create();

		// TODO: Should be handled in start()/stop() once we have lifetimes sorted out
		this.messageCentral.addListener(MempoolAtomAddedMessage.class, this::handleMempoolAtomMessage);
	}


	@Override
	public void sendMempoolSubmission(Atom atom) {
		MempoolAtomAddedMessage message = new MempoolAtomAddedMessage(this.magic, atom);
		final EUID self = this.localPeer.getNID();
		this.addressBook.peers()
			.filter(Peer::hasSystem) // Only peers with systems (and therefore transports)
			.filter(p -> !self.equals(p.getNID())) // Exclude self, already sent
			.forEach(peer -> this.messageCentral.send(peer, message));
	}

	@Override
	public Observable<Atom> atomMessages() {
		return this.atoms;
	}

	private void handleMempoolAtomMessage(Peer source, MempoolAtomAddedMessage message) {
		this.atoms.onNext(message.atom());
	}
}
