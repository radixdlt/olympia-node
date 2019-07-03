package org.radix.utils.reflection;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author flotothemoon
 */
public final class ReflectiveTypeDiscovery {
	private ReflectiveTypeDiscovery() {
		throw new IllegalStateException();
	}

	/**
	 * Find an actual generic type argument of a target class implemented by a base class
	 *
	 * @param baseClazz The class that implements the generic type argument
	 * @param targetClazz The target class that has the generic type argument we're looking for
	 * @return The actual class of the first generic argument type in the given target class
	 *         as implemented by the given base class
	 * @throws IllegalStateException If the type could not be found / resolved
	 */
	public static <T> Class<T> findGenericArgumentType(Class<?> baseClazz, Class<?> targetClazz) {
		return findGenericArgumentType(baseClazz, targetClazz, 0); // by default look for the first type argument
	}

	/**
	 * Find an actual generic type argument of a target class implemented by a base class
	 *
	 * @param baseClazz The class that implements the generic type argument
	 * @param targetClazz The target class that has the generic type argument we're looking for
	 * @param genericArgumentIndex The index of the target type argument in the target class
	 * @param <T> The type of the resulting class, auto-cast for convenience
	 * @return The actual class of the generic argument type at the given index in the given target class
	 *         as implemented by the given base class
	 * @throws IllegalStateException If the type could not be found / resolved
	 */
	@SuppressWarnings("unchecked")
	public static <T> Class<T> findGenericArgumentType(Class<?> baseClazz, Class<?> targetClazz, int genericArgumentIndex) {
		Objects.requireNonNull(baseClazz, "baseClazz is required");
		Objects.requireNonNull(targetClazz, "targetClazz is required");

		try {
			Map<Type, Type> typeResolutionMap = new HashMap<>();
			ParameterizedType parameterizedType = findGenericSubType(baseClazz, targetClazz, typeResolutionMap);

			Type actualTypeArgument = parameterizedType.getActualTypeArguments()[genericArgumentIndex];
			if (typeResolutionMap.containsKey(actualTypeArgument)) {
				actualTypeArgument = typeResolutionMap.get(actualTypeArgument);
			}

			return (Class<T>) actualTypeArgument;
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Unable to find generic argument type at %d of %s in %s ",
					genericArgumentIndex, targetClazz, baseClazz), e);
		}
	}

	/**
	 * Attempt to find a {@link ParameterizedType} that is a subtype of the target type with relevant type information.
	 * As the type information in the generic subtype of target type may not be resolved, this method also populates
	 * a type resolution map that can be used to resolve generic type variables to their actual concrete types.
	 *
	 * @param baseClazz The base class that implements the generic type arguments
	 * @param targetSuperType The target type with the generic arguments we are interested in, may be class or interface
	 * @param typeResolutionMap The resulting type resolution map
	 * @return The {@link ParameterizedType} of a subclass to the target class with actual but potentially unresolved generic type arguments
	 * @throws IllegalArgumentException If the type could not be found / resolved
	 */
	private static ParameterizedType findGenericSubType(Class<?> baseClazz, Class<?> targetSuperType, Map<Type, Type> typeResolutionMap) {
		Objects.requireNonNull(baseClazz, "baseClazz is required)");
		Objects.requireNonNull(targetSuperType, "targetSuperType is required");
		Objects.requireNonNull(typeResolutionMap, "targetSuperType is required");

		// list of next types used to recursively walk the class hierarchy up to target type
		Deque<Type> nextTypes = new ArrayDeque<>();
		nextTypes.addLast(baseClazz);

		while (!nextTypes.isEmpty()) {
			Type pendingType = nextTypes.pop();

			// if it's not a class we don't care as we can only traverse classes
			if (!(pendingType instanceof Class)) {
				continue;
			}

			Class<?> pendingClazz = (Class<?>) pendingType;
			Class<?> superClazz = pendingClazz.getSuperclass();
			Type genericSuperClazz = pendingClazz.getGenericSuperclass();

			// as the direct subtype of the target type may not have all the required type arguments
			// we need to gather all type arguments from generic superclasses and interfaces along the way
			// to populate type resolution map so that actual generic types can be resolved
			extractSuperTypeArguments(pendingClazz, typeResolutionMap);

			// superclass may be null if traversed past interfaces
			// or reached top of hierarchy without meeting target type
			if (superClazz != null) {
				// only interested in parameterized types as we need to get the parameterized subtype
				if (genericSuperClazz instanceof ParameterizedType) {
					ParameterizedType parameterizedType = (ParameterizedType) genericSuperClazz;

					// if the super type to the pending type is the type we're looking for we return that
					if (superClazz.isAssignableFrom(targetSuperType)) {
						return parameterizedType;
					}
				}

				nextTypes.addLast(superClazz);
			}

			// walk through the *generic* super interfaces for parameterized type information
			for (Type superInterface : pendingClazz.getGenericInterfaces()) {
				// only interested in parameterized types as we need to get the parameterized subtype
				if (superInterface instanceof ParameterizedType) {
					ParameterizedType parameterizedType = (ParameterizedType) superInterface;

					// if the super type to the pending type is the type we're looking for we return that
					if (((Class<?>) parameterizedType.getRawType()).isAssignableFrom(targetSuperType)) {
						return parameterizedType;
					}

					// if not what we're looking for revert to the raw class type
					// underlying the ParameterizedType for further traversal
					nextTypes.addLast(parameterizedType.getRawType());
				} else {
					// if not parameterized we just keep looking
					nextTypes.addLast(superInterface);
				}
			}
		}

		throw new IllegalArgumentException("Unable to find generic super type " + targetSuperType + " in " + baseClazz);
	}

	/**
	 * Gather and put all actual type arguments alongside their type parameters of the given class's
	 * generic superclass and generic super interfaces in the type resolution map.
	 *
	 * @param clazz The clazz whose superclasses and superinterfaces should be extracted
	 * @param typeResolutionMap Result map of actual type arguments by type parameters
	 */
	private static void extractSuperTypeArguments(Class<?> clazz, Map<Type, Type> typeResolutionMap) {
		Type genericSuperclass = clazz.getGenericSuperclass();
		if ((genericSuperclass instanceof ParameterizedType)) {
			extractTypeArguments((ParameterizedType) genericSuperclass, typeResolutionMap);
		}

		for (Type genericInterface : clazz.getGenericInterfaces()) {
			if (genericInterface instanceof ParameterizedType) {
				extractTypeArguments((ParameterizedType) genericInterface, typeResolutionMap);
			}
		}
	}

	/**
	 * Put all actual type arguments alongside their type parameters in the resolution map
	 *
	 * @param parameterizedType The parameterized type to get the type arguments from
	 * @param typeResolutionMap Result map of actual type arguments by type parameters
	 */
	private static void extractTypeArguments(ParameterizedType parameterizedType, Map<Type, Type> typeResolutionMap) {
		Type[] typeParameter = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
		Type[] actualTypeArgument = parameterizedType.getActualTypeArguments();

		for (int i = 0; i < typeParameter.length; i++) {
			if (typeResolutionMap.containsKey(actualTypeArgument[i])) {
				actualTypeArgument[i] = typeResolutionMap.get(actualTypeArgument[i]);
			}
			typeResolutionMap.put(typeParameter[i], actualTypeArgument[i]);
		}
	}
}
