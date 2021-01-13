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

package com.radixdlt.middleware2.network;

import com.radixdlt.consensus.Command;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.mempool.MempoolAddedCommand;
import com.radixdlt.mempool.MempoolNetworkRx;
import java.util.Objects;

import javax.inject.Inject;

import org.radix.universe.system.LocalSystem;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.messages.MempoolAtomAddedMessage;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.universe.Universe;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * Overly simplistic network implementation that does absolutely nothing right now.
 */
public class SimpleMempoolNetwork implements MempoolNetworkRx {
	private final PeerWithSystem localPeer;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;

	private final PublishSubject<Command> commands;

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

		this.commands = PublishSubject.create();

		// TODO: Should be handled in start()/stop() once we have lifetimes sorted out
		this.messageCentral.addListener(MempoolAtomAddedMessage.class, this::handleMempoolAtomMessage);
	}

	public EventProcessor<MempoolAddedCommand> mempoolAddedCommandEventProcessor() {
		return cmd -> {
			MempoolAtomAddedMessage message = new MempoolAtomAddedMessage(this.magic, cmd.getCommand());
			final EUID self = this.localPeer.getNID();
			this.addressBook.peers()
					.filter(Peer::hasSystem) // Only peers with systems (and therefore transports)
					.filter(p -> !self.equals(p.getNID())) // Exclude self, already sent
					.forEach(peer -> this.messageCentral.send(peer, message));
		};
	}

	@Override
	public Observable<Command> commands() {
		return this.commands;
	}

	private void handleMempoolAtomMessage(Peer source, MempoolAtomAddedMessage message) {
		this.commands.onNext(message.command());
	}
}
