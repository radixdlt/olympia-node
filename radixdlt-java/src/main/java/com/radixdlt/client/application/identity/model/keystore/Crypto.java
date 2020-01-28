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

package com.radixdlt.client.application.identity.model.keystore;

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
