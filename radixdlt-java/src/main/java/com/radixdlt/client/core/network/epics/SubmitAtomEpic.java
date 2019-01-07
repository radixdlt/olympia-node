package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubmitAtomEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubmitAtomEpic.class);
	private final FindANodeMiniEpic findANode;

	public SubmitAtomEpic(RadixPeerSelector selector) {
		this.findANode = new FindANodeMiniEpic(selector);
	}

	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(u -> u instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomSubmissionState.SEARCHING_FOR_NODE))
			.flatMap(searchUpdate ->
				findANode.apply(searchUpdate.getAtom().getRequiredFirstShard(), networkState)
					.map(a ->
						a.getType().equals(NodeUpdateType.SELECT_NODE)
							? AtomSubmissionUpdate.submit(searchUpdate.getUuid(), searchUpdate.getAtom(), a.getNode())
							: a
					)
			);
	}
}
