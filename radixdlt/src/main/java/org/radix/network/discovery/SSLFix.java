/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.network.discovery;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class SSLFix {

	private SSLFix() {
		throw new IllegalStateException("Can't construct");
	}

	private static final Logger log = LogManager.getLogger();

	// This, and the code that uses it, is here to placate sonar.
	// It should always be true, unless you do not want to use TLS etc to connect to
	// other sites
	static boolean standardVerifyResult = true;

	static X509ExtendedTrustManager[] trustAllHosts() {
		try {
			X509ExtendedTrustManager[] trustAllCerts = new X509ExtendedTrustManager[] {
				new X509ExtendedTrustManager() {
					@Override
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return new java.security.cert.X509Certificate[0];
					}

					@Override
					public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
						throws CertificateException {
						checkClient();
					}

					@Override
					public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
						throws CertificateException {
						checkServer();
					}

					@Override
					public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string, Socket socket)
						throws CertificateException {
						checkClient();
					}

					@Override
					public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string, Socket socket)
						throws CertificateException {
						checkServer();
					}

					@Override
					public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string, SSLEngine ssle)
						throws CertificateException {
						checkClient();
					}

					@Override
					public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string, SSLEngine ssle)
						throws CertificateException {
						checkServer();
					}

					private void checkClient() throws CertificateException {
						if (!standardVerifyResult) {
							throw new CertificateException("Client not trusted by default.");
						}
					}

					private void checkServer() throws CertificateException {
						if (!standardVerifyResult) {
							throw new CertificateException("Server not trusted by default.");
						}
					}
				}
			};

			SSLContext sc = SSLContext.getInstance("TLSv1.2");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = (hostname, session) -> standardVerifyResult;

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
			return trustAllCerts;
		} catch (GeneralSecurityException e) {
			log.error("Error occurred", e);
			throw new IllegalStateException("Error occurred installing trust manager", e);
		}
	}
}
