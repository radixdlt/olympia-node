package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Epic which manages the store of low level webSockets and connects epics dependent on useage of these webSockets.
 */
public final class WebSocketsEpic implements RadixNetworkEpic {

	/**
	 * Builds a WebSocketsEpic composed of epics which require websockets. After being built, all epics
	 * share the same set of websockets which can be used.
	 */
	public static class WebSocketsEpicBuilder {
		private final List<Function<WebSockets, RadixNetworkEpic>> webSocketEpics = new ArrayList<>();

		public WebSocketsEpicBuilder add(Function<WebSockets, RadixNetworkEpic> webSocketEpic) {
			webSocketEpics.add(webSocketEpic);
			return this;
		}

		public WebSocketsEpic build() {
			return new WebSocketsEpic(new ArrayList<>(webSocketEpics));
		}
	}

	/**
	 * All websockets are created and managed here.
	 */
	public static class WebSockets {
		private final ConcurrentHashMap<RadixNode, WebSocketClient> webSockets = new ConcurrentHashMap<>();

		private WebSockets() {
		}

		/**
		 * Returns the unique websocket for a given node. Will never return null.
		 *
		 * @param node a radix node to get the websocket client for
		 * @return a websocket client mapped to the node
		 */
		public WebSocketClient get(RadixNode node) {
			return webSockets.computeIfAbsent(
				node,
				n -> new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(n.getLocation(), listener))
			);
		}
	}

	private final List<Function<WebSockets, RadixNetworkEpic>> webSocketEpics;

	private WebSocketsEpic(List<Function<WebSockets, RadixNetworkEpic>> webSocketEpics) {
		this.webSocketEpics = webSocketEpics;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		final WebSockets webSockets = new WebSockets();
		return
			Observable.merge(
				webSocketEpics.stream()
					.map(f -> f.apply(webSockets).epic(actions, networkState))
					.collect(Collectors.toSet())
			);
	}
}
