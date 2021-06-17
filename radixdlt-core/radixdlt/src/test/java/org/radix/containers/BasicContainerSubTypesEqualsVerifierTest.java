package org.radix.containers;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;
import org.radix.network.messaging.Message;
import org.radix.universe.system.LocalSystem;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BasicContainerSubTypesEqualsVerifierTest {

    @Test
    public void verify_all_subtypes_correctly_override_equals_and_hash_code() {
        final Set<Class<? extends BasicContainer>> subTypes = new HashSet<>();
        subTypes.addAll(findSubTypesInPkg("org.radix"));
        subTypes.addAll(findSubTypesInPkg("com.radixdlt"));

        final Map<Class<?>, List<String>> ignoredFieldsByClass = Map.of(
                Message.class, List.of("instance"),
                LocalSystem.class, List.of("infoSupplier"));

        subTypes.stream()
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .forEach(clazz -> {
                    final String[] ignoredFields =
                        ignoredFieldsByClass.entrySet().stream()
                            .filter(e -> e.getKey().isAssignableFrom(clazz))
                            .flatMap(e -> e.getValue().stream())
                            .toArray(String[]::new);

                    EqualsVerifier.forClass(clazz)
                        .withRedefinedSuperclass()
                        .suppress(Warning.NONFINAL_FIELDS)
                        .withIgnoredFields(ignoredFields)
                        .usingGetClass()
                        .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
                        .verify();
        });
    }

    private Set<Class<? extends BasicContainer>> findSubTypesInPkg(String packagePrefix) {
        return new Reflections(packagePrefix).getSubTypesOf(BasicContainer.class);
    }
}
