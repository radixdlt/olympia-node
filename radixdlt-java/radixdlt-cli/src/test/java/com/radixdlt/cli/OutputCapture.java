package com.radixdlt.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class OutputCapture implements AutoCloseable {
	public enum OutputType implements Consumer<PrintStream>, Supplier<PrintStream> {
		STDOUT {
			@Override
			public PrintStream get() {
				return System.out;
			}

			@Override
			public void accept(PrintStream printStream) {
				System.setOut(printStream);
			}
		},
		STDERR {
			@Override
			public PrintStream get() {
				return System.err;
			}

			@Override
			public void accept(PrintStream printStream) {
				System.setErr(printStream);
			}
		};
	}

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final PrintStream capturedStream = new PrintStream(out);
	private final OutputType outputType;
	private PrintStream savedStream;

	private OutputCapture(final OutputType outputType) {
		this.outputType = outputType;
		this.savedStream = this.outputType.get();
		this.outputType.accept(capturedStream);
	}

	public static OutputCapture startStdout() {
		return new OutputCapture(OutputType.STDOUT);
	}

	public static OutputCapture startStderr() {
		return new OutputCapture(OutputType.STDERR);
	}

	public String stop() {
		close();
		capturedStream.flush();
		capturedStream.close();
		return out.toString(StandardCharsets.UTF_8);
	}

	@Override
	public void close() {
		if (savedStream != null) {
			outputType.accept(savedStream);
			savedStream = null;
		}
	}
}
