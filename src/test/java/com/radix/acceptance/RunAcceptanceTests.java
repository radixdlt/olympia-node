package com.radix.acceptance;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

import cucumber.api.junit.Cucumber;

/**
 * Discovers all Cucumber tests and runs them in a suite.
 */
@RunWith(RunAcceptanceTests.CucumberTestsRunner.class)
public final class RunAcceptanceTests {

	private RunAcceptanceTests() {
		// static only
	}

	// Protected so it's accessible to JUnit, but Eclipse does not show as an
	// option when choosing <right click>/Run As/JUnit Test.
	protected static final class CucumberTestsRunner extends Suite {
		public CucumberTestsRunner(Class<?> testClass) throws InitializationError {
			super(testClass, loadClasses(findClassDir(testClass)));
		}

		private static Class<?>[] loadClasses(File dir) {
			try {
				int dirPathLength = dir.getPath().length() + File.pathSeparator.length();
				return Files.walk(dir.toPath())
					.map(Path::toFile)
					.filter(File::isFile)
					.map(File::getPath)
					.filter(name -> name.endsWith(".class"))
					.map(name -> name.substring(dirPathLength))        // Remove the initial directory and separator
					.map(name -> name.substring(0, name.length() - 6)) // Remove the ".class" bit on the end
					.map(name -> name.replace('/', '.').replace('\\', '.')) // Replace path separators
					.map(name -> loadClass(name))
					.filter(cls -> hasRunWithCucumber(cls))
					.sorted(Comparator.comparing(Class::getName)) // Sort...
					.distinct()                                   // ...and uniqueify so we have same order as Eclipse
					.toArray(n -> new Class<?>[n]);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		private static Class<?> loadClass(String name) {
			try {
				return Class.forName(name);
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException("Can't load class " + name, e);
			}
		}

		private static boolean hasRunWithCucumber(Class<?> cls) {
			RunWith rw = cls.getAnnotation(RunWith.class);
			return rw != null && Cucumber.class.equals(rw.value());
		}

		private static File findClassDir(Class<?> cls) {
			try {
				String path = cls.getProtectionDomain().getCodeSource().getLocation().getFile();
				return new File(URLDecoder.decode(path, "UTF-8"));
			} catch (UnsupportedEncodingException impossible) {
				// using standard encoding, has to exist
				throw new IllegalStateException("Can't decode file", impossible);
			}
		}
	}
}