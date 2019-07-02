package com.radixdlt.client.core.network.websocket;

import com.radixdlt.client.core.network.jsonrpc.PersistentChannel;
import io.reactivex.Observable;
import io.reactivex.functions.Cancellable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the state for a single websocket.
 */
public class WebSocketClient implements PersistentChannel {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClient.class);
	private final Object lock = new Object();
	private final BehaviorSubject<WebSocketStatus> state = BehaviorSubject.createDefault(WebSocketStatus.DISCONNECTED);
	private final Function<WebSocketListener, WebSocket> websocketFactory;
	private WebSocket webSocket;
	private final List<Consumer<String>> messageListeners = new CopyOnWriteArrayList<>();

	public WebSocketClient(Function<WebSocketListener, WebSocket> websocketFactory) {
		this.websocketFactory = websocketFactory;

		// FIXME: This disposable is never disposed of. Need to clean this up.
		this.state
			.filter(state -> state.equals(WebSocketStatus.FAILED))
			.debounce(1, TimeUnit.MINUTES)
			.subscribe(i -> {
				synchronized (lock) {
					if (this.state.getValue().equals(WebSocketStatus.FAILED)) {
						this.state.onNext(WebSocketStatus.DISCONNECTED);
					}
				}
			});
	}

	private WebSocket connectWebSocket() {
		synchronized (lock) {
			return this.websocketFactory.apply(new WebSocketListener() {
				@Override
				public void onOpen(WebSocket webSocket, Response response) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Websocket {} opened", System.identityHashCode(WebSocketClient.this));
					}
					synchronized (lock) {
						WebSocketClient.this.webSocket = webSocket;
						WebSocketClient.this.state.onNext(WebSocketStatus.CONNECTED);
					}
				}

				@Override
				public void onMessage(WebSocket webSocket, String message) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Websocket {} message: {}", System.identityHashCode(WebSocketClient.this), message);
					}
					messageListeners.forEach(c -> c.accept(message));
				}

				@Override
				public void onClosing(WebSocket webSocket, int code, String reason) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Websocket {} closing ({}/{})", System.identityHashCode(WebSocketClient.this), code, reason);
					}
					webSocket.close(1000, null);
				}

				@Override
				public void onClosed(WebSocket webSocket, int code, String reason) {
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Websocket {} closed ({}/{})", System.identityHashCode(WebSocketClient.this), code, reason);
					}
					synchronized (lock) {
						WebSocketClient.this.state.onNext(WebSocketStatus.DISCONNECTED);
						WebSocketClient.this.webSocket = null;
					}
				}

				@Override
				public void onFailure(WebSocket websocket, Throwable t, Response response) {
					if (LOGGER.isDebugEnabled()) {
						String msg = String.format("Websocket %s failed", System.identityHashCode(WebSocketClient.this));
						LOGGER.debug(msg, t);
					}
					synchronized (lock) {
						if (state.getValue().equals(WebSocketStatus.CLOSING)) {
							WebSocketClient.this.state.onNext(WebSocketStatus.DISCONNECTED);
							WebSocketClient.this.webSocket = null;
							return;
						}

						WebSocketClient.this.state.onNext(WebSocketStatus.FAILED);
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
					WebSocketClient.this.state.onNext(WebSocketStatus.CONNECTING);
					this.connectWebSocket();
					return;
				case CONNECTING:
				case CONNECTED:
				case CLOSING:
					return;
			}
		}
	}

	@Override
	public Cancellable addListener(Consumer<String> messageListener) {
		messageListeners.add(messageListener);
		return () -> messageListeners.remove(messageListener);
	}

	public Observable<WebSocketStatus> getState() {
		return this.state;
	}

	public boolean close() {
		if (!messageListeners.isEmpty()) {
			return false;
		}

		synchronized (lock) {
			if (this.webSocket != null) {
				this.state.onNext(WebSocketStatus.CLOSING);
				this.webSocket.cancel();
			}
		}

		return true;
	}

	@Override
	public boolean sendMessage(String message) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Websocket {} send: {}", System.identityHashCode(this), message);
		}
		synchronized (lock) {
			if (!this.state.getValue().equals(WebSocketStatus.CONNECTED)) {
				LOGGER.error("Most likely a programming bug. Should not end here.");
				return false;
			}

			return this.webSocket.send(message);
		}
	}
}
