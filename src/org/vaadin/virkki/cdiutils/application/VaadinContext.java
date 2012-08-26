package org.vaadin.virkki.cdiutils.application;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.inject.Scope;

import com.vaadin.ui.Root;

/**
 * CDI Extension which registers VaadinContextImpl context.
 * 
 * @author Tomi Virkki / Vaadin Ltd
 */
public class VaadinContext implements Extension {

    void afterBeanDiscovery(
            @Observes final AfterBeanDiscovery afterBeanDiscovery,
            final BeanManager beanManager) {
        afterBeanDiscovery.addContext(new VaadinContextImpl(beanManager));
    }

    /**
     * Custom CDI context for Vaadin applications. Stores references to bean
     * instances in the scope of a Vaadin Root.
     * 
     * @author Tomi Virkki / Vaadin Ltd
     */
    private static class VaadinContextImpl implements Context {

        private final BeanManager beanManager;

        public VaadinContextImpl(final BeanManager beanManager) {
            this.beanManager = beanManager;
        }

        private RootBeanStore getCurrentBeanStore() {
            final Bean<?> bean = beanManager.getBeans(BeanStoreContainer.class)
                    .iterator().next();
            final BeanStoreContainer container = (BeanStoreContainer) beanManager
                    .getReference(bean, bean.getBeanClass(),
                            beanManager.createCreationalContext(bean));
            return container.getBeanStore(Root.getCurrent());
        }

        @Override
        public <T> T get(final Contextual<T> contextual) {
            return get(contextual, null);
        }

        @Override
        public <T> T get(final Contextual<T> contextual,
                final CreationalContext<T> creationalContext) {
            return getCurrentBeanStore().getBeanInstance((Bean<T>) contextual,
                    creationalContext);
        }

        @Override
        public Class<? extends Annotation> getScope() {
            return VaadinScoped.class;
        }

        @Override
        public boolean isActive() {
            return true;
        }
    }

    /**
     * Datastructure for storing bean instances in {@link VaadinScope} context.
     * 
     * @author Tomi Virkki / Vaadin Ltd
     */
    static class RootBeanStore {
        private final Map<Bean<?>, ContextualInstance<?>> instances = new HashMap<Bean<?>, ContextualInstance<?>>();

        @SuppressWarnings("unchecked")
        protected <T> T getBeanInstance(final Bean<T> bean,
                final CreationalContext<T> creationalContext) {
            ContextualInstance<T> contextualInstance = (ContextualInstance<T>) instances
                    .get(bean);
            if (contextualInstance == null && creationalContext != null) {
                contextualInstance = new ContextualInstance<T>(
                        bean.create(creationalContext), creationalContext);
                instances.put(bean, contextualInstance);
            }
            return contextualInstance != null ? contextualInstance
                    .getInstance() : null;
        }

        public void dereferenceAllBeanInstances() {
            for (final Bean<?> bean : new HashSet<Bean<?>>(instances.keySet())) {
                dereferenceBeanInstance(bean);
            }
        }

        public <T> void dereferenceBeanInstance(final Bean<T> bean) {
            @SuppressWarnings("unchecked")
            final ContextualInstance<T> contextualInstance = (ContextualInstance<T>) instances
                    .get(bean);
            if (contextualInstance != null) {
                bean.destroy(contextualInstance.getInstance(),
                        contextualInstance.getCreationalContext());
                instances.remove(bean);
            }
        }

        class ContextualInstance<T> {
            private final T instance;
            private final CreationalContext<T> creationalContext;

            public ContextualInstance(final T instance,
                    final CreationalContext<T> creationalContext) {
                super();
                this.instance = instance;
                this.creationalContext = creationalContext;
            }

            public T getInstance() {
                return instance;
            }

            public CreationalContext<T> getCreationalContext() {
                return creationalContext;
            }
        }
    }

    @SuppressWarnings("serial")
    @SessionScoped
    static class BeanStoreContainer implements Serializable {
        private final Map<Integer, RootBeanStore> beanStores = new HashMap<Integer, VaadinContext.RootBeanStore>();

        public RootBeanStore getBeanStore(final Root current) {
            final Integer key = current != null ? current.hashCode() : null;
            if (!beanStores.containsKey(key)) {
                beanStores.put(key, new RootBeanStore());
            }
            return beanStores.get(key);
        }

        public void rootInitialized(final Root root) {
            beanStores.put(root.hashCode(), beanStores.remove(null));
            // TODO: Listen for Root close -> Dereference beans of the related
            // beanstore
        }

        @SuppressWarnings("unused")
        @PreDestroy
        private void preDestroy() {
            for (final RootBeanStore beanStore : beanStores.values()) {
                beanStore.dereferenceAllBeanInstances();
            }
        }
    }

    /**
     * Annotation used for declaring bean class scope for VaadinScoped beans
     * 
     * @author Tomi Virkki / Vaadin Ltd
     */
    @Scope
    // TODO: NormalScope
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    public @interface VaadinScoped {
    }

}
