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

package com.radixdlt.network.messaging.router;

import com.radixdlt.network.messaging.Message;
import com.radixdlt.network.messaging.MessageFromPeer;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.proxy.ProxyCertificateManager;
import io.reactivex.rxjava3.core.Observable;

import java.util.Objects;

public final class MessageRouter {
	private final NodeId self;
	private final P2PConfig config;
	private final ProxyCertificateManager proxyCertificateManager;

	private final Observable<RoutingResult> routedMessages;

	public MessageRouter(
		NodeId self,
		P2PConfig config,
		ProxyCertificateManager proxyCertificateManager,
		Observable<MessageFromPeer<Message>> messages
	) {
		this.self = Objects.requireNonNull(self);
		this.config = Objects.requireNonNull(config);
		this.proxyCertificateManager = Objects.requireNonNull(proxyCertificateManager);

		this.routedMessages = messages
			.map(msg -> route(msg.getSource(), getOrCreateEnvelope(msg)))
			.share();
	}

	/* for backwards compatibility we support both envelope and non-envelope messages */
	private <T extends Message> MessageEnvelope getOrCreateEnvelope(MessageFromPeer<T> messageFromPeer) {
		if (messageFromPeer.getMessage() instanceof MessageEnvelope messageEnvelope) {
			return messageEnvelope;
		} else {
			return MessageEnvelope.create(messageFromPeer.getSource(), self, messageFromPeer.getMessage());
		}
	}

	public Observable<RoutingResult.Process> messagesToProcess() {
		return messagesOfResult(RoutingResult.Process.class);
	}

	public Observable<RoutingResult.Forward> messagesToForward() {
		return messagesOfResult(RoutingResult.Forward.class);
	}

	public Observable<RoutingResult.Drop> messagesToDrop() {
		return messagesOfResult(RoutingResult.Drop.class);
	}

	private <T extends RoutingResult> Observable<T> messagesOfResult(Class<T> clazz) {
		return routedMessages.filter(clazz::isInstance).map(clazz::cast);
	}

	private RoutingResult route(NodeId sender, MessageEnvelope messageEnvelope) {
		if (!verifySender(sender, messageEnvelope)) {
			return new RoutingResult.Drop(messageEnvelope);
		}

		if (messageEnvelope.getRecipient().equals(self)) {
			return new RoutingResult.Process(new MessageFromPeer<>(messageEnvelope.getAuthor(), messageEnvelope.getMessage()));
		} else {
			return handleMessageToOtherRecipient(sender, messageEnvelope);
		}
	}

	private boolean verifySender(NodeId sender, MessageEnvelope messageEnvelope) {
		return sender.equals(messageEnvelope.getAuthor()) // message received directly from the author
			|| this.config.authorizedProxies().contains(sender) // message received from "our" authorized proxy
			|| this.proxyCertificateManager.getVerifiedProxiesForNode(messageEnvelope.getAuthor())
				.contains(sender);  // message received from author's authorized proxy
	}

	private RoutingResult handleMessageToOtherRecipient(NodeId sender, MessageEnvelope messageEnvelope) {
		if (!config.proxyEnabled()) {
			// not going to forward a message if proxy is disabled
			return new RoutingResult.Drop(messageEnvelope);
		}

		// a message from an authorized peer
		if (config.authorizedProxiedPeers().contains(sender)) {
			return new RoutingResult.Forward(messageEnvelope.getRecipient(), messageEnvelope);
		}

		// or a message to an authorized peer
		if (config.authorizedProxiedPeers().contains(messageEnvelope.getRecipient())) {
			return new RoutingResult.Forward(messageEnvelope.getRecipient(), messageEnvelope);
		}

		return new RoutingResult.Drop(messageEnvelope);
	}

	public interface RoutingResult {
		record Process(MessageFromPeer<Message> messageFromPeer) implements RoutingResult { }
		record Forward(NodeId forwardTo, MessageEnvelope messageEnvelope) implements RoutingResult { }
		record Drop(MessageEnvelope messageEnvelope) implements RoutingResult { }
	}
}
