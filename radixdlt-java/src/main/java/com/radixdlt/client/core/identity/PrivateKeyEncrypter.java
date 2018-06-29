package com.radixdlt.client.core.identity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.LinuxSecureRandom;
import com.radixdlt.client.core.identity.model.keystore.Cipherparams;
import com.radixdlt.client.core.identity.model.keystore.Crypto;
import com.radixdlt.client.core.identity.model.keystore.Keystore;
import com.radixdlt.client.core.identity.model.keystore.Pbkdfparams;
import okio.ByteString;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;

public final class PrivateKeyEncrypter {

    // In order to prevent the possibility of developers who may use or tweak the library for versions of Android
    // lower than API 17 Jellybean where there is a vulnerability in the implementation of SecureRandom, the below
    // initialisation of SecureRandom on Android fixes the potential issue.
    static {
        if (isAndroidRuntime()) {
            new LinuxSecureRandom();
        }
        SECURE_RANDOM = new SecureRandom();
    }

    private static final SecureRandom SECURE_RANDOM;

    // Taken from BitcoinJ implementation
    // https://github.com/bitcoinj/bitcoinj/blob/3cb1f6c6c589f84fe6e1fb56bf26d94cccc85429/core/src/main/java/org/bitcoinj/core/Utils.java#L573
    private static int isAndroid = -1;

    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 32;
    private static final String DIGEST = "sha512";
    private static final String ALGORITHM = "aes-256-ctr";

    private PrivateKeyEncrypter() { }

    public static void createEncryptedPrivateKeyFile(String password, String filePath) throws Exception {
        ECKeyPair ecKeyPair = ECKeyPairGenerator.newInstance().generateKeyPair();
        String privateKey = ByteString.of(ecKeyPair.getPrivateKey()).hex();
        byte[] salt = getSalt().getBytes(StandardCharsets.UTF_8);

        SecretKey derivedKey = getSecretKey(password, salt, ITERATIONS, KEY_LENGTH);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey);
        byte[] iv = cipher.getParameters().getParameterSpec(IvParameterSpec.class).getIV();

        String cipherText = encrypt(cipher, privateKey);
        byte[] mac = generateMac(derivedKey.getEncoded(), ByteString.decodeHex(cipherText).toByteArray());

        Keystore keystore = createKeystore(ecKeyPair, cipherText, mac, iv, salt);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String strJson = gson.toJson(keystore);

        createFile(strJson, filePath);
    }

    public static byte[] decryptPrivateKeyFile(String password, String filePath) throws Exception {
        Keystore keystore = getKeystore(filePath);
        byte[] salt = keystore.getCrypto().getPbkdfparams().getSalt().getBytes(StandardCharsets.UTF_8);
        int iterations = keystore.getCrypto().getPbkdfparams().getIterations();
        int keyLen = keystore.getCrypto().getPbkdfparams().getKeylen();
        byte[] iv = ByteString.decodeHex(keystore.getCrypto().getCipherparams().getIv()).toByteArray();
        String mac = keystore.getCrypto().getMac();
        byte[] cipherText = ByteString.decodeHex(keystore.getCrypto().getCiphertext()).toByteArray();

        SecretKey derivedKey = getSecretKey(password, salt, iterations, keyLen);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, new IvParameterSpec(iv));

        byte[] computedMac = generateMac(derivedKey.getEncoded(), cipherText);

        if (!Arrays.equals(computedMac, ByteString.decodeHex(mac).toByteArray())) {
            throw new Exception("MAC mismatch");
        }

        String privateKey = decrypt(cipher, cipherText);

        return ByteString.decodeHex(privateKey).toByteArray();
    }

    private static SecretKey getSecretKey(String passPhrase, byte[] salt, int iterations, int keyLength)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        KeySpec spec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterations, keyLength * 8);
        SecretKey key = factory.generateSecret(spec);

        return new SecretKeySpec(key.getEncoded(), "AES");
    }

    private static Keystore getKeystore(String filePath) throws Exception {
        JsonReader reader;
        try {
            reader = new JsonReader(new FileReader(filePath));

            return new Gson().fromJson(reader, Keystore.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception();
        }
    }

    private static void createFile(String fileContents, String filePath) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(filePath);
            writer.write(fileContents);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static Keystore createKeystore(ECKeyPair ecKeyPair, String cipherText, byte[] mac, byte[] iv, byte[] salt)
            throws UnsupportedEncodingException {
        Keystore keystore = new Keystore();
        keystore.setId(ecKeyPair.getUID().toString());

        Crypto crypto = new Crypto();
        crypto.setCipher(ALGORITHM);
        crypto.setCiphertext(cipherText);
        crypto.setMac(ByteString.of(mac).hex());

        Pbkdfparams pbkdfparams = new Pbkdfparams();
        pbkdfparams.setDigest(DIGEST);
        pbkdfparams.setIterations(ITERATIONS);
        pbkdfparams.setKeylen(KEY_LENGTH);
        pbkdfparams.setSalt(new String(salt, StandardCharsets.UTF_8));

        Cipherparams cipherparams = new Cipherparams();
        cipherparams.setIv(ByteString.of(iv).hex());

        crypto.setCipherparams(cipherparams);
        crypto.setPbkdfparams(pbkdfparams);

        keystore.setCrypto(crypto);

        return keystore;
    }

    private static String encrypt(Cipher cipher, String encrypt) throws Exception {
        byte[] bytes = encrypt.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher.doFinal(bytes);
        return ByteString.of(encrypted).hex();
    }

    private static String decrypt(Cipher cipher, byte[] encrypted) throws Exception {
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static byte[] generateMac(byte[] derivedKey, byte[] cipherText) throws NoSuchAlgorithmException {
        byte[] result = new byte[derivedKey.length + cipherText.length];
        result = ByteBuffer.wrap(result).put(derivedKey).put(cipherText).array();

        return RadixHash.of(result).toByteArray();
    }

    private static String getSalt() {
        byte[] salt = new byte[32];
        getSecureRandom().nextBytes(salt);
        return ByteString.of(salt).hex();
    }

    private static SecureRandom getSecureRandom() {
        return SECURE_RANDOM;
    }

    private static boolean isAndroidRuntime() {
        if (isAndroid == -1) {
            final String runtime = System.getProperty("java.runtime.name");
            isAndroid = (runtime != null && runtime.equals("Android Runtime")) ? 1 : 0;
        }
        return isAndroid == 1;
    }
}
