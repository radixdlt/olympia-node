package com.radixdlt.client.core.network;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketClient.class);

	private WebSocket webSocket;
	public enum RadixClientStatus {
		CONNECTING, OPEN, CLOSED, FAILURE
	}

	private final BehaviorSubject<RadixClientStatus> status = BehaviorSubject.createDefault(RadixClientStatus.CLOSED);
	private final AtomicBoolean closed = new AtomicBoolean(false);

	private final Request endpoint;
	private final Supplier<OkHttpClient> okHttpClient;

	private PublishSubject<String> messages = PublishSubject.create();

	public WebSocketClient(Supplier<OkHttpClient> okHttpClient, Request endpoint) {
		this.okHttpClient = okHttpClient;
		this.endpoint = endpoint;

		this.status
			.filter(status -> status.equals(RadixClientStatus.FAILURE))
			.debounce(1, TimeUnit.MINUTES)
			.subscribe(i -> {
				this.messages = PublishSubject.create();
				this.status.onNext(RadixClientStatus.CLOSED);
			});
	}

	public Observable<String> getMessages() {
		return messages;
	}

	public Request getEndpoint() {
		return endpoint;
	}

	public Observable<RadixClientStatus> getStatus() {
		return status;
	}

	public boolean close() {
		if (messages.hasObservers()) {
			return false;
		}

		if (this.webSocket != null) {
			this.webSocket.close(1000, null);
		}

		return true;
	}

	private void tryConnect() {
		// TODO: Race condition here but not fatal, fix later on
		if (this.status.getValue() == RadixClientStatus.CONNECTING) {
			return;
		}

		this.status.onNext(RadixClientStatus.CONNECTING);

		// HACKISH: fix
		this.webSocket = this.okHttpClient.get().newWebSocket(endpoint, new WebSocketListener() {
			@Override
			public void onOpen(WebSocket webSocket, Response response) {
				WebSocketClient.this.status.onNext(RadixClientStatus.OPEN);
			}

			@Override
			public void onMessage(WebSocket webSocket, String message) {
				messages.onNext(message);
			}

			@Override
			public void onClosing(WebSocket webSocket, int code, String reason) {
				webSocket.close(1000, null);
			}

			@Override
			public void onClosed(WebSocket webSocket, int code, String reason) {
				WebSocketClient.this.status.onNext(RadixClientStatus.CLOSED);
			}

			@Override
			public void onFailure(WebSocket websocket, Throwable t, Response response) {
				if (closed.get()) {
					return;
				}

				LOGGER.error(t.toString());
				WebSocketClient.this.status.onNext(RadixClientStatus.FAILURE);

				WebSocketClient.this.messages.onError(t);
			}
		});
	}

	/**
	 * Attempts to connect to this Radix node on subscribe if not already connected
	 *
	 * @return completable which signifies when connection has been made
	 */
	public Completable connect() {
		return this.getStatus()
			.doOnNext(status -> {
				// TODO: cancel tryConnect on dispose
				if (status.equals(RadixClientStatus.CLOSED)) {
					this.tryConnect();
				} else if (status.equals(RadixClientStatus.FAILURE)) {
					throw new IOException();
				}
			})
			.filter(status -> status.equals(RadixClientStatus.OPEN))
			.firstOrError()
			.ignoreElement();
	}

	public boolean send(String message) {
		return this.webSocket.send(message);
	}
}
