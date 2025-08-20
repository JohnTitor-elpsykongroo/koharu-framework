package com.johntitor.koharu.context;

import jakarta.annotation.Nullable;

import java.util.List;

public interface ConfigurableApplicationContext extends ApplicationContext {

    Object createBeanAsEarlySingleton(BeanDefinition def);

    List<BeanDefinition> findBeanDefinitions(Class<?> type);

    @Nullable
    BeanDefinition findBeanDefinition(Class<?> type);

    @Nullable
    BeanDefinition findBeanDefinition(String name);

    @Nullable
    BeanDefinition findBeanDefinition(String name, Class<?> requiredType);
}
