package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.WebSockets;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.List;

import java.util.Objects;
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
		private WebSockets webSockets;

		public WebSocketsEpicBuilder add(Function<WebSockets, RadixNetworkEpic> webSocketEpic) {
			webSocketEpics.add(webSocketEpic);
			return this;
		}

		public WebSocketsEpicBuilder setWebSockets(WebSockets webSockets) {
			this.webSockets = webSockets;
			return this;
		}

		public WebSocketsEpic build() {
			Objects.requireNonNull(webSockets);
			return new WebSocketsEpic(webSockets, new ArrayList<>(webSocketEpics));
		}
	}

	private final List<Function<WebSockets, RadixNetworkEpic>> webSocketEpics;
	private final WebSockets webSockets;

	private WebSocketsEpic(WebSockets webSockets, List<Function<WebSockets, RadixNetworkEpic>> webSocketEpics) {
		this.webSockets = webSockets;
		this.webSocketEpics = webSocketEpics;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return
			Observable.merge(
				webSocketEpics.stream()
					.map(f -> f.apply(webSockets).epic(actions, networkState))
					.collect(Collectors.toSet())
			);
	}
}
