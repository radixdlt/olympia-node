package org.radix.containers;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;
import org.reflections.Reflections;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class BasicContainerSubTypesEqualsVerifierTest {

    @Test
    public void verify_all_subtypes_correctly_override_equals_and_hash_code() {
        final Set<Class<? extends BasicContainer>> subTypes = new HashSet<>();
        subTypes.addAll(findSubTypesInPkg("org.radix"));
        subTypes.addAll(findSubTypesInPkg("com.radixdlt"));

        subTypes.stream()
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers()))
                .forEach(clazz ->
                    EqualsVerifier.forClass(clazz)
                        .withRedefinedSuperclass()
                        .suppress(Warning.NONFINAL_FIELDS)
                        .withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
                        .verify()
		);
    }

    private Set<Class<? extends BasicContainer>> findSubTypesInPkg(String packagePrefix) {
        return new Reflections(packagePrefix).getSubTypesOf(BasicContainer.class);
    }
}
