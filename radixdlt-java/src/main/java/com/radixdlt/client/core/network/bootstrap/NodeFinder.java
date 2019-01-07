package com.radixdlt.client.core.network.bootstrap;

import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNode;
import io.reactivex.Single;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class NodeFinder {
	private final String nodeFinderUrl;
	private final int port;

	public NodeFinder(String url, int port) {
		this.nodeFinderUrl = url;
		this.port = port;
	}

	public Single<RadixNode> getSeed() {
		return Single.<String>create(emitter -> {
				Request request = new Request.Builder()
					.url(this.nodeFinderUrl)
					.build();
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
			.map(peerUrl -> new RadixNode(peerUrl, true, port));
	}
}
