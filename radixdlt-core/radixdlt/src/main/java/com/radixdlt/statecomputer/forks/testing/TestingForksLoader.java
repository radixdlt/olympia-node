package com.radixdlt.statecomputer.forks.testing;

import com.google.inject.Module;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test fork configuration modules can be specified via env properties. This class contains the boilerplate code for that.
 */
public class TestingForksLoader {

  static class TestingForksLoadingException extends RuntimeException {
    TestingForksLoadingException(Exception e) {
      super(e);
    }

    TestingForksLoadingException(String message) {
      super(message);
    }
  }

    /**
     * Will search in this package for a {@link Module }class with the given class name, and instantiate it.
     */
  public Module createTestingForksModuleConfigFromClassName(String simpleClassName) {
    try {
      Set<Class<? extends Module>> modules = new Reflections(getClass().getPackageName()).getSubTypesOf(Module.class);
      List<Class<? extends Module>> matches = modules.stream().filter(module -> module.getSimpleName().equals(simpleClassName))
        .collect(Collectors.toList());
      if (matches.size() != 1) {
        throw new TestingForksLoadingException(
          String.format("Found %d testing forks modules for name %s: %s", matches.size(), simpleClassName, matches)
        );
      }
      return matches.get(0).getConstructor().newInstance();
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new TestingForksLoadingException(e);
    }
  }
}
