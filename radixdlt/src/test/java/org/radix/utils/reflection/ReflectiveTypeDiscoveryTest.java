package org.radix.utils.reflection;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the magical {@link ReflectiveTypeDiscovery}
 *
 * @author flotothemoon
 */
public class ReflectiveTypeDiscoveryTest {
	private class A { }
	private class B extends A { }
	private class C<T> extends B { }

	private interface GenericInterface<A, B> { }
	private interface SpecificInterface<A, B> extends GenericInterface<A, B> { }
	private class SpecificClass implements GenericInterface<A, B> { }
	private abstract class AbstractSpecificClass<A, B> implements GenericInterface<A, B> { }

	@Test
	public void findActualTypeInClassFromGenericInterface() {
		Assert.assertEquals(A.class, ReflectiveTypeDiscovery.findGenericArgumentType(SpecificClass.class, GenericInterface.class));
		Assert.assertEquals(B.class, ReflectiveTypeDiscovery.findGenericArgumentType(SpecificClass.class, GenericInterface.class, 1));
	}

	@Test
	public void findActualTypeInClassFromSpecificInterface() {
		Assert.assertEquals(A.class, ReflectiveTypeDiscovery.findGenericArgumentType(SpecificClass.class, SpecificInterface.class)); // should default to first type
		Assert.assertEquals(B.class, ReflectiveTypeDiscovery.findGenericArgumentType(SpecificClass.class, SpecificInterface.class, 1));
	}

	@Test
	public void findActualTypeInAnonymousClassFromGenericInterface() {
		Class<?> clazz = new AbstractSpecificClass<A, B>() {}.getClass();

		Assert.assertEquals(A.class, ReflectiveTypeDiscovery.findGenericArgumentType(clazz, GenericInterface.class, 0));
		Assert.assertEquals(B.class, ReflectiveTypeDiscovery.findGenericArgumentType(clazz, GenericInterface.class, 1));
	}

	@Test
	public void findActualTypeInAnonymousClassFromSpecificInterface() {
		Class<?> clazz = new AbstractSpecificClass<A, B>() {}.getClass();
		Assert.assertEquals(A.class, ReflectiveTypeDiscovery.findGenericArgumentType(clazz, SpecificInterface.class, 0));
		Assert.assertEquals(B.class, ReflectiveTypeDiscovery.findGenericArgumentType(clazz, SpecificInterface.class, 1));
	}

	private abstract class AbstractTemplateClass<T> extends AbstractSpecificClass<C<T>, B> { }
	private class SpecificTemplateClass extends AbstractTemplateClass<A> { }

	@Test
	public void findActualTypeInNestedClassFromAbstractClass() {
		Assert.assertEquals(A.class, ReflectiveTypeDiscovery.findGenericArgumentType(SpecificTemplateClass.class, AbstractTemplateClass.class, 0));
	}

	@Test
	public void findActualTypeInAnonymousNestedClassFromAbstractClass() {
		Class<?> clazz = new AbstractTemplateClass<A>() {}.getClass();
		Assert.assertEquals(A.class, ReflectiveTypeDiscovery.findGenericArgumentType(clazz, AbstractTemplateClass.class, 0));
	}

	@Test
	public void findActualTypeInNestedClassFromGenericInterface() {
		Assertions.assertThatThrownBy(() -> ReflectiveTypeDiscovery.findGenericArgumentType(SpecificTemplateClass.class, GenericInterface.class, 3))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void findActualTypeInNestedClassFromSpecificInterface() {
		Assertions.assertThatThrownBy(() -> ReflectiveTypeDiscovery.findGenericArgumentType(SpecificTemplateClass.class, SpecificInterface.class, 3))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void findNonExistentTypeInClassFromGenericInterface() {
		Assertions.assertThatThrownBy(() -> ReflectiveTypeDiscovery.findGenericArgumentType(SpecificClass.class, GenericInterface.class, 3))
				.isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(ArrayIndexOutOfBoundsException.class);
	}
}