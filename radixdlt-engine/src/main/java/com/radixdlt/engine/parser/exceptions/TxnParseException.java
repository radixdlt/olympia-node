/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.engine.parser.exceptions;

import com.radixdlt.engine.parser.REParser;

public class TxnParseException extends Exception {
	private final REParser.ParserState parserState;

	public TxnParseException(REParser.ParserState parserState, String message, Throwable cause) {
		super(toCurrent(parserState) + ": " + message + "\n" + toParsed(parserState), cause);
		this.parserState = parserState;
	}

	public TxnParseException(REParser.ParserState parserState, Throwable cause) {
		this(parserState, "", cause);
	}

	public TxnParseException(REParser.ParserState parserState, String message) {
		this(parserState, message, null);
	}

	private static String toCurrent(REParser.ParserState parserState) {
		return String.format("pos=%s inst_index=%s", parserState.curPosition(), parserState.curIndex());
	}

	private static String toParsed(REParser.ParserState parserState) {
		var builder = new StringBuilder();
		for (int i = 0; i < parserState.instructions().size(); i++) {
			builder.append(i);
			builder.append(": ");
			builder.append(parserState.instructions().get(i));
			builder.append("\n");
		}
		return builder.toString();
	}
}
