package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction.SubmitAtomActionType;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubmitAtomRequestEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubmitAtomRequestEpic.class);
	private final FindANodeMiniEpic findANode;

	public SubmitAtomRequestEpic(RadixPeerSelector selector) {
		this.findANode = new FindANodeMiniEpic(selector);
	}

	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(u -> u instanceof SubmitAtomAction)
			.map(SubmitAtomAction.class::cast)
			.filter(update -> update.getType().equals(SubmitAtomActionType.FIND_A_NODE))
			.flatMap(searchUpdate ->
				findANode.apply(searchUpdate.getAtom().getRequiredFirstShard(), networkState)
					.map(a ->
						a.getType().equals(NodeUpdateType.SELECT_NODE)
							? SubmitAtomAction.submit(searchUpdate.getUuid(), searchUpdate.getAtom(), a.getNode())
							: a
					)
			);
	}
}
