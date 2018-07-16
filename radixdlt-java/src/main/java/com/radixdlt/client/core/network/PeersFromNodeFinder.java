package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.Single;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PeersFromNodeFinder implements PeerDiscovery {
	private final String nodeFinderUrl;
	private final int port;

	public PeersFromNodeFinder(String url, int port) {
		this.nodeFinderUrl = url;
		this.port = port;
	}

	public Observable<RadixPeer> findPeers() {
		Request request = new Request.Builder()
			.url(this.nodeFinderUrl)
			.build();

		return Single.<String>create(emitter -> {
				Call call = HttpClients.getSslAllTrustingClient().newCall(request);
				emitter.setCancellable(call::cancel);
				call.enqueue(new Callback() {
					@Override
					public void onFailure(Call call, IOException e) {
						emitter.tryOnError(e);
					}

					@Override
					public void onResponse(Call call, Response response) throws IOException {
						ResponseBody body = response.body();
						if (response.isSuccessful() && body != null) {
							String bodyString = body.string();
							body.close();
							if (bodyString.isEmpty()) {
								emitter.tryOnError(new IOException("Received empty peer."));
							} else {
								emitter.onSuccess(bodyString);
							}
						} else {
							emitter.tryOnError(new IOException("Error retrieving peer: " + response.message()));
						}
					}
				});
			})
			.map(peerUrl -> new PeersFromSeed(peerUrl, true, port))
			.flatMapObservable(PeersFromSeed::findPeers)
			.timeout(3, TimeUnit.SECONDS)
			.retryWhen(new IncreasingRetryTimer());
	}
}
