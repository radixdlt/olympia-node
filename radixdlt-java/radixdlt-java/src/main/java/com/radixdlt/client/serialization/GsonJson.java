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

package com.radixdlt.client.serialization;

import org.json.JSONObject;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Hopefully temporary class to provide conversion from
 * org.json JSONObject class to Gson JsonElement.
 */
public final class GsonJson {

	private static class Holder {
		private static GsonJson instance = new GsonJson();

		// This paraphernalia is here to placate checkstyle
		static GsonJson instance() {
			return instance;
		}
	}

	public static GsonJson getInstance() {
		return Holder.instance();
	}

	private final Gson gson;
	private final JsonParser parser;

	private GsonJson() {
		this.gson = new Gson();
		this.parser = new JsonParser();
	}

	public JSONObject fromGson(JsonElement element) {
		return new JSONObject(this.gson.toJson(element));
	}

	public String stringFromGson(JsonElement element) {
		return this.gson.toJson(element);
	}

	public JsonElement toGson(JSONObject element) {
		return this.parser.parse(element.toString());
	}
}
