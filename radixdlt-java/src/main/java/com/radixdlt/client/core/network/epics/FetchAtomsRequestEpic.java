package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsAction;
import com.radixdlt.client.core.network.actions.FetchAtomsAction.FetchAtomsActionType;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchAtomsRequestEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(SubmitAtomRequestEpic.class);
	private final FindANodeMiniEpic findANode;

	public FetchAtomsRequestEpic(RadixPeerSelector selector) {
		this.findANode = new FindANodeMiniEpic(selector);
	}

	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(u -> u instanceof FetchAtomsAction)
			.map(FetchAtomsAction.class::cast)
			.filter(update -> update.getType().equals(FetchAtomsActionType.FIND_A_NODE))
			.flatMap(searchUpdate ->
				findANode.apply(Collections.singleton(searchUpdate.getAddress().getUID().getShard()), networkState)
					.map(a ->
						a.getType().equals(NodeUpdateType.SELECT_NODE)
							? FetchAtomsAction.submitQuery(searchUpdate.getUuid(), searchUpdate.getAddress(), a.getNode())
							: a
					)
			);
	}

}
