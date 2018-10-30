package org.radix.serialization2.mapper;

import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * A Jackson {@link SimpleModule} that adds a {@code DsonBeanSerializerModifier}
 * to the {@link SetupContext} for the module.
 */
class DsonModule extends SimpleModule {
	private static final long serialVersionUID = 29L; // Jackson 2.9.x

	DsonModule() {
		// Nothing to do at this point
    }

    @Override
	public void setupModule(SetupContext context) {
        super.setupModule(context);
//        context.addBeanSerializerModifier(new DsonBeanSerializerModifier());
    }
}
