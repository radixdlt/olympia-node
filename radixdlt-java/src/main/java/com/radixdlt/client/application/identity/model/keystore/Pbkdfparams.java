package com.radixdlt.client.application.identity.model.keystore;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Pbkdfparams {
    @SerializedName("iterations")
    @Expose
    private Integer iterations;
    @SerializedName("keylen")
    @Expose
    private Integer keylen;
    @SerializedName("digest")
    @Expose
    private String digest;
    @SerializedName("salt")
    @Expose
    private String salt;

    public Integer getIterations() {
        return iterations;
    }

    public void setIterations(Integer iterations) {
        this.iterations = iterations;
    }

    public Integer getKeylen() {
        return keylen;
    }

    public void setKeylen(Integer keylen) {
        this.keylen = keylen;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }
}
