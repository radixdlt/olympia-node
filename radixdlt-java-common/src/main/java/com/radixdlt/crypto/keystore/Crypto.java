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

package com.radixdlt.crypto.keystore;

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
