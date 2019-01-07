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
 * Manages the store of low level webSockets and connects epics dependent on useage of these webSockets.
 */
public final class WebSocketsEpic implements RadixNetworkEpic {
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

	public static class WebSockets {
		private final ConcurrentHashMap<RadixNode, WebSocketClient> webSockets = new ConcurrentHashMap<>();

		private WebSockets() {
		}

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
