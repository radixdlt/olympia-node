package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomSubmitFindANodeEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(AtomSubmitFindANodeEpic.class);
	private final FindANodeMiniEpic findANodeFunction;

	public AtomSubmitFindANodeEpic(RadixNetwork network, RadixPeerSelector selector) {
		this.findANodeFunction = new FindANodeMiniEpic(network, selector);
	}

	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(u -> u instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomSubmissionState.SEARCHING_FOR_NODE))
			.flatMapSingle(searchUpdate ->
				findANodeFunction.apply(searchUpdate.getAtom().getRequiredFirstShard(), networkState)
					.map(n -> AtomSubmissionUpdate.submit(searchUpdate.getUuid(), searchUpdate.getAtom(), n))
			);
	}
}
