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
		super(toMessage(parserState) + ": " + message, cause);
		this.parserState = parserState;
	}

	public TxnParseException(REParser.ParserState parserState, Throwable cause) {
		super(toMessage(parserState), cause);
		this.parserState = parserState;
	}

	public TxnParseException(REParser.ParserState parserState, String message) {
		super(toMessage(parserState) + ": " + message);
		this.parserState = parserState;
	}

	private static String toMessage(REParser.ParserState parserState) {
		return String.format("inst_index=%s", parserState.curIndex());
	}
}
