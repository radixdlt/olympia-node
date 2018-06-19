package com.radixdlt.client.core.identity.model.keystore;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Crypto {
    @SerializedName("cipher")
    @Expose
    private String cipher;
    @SerializedName("cipherparams")
    @Expose
    private Cipherparams cipherparams;
    @SerializedName("ciphertext")
    @Expose
    private String ciphertext;
    @SerializedName("pbkdfparams")
    @Expose
    private Pbkdfparams pbkdfparams;
    @SerializedName("mac")
    @Expose
    private String mac;

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public Cipherparams getCipherparams() {
        return cipherparams;
    }

    public void setCipherparams(Cipherparams cipherparams) {
        this.cipherparams = cipherparams;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public Pbkdfparams getPbkdfparams() {
        return pbkdfparams;
    }

    public void setPbkdfparams(Pbkdfparams pbkdfparams) {
        this.pbkdfparams = pbkdfparams;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }
}
