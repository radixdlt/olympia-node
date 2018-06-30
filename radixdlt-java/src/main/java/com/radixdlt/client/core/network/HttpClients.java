package com.radixdlt.client.core.network;

import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;

public class HttpClients {
	private HttpClients() {
	}

	/**
	 * Single OkHttpClient to be used for all connections
	 */
	private static final OkHttpClient OK_HTTP_CLIENT;

	/**
	 * Builds OkHttpClient to be used for secure connections with self signed
	 * certificates.
	 */
	static {
		try {
			// Create a trust manager that does not validate certificate chains
			final TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
					}

					@Override
					public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
					}

					@Override
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return new java.security.cert.X509Certificate[] {};
					}
				}
			};

			// Install the all-trusting trust manager
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
			builder.hostnameVerifier((hostname, session) -> true);

			builder
				.connectTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS)
				.readTimeout(30, TimeUnit.SECONDS)
				.pingInterval(30, TimeUnit.SECONDS);

			OK_HTTP_CLIENT = builder.build();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static OkHttpClient get() {
		return OK_HTTP_CLIENT;
	}

}
