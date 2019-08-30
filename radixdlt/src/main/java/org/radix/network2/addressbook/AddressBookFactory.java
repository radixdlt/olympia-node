package org.radix.network2.addressbook;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;

/**
 * Factory for creating an {@link AddressBook}.
 */
public class AddressBookFactory {

	/**
	 * Create a {@link AddressBook} based on a default configuration.
	 *
	 * @return The newly constructed {@link AddressBook}
	 */
	public AddressBook createDefault() {
		return createInjector().getInstance(AddressBook.class);
	}

	private Injector createInjector() {
		Injector injector = Guice.createInjector(new AddressBookModule());
		return injector;
	}

	private static void outputGraph(Injector injector, String filename) {
		try (PrintWriter out = new PrintWriter(new File(filename), "UTF-8")) {
			Injector graphvizInjector = Guice.createInjector(new GraphvizModule());
			GraphvizGrapher grapher = graphvizInjector.getInstance(GraphvizGrapher.class);
			grapher.setOut(out);
			grapher.setRankdir("TB");
			grapher.graph(injector);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static void main(String[] args) {
		Injector injector = new AddressBookFactory().createInjector();
		outputGraph(injector, "AddressBook.dot");
	}
}
