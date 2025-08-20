package com.johntitor.koharu.context;

public interface BeanPostProcessor {

    /**
     * Bean 初始化 → 包装成代理 → 注入属性时再切回原始对象 → 初始化完成后还是用代理。
     * */

    /**
     * Invoked after new Bean().
     * 对象构造后代理对象
     */
    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    /**
     * Invoked after bean.init() called.
     * 对象初始化后代理对象
     */
    default Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }

    /**
     * Invoked before bean.setXyz() called.
     * 在给 Bean 注入依赖属性的时候，把代理对象替换回原始对象，避免循环依赖或者错误注入。
     */
    default Object postProcessOnSetProperty(Object bean, String beanName) {
        return bean;
    }
}
