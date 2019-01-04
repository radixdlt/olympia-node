package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketClient implements PersistentChannel {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClient.class);
	private final Object lock = new Object();
	private final BehaviorSubject<RadixClientStatus> state = BehaviorSubject.createDefault(RadixClientStatus.DISCONNECTED);
	private final Function<WebSocketListener, WebSocket> websocketFactory;
	private final PublishSubject<String> messages = PublishSubject.create();
	private WebSocket webSocket;

	public WebSocketClient(Function<WebSocketListener, WebSocket> websocketFactory) {
		this.websocketFactory = websocketFactory;
		this.state
			.filter(state -> state.equals(RadixClientStatus.FAILED))
			.debounce(1, TimeUnit.MINUTES)
			.subscribe(i -> {
				synchronized (lock) {
					if (this.state.getValue().equals(RadixClientStatus.FAILED)) {
						this.state.onNext(RadixClientStatus.DISCONNECTED);
					}
				}
			});
	}

	private WebSocket connectWebSocket() {
		synchronized (lock) {
			return this.websocketFactory.apply(new WebSocketListener() {
				@Override
				public void onOpen(WebSocket webSocket, Response response) {
					synchronized (lock) {
						WebSocketClient.this.state.onNext(RadixClientStatus.CONNECTED);
					}
				}

				@Override
				public void onMessage(WebSocket webSocket, String message) {
					synchronized (lock) {
						messages.onNext(message);
					}
				}

				@Override
				public void onClosing(WebSocket webSocket, int code, String reason) {
					webSocket.close(1000, null);
				}

				@Override
				public void onClosed(WebSocket webSocket, int code, String reason) {
					synchronized (lock) {
						WebSocketClient.this.state.onNext(RadixClientStatus.DISCONNECTED);
						WebSocketClient.this.webSocket = null;
					}
				}

				@Override
				public void onFailure(WebSocket websocket, Throwable t, Response response) {
					synchronized (lock) {
						if (state.getValue().equals(RadixClientStatus.CLOSING)) {
							WebSocketClient.this.state.onNext(RadixClientStatus.DISCONNECTED);
							WebSocketClient.this.webSocket = null;
							return;
						}

						WebSocketClient.this.state.onNext(RadixClientStatus.FAILED);
						WebSocketClient.this.webSocket = null;
					}
				}
			});
		}
	}

	/**
	 * Attempts to connect to this Radix node if not already connected
	 */
	public void connect() {
		synchronized (lock) {
			switch (state.getValue()) {
				case DISCONNECTED:
				case FAILED:
					WebSocketClient.this.state.onNext(RadixClientStatus.CONNECTING);
					this.webSocket = this.connectWebSocket();
					return;
				case CONNECTING:
				case CONNECTED:
				case CLOSING:
					return;
			}
		}
	}

	public Observable<String> getMessages() {
		return messages;
	}

	public Observable<RadixClientStatus> getState() {
		return this.state;
	}

	public boolean close() {
		if (messages.hasObservers()) {
			return false;
		}

		synchronized (lock) {
			if (this.webSocket != null) {
				this.state.onNext(RadixClientStatus.CLOSING);
				this.webSocket.cancel();
			}
		}

		return true;
	}

	public boolean sendMessage(String message) {
		synchronized (lock) {
			if (!this.state.getValue().equals(RadixClientStatus.CONNECTED)) {
				LOGGER.error("Most likely a programming bug. Should not end here.");
				return false;
			}

			return this.webSocket.send(message);
		}
	}
}
