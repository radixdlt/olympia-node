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
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class SSLFixTest {
	// Dummy typed null values
	private X509Certificate[] chain = null;
	private String authType = null;
	private Socket socket = null;
	private SSLEngine engine = null;
	private String hostname = null;
	private SSLSession session = null;

	@After
	public void resetExceptionStatus() {
		SSLFix.standardVerifyResult = true;
	}

	@Test
	public void testSSLFix() throws CertificateException {
		X509ExtendedTrustManager[] tms = SSLFix.trustAllHosts();

		assertNotNull(tms);
		assertEquals(1, tms.length);

		X509ExtendedTrustManager tm = tms[0];
		assertNotNull(tm.getAcceptedIssuers());
		assertEquals(0, tm.getAcceptedIssuers().length);

		// No exceptions for these methods, please
		tm.checkClientTrusted(chain, authType);
		tm.checkClientTrusted(chain, authType, socket);
		tm.checkClientTrusted(chain, authType, engine);

		tm.checkServerTrusted(chain, authType);
		tm.checkServerTrusted(chain, authType, socket);
		tm.checkServerTrusted(chain, authType, engine);

		// Check hostname verifier is also good
		HostnameVerifier hnv = HttpsURLConnection.getDefaultHostnameVerifier();
		assertTrue(hnv.verify(hostname, session));
		assertTrue(hnv.verify("radixdlt.com", session));
		assertTrue(hnv.verify("hjklas123789qsdfhj.com", session));
	}

	@Test(expected = CertificateException.class)
	public void testSSLFailClient1() throws CertificateException {
		SSLFix.standardVerifyResult = false;
		X509ExtendedTrustManager[] tms = SSLFix.trustAllHosts();

		assertNotNull(tms);
		assertEquals(1, tms.length);

		X509ExtendedTrustManager tm = tms[0];
		tm.checkClientTrusted(chain, authType);
	}

	@Test(expected = CertificateException.class)
	public void testSSLFailClient2() throws CertificateException {
		SSLFix.standardVerifyResult = false;
		X509ExtendedTrustManager[] tms = SSLFix.trustAllHosts();

		assertNotNull(tms);
		assertEquals(1, tms.length);

		X509ExtendedTrustManager tm = tms[0];
		tm.checkClientTrusted(chain, authType, socket);
	}

	@Test(expected = CertificateException.class)
	public void testSSLFailClient3() throws CertificateException {
		SSLFix.standardVerifyResult = false;
		X509ExtendedTrustManager[] tms = SSLFix.trustAllHosts();

		assertNotNull(tms);
		assertEquals(1, tms.length);

		X509ExtendedTrustManager tm = tms[0];
		tm.checkClientTrusted(chain, authType, engine);
	}

	@Test(expected = CertificateException.class)
	public void testSSLFailServer1() throws CertificateException {
		SSLFix.standardVerifyResult = false;
		X509ExtendedTrustManager[] tms = SSLFix.trustAllHosts();

		assertNotNull(tms);
		assertEquals(1, tms.length);

		X509ExtendedTrustManager tm = tms[0];
		tm.checkServerTrusted(chain, authType);
	}

	@Test(expected = CertificateException.class)
	public void testSSLFailServer2() throws CertificateException {
		SSLFix.standardVerifyResult = false;
		X509ExtendedTrustManager[] tms = SSLFix.trustAllHosts();

		assertNotNull(tms);
		assertEquals(1, tms.length);

		X509ExtendedTrustManager tm = tms[0];
		tm.checkServerTrusted(chain, authType, socket);
	}

	@Test(expected = CertificateException.class)
	public void testSSLFailServer3() throws CertificateException {
		SSLFix.standardVerifyResult = false;
		X509ExtendedTrustManager[] tms = SSLFix.trustAllHosts();

		assertNotNull(tms);
		assertEquals(1, tms.length);

		X509ExtendedTrustManager tm = tms[0];
		tm.checkServerTrusted(chain, authType, engine);
	}
}
