package com.radixdlt.client.core.identity.model.keystore;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Keystore {
    @SerializedName("crypto")
    @Expose
    private Crypto crypto;
    @SerializedName("id")
    @Expose
    private String id;

    public Crypto getCrypto() {
        return crypto;
    }

    public void setCrypto(Crypto crypto) {
        this.crypto = crypto;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
