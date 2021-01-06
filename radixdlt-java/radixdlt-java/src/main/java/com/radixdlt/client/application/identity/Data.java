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

package com.radixdlt.client.application.identity;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.radixdlt.crypto.encryption.Encryptor;
import com.radixdlt.crypto.exception.ECIESException;
import org.bouncycastle.util.encoders.Base64;

/**
 * Application layer bytes object. Can be stored and retrieved from a RadixAddress.
 */
public class Data {
	public static class DataBuilder {
		private Map<String, Object> metaData = new LinkedHashMap<>();
		private byte[] bytes;
		private Encryptor.EncryptorBuilder encryptorBuilder = new Encryptor.EncryptorBuilder();
		private boolean unencrypted = false;

		public DataBuilder() {
		}

		public DataBuilder metaData(String key, Object value) {
			metaData.put(key, value);
			return this;
		}

		public DataBuilder bytes(byte[] bytes) {
			this.bytes = bytes;
			return this;
		}

		public DataBuilder addReader(ECPublicKey reader) {
			encryptorBuilder.addReader(reader);
			return this;
		}

		public DataBuilder unencrypted() {
			this.unencrypted = true;
			return this;
		}

		public Data build() {
			if (this.bytes == null) {
				throw new IllegalStateException("Must include bytes.");
			}

			final byte[] bytes;
			final Encryptor encryptor;

			if (unencrypted) {
				encryptor = null;
				bytes = this.bytes;
			} else {
				if (encryptorBuilder.getNumReaders() == 0) {
					throw new IllegalStateException("Must either be unencrypted or have at least one reader.");
				}

				ECKeyPair sharedKey = ECKeyPair.generateNew();
				encryptorBuilder.sharedKey(sharedKey);
				encryptor = encryptorBuilder.build();
				try {
					bytes = sharedKey.getPublicKey().encrypt(this.bytes);
				} catch (ECIESException e) {
					throw new IllegalStateException("Expected to always be able to encrypt", e);
				}
			}
			metaData.put("encrypted", !unencrypted);

			return new Data(bytes, metaData, encryptor);
		}
	}

	// TODO: Cleanup this interface
	public static Data raw(byte[] bytes, Map<String, Object> metaData, Encryptor encryptor) {
		return new Data(bytes, metaData, encryptor);
	}

	private final Map<String, Object> metaData;
	private final byte[] bytes;
	private final Encryptor encryptor;

	private Data(byte[] bytes, Map<String, Object> metaData, Encryptor encryptor) {
		this.bytes = bytes;
		this.metaData = metaData;
		this.encryptor = encryptor;
	}

	// TODO: make unmodifiable
	public byte[] getBytes() {
		return bytes;
	}

	public Encryptor getEncryptor() {
		return encryptor;
	}

	public Map<String, Object> getMetaData() {
		return Collections.unmodifiableMap(metaData);
	}

	@Override
	public String toString() {
		boolean encrypted = (Boolean) metaData.get("encrypted");

		return encrypted ? ("encrypted: " + Base64.toBase64String(bytes)) : ("unencrypted: " + new String(bytes));
	}
}
