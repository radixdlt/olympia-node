package com.radixdlt.test.utils.universe;

/**
 * a pojo holding the (string) keys generated via GenerateUniverses
 */
public class ValidatorKeypair {

    private String privateKey;
    private String publicKey;

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

}
