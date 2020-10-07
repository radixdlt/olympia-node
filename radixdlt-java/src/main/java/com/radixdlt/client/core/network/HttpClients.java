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

package com.radixdlt.client.core.network;

import io.reactivex.Single;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;

public class HttpClients {
	private HttpClients() {
	}

	/**
	 * Lock for http client
	 */
	private static final Object LOCK = new Object();

	/**
	 * Single OkHttpClient to be used for all connections
	 */
	private static OkHttpClient sslAllTrustingClient;

	private static OkHttpClient createClient(BiFunction<X509Certificate[], String, Single<Boolean>> trustManager) {
		// TODO: Pass trust issue to user
		// Create a trust manager that does not validate certificate chains
		final TrustManager[] trustAllCerts = new TrustManager[] {
			new X509TrustManager() {
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					if (!trustManager.apply(chain, authType).blockingGet()) {
						throw new CertificateException();
					}
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
					if (!trustManager.apply(chain, authType).blockingGet()) {
						throw new CertificateException();
					}
				}

				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return new java.security.cert.X509Certificate[] {};
				}
			}
		};

		try {
			// Install the all-trusting trust manager
			final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// Create an ssl socket factory with our all-trusting manager
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

			OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
			builder.hostnameVerifier((hostname, session) -> hostname.equals(session.getPeerHost()));

			builder.connectTimeout(30, TimeUnit.SECONDS)
				.writeTimeout(30, TimeUnit.SECONDS)
				.readTimeout(30, TimeUnit.SECONDS)
				.pingInterval(30, TimeUnit.SECONDS);

			return builder.build();
		} catch (NoSuchAlgorithmException | KeyManagementException e) {
			throw new RuntimeException("Could not create http client: " + e.getMessage());
		}
	}

	/**
	 * Builds OkHttpClient to be used for secure connections with self signed
	 * certificates.
	 *
	 * @return created client
	 */
	public static OkHttpClient getSslAllTrustingClient() {
		synchronized (LOCK) {
			if (sslAllTrustingClient == null) {
				sslAllTrustingClient = createClient(((x509Certificates, s) -> Single.just(true)));
			}
			return sslAllTrustingClient;
		}
	}

}
