/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
