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

package com.radixdlt.crypto.encryption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.stream.JsonReader;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECKeyUtils;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.exception.MacMismatchException;
import com.radixdlt.crypto.keystore.Cipherparams;
import com.radixdlt.crypto.keystore.Crypto;
import com.radixdlt.crypto.keystore.Keystore;
import com.radixdlt.crypto.keystore.Pbkdfparams;
import com.radixdlt.utils.Bytes;

@SecurityCritical({ SecurityKind.PK_DECRYPT, SecurityKind.PK_ENCRYPT })
public final class PrivateKeyEncrypter {

    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 32;
    private static final String DIGEST = "sha512";
    private static final String ALGORITHM = "aes-256-ctr";

    private PrivateKeyEncrypter() { }

    public static String createEncryptedPrivateKey(String password) throws GeneralSecurityException {
        Objects.requireNonNull(password);
        ECKeyPair ecKeyPair = ECKeyPair.generateNew();
        String privateKey = Bytes.toHexString(ecKeyPair.getPrivateKey());
        byte[] salt = getSalt().getBytes(StandardCharsets.UTF_8);

        SecretKey derivedKey = getSecretKey(password, salt, ITERATIONS, KEY_LENGTH);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey);
        byte[] iv = cipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();

        String cipherText = encrypt(cipher, privateKey);
        byte[] mac = generateMac(derivedKey.getEncoded(), Bytes.fromHexString(cipherText));

        Keystore keystore = createKeystore(ecKeyPair, cipherText, mac, iv, salt);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(keystore);
    }

    public static byte[] decryptPrivateKey(String password, Reader keyReader) throws IOException, GeneralSecurityException {
        Keystore keystore = getKeystore(keyReader);
        byte[] salt = keystore.getCrypto().getPbkdfparams().getSalt().getBytes(StandardCharsets.UTF_8);
        int iterations = keystore.getCrypto().getPbkdfparams().getIterations();
        int keyLen = keystore.getCrypto().getPbkdfparams().getKeylen();
        byte[] iv = Bytes.fromHexString(keystore.getCrypto().getCipherparams().getIv());
        byte[] mac = Bytes.fromHexString(keystore.getCrypto().getMac());
        byte[] cipherText = Bytes.fromHexString(keystore.getCrypto().getCiphertext());

        SecretKey derivedKey = getSecretKey(password, salt, iterations, keyLen);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, new IvParameterSpec(iv));

        byte[] computedMac = generateMac(derivedKey.getEncoded(), cipherText);

        if (!Arrays.equals(computedMac, mac)) {
            throw new GeneralSecurityException(new MacMismatchException(computedMac, mac));
        }

        String privateKey = decrypt(cipher, cipherText);

        return Bytes.fromHexString(privateKey);
    }

    private static SecretKey getSecretKey(String passPhrase, byte[] salt, int iterations, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterations, keyLength * 8);
        SecretKey key = factory.generateSecret(spec);

        return new SecretKeySpec(key.getEncoded(), "AES");
    }

    private static Keystore getKeystore(Reader keyReader) throws IOException {
        try (JsonReader jsonReader = new JsonReader(keyReader)) {
            return new Gson().fromJson(jsonReader, Keystore.class);
        }
    }

    private static Keystore createKeystore(ECKeyPair ecKeyPair, String cipherText, byte[] mac, byte[] iv, byte[] salt) {
        Keystore keystore = new Keystore();
        keystore.setId(ecKeyPair.euid().toString());

        Crypto crypto = new Crypto();
        crypto.setCipher(ALGORITHM);
        crypto.setCiphertext(cipherText);
        crypto.setMac(Bytes.toHexString(mac));

        Pbkdfparams pbkdfparams = new Pbkdfparams();
        pbkdfparams.setDigest(DIGEST);
        pbkdfparams.setIterations(ITERATIONS);
        pbkdfparams.setKeylen(KEY_LENGTH);
        pbkdfparams.setSalt(new String(salt, StandardCharsets.UTF_8));

        Cipherparams cipherparams = new Cipherparams();
        cipherparams.setIv(Bytes.toHexString(iv));

        crypto.setCipherparams(cipherparams);
        crypto.setPbkdfparams(pbkdfparams);

        keystore.setCrypto(crypto);

        return keystore;
    }

    private static String encrypt(Cipher cipher, String encrypt) throws GeneralSecurityException {
        byte[] bytes = encrypt.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher.doFinal(bytes);
        return Bytes.toHexString(encrypted);
    }

    private static String decrypt(Cipher cipher, byte[] encrypted) throws GeneralSecurityException {
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static byte[] generateMac(byte[] derivedKey, byte[] cipherText) {
        byte[] result = new byte[derivedKey.length + cipherText.length];
        result = ByteBuffer.wrap(result).put(derivedKey).put(cipherText).array();

        return HashUtils.sha256(result).asBytes();
    }

    private static String getSalt() {
        byte[] salt = new byte[32];
        ECKeyUtils.secureRandom().nextBytes(salt);
        return Bytes.toHexString(salt);
    }
}
