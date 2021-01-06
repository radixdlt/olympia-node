/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

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
