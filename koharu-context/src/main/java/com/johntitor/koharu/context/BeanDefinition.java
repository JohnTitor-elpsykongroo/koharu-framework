package com.johntitor.koharu.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class BeanDefinition {

    // 全局唯一的Bean Name:
    private String name;
    // Bean的声明类型:
    private Class<?> beanClass;
    // Bean的实例:
    private Object instance = null;
    // 构造方法/null:
    private Constructor<?> constructor;
    // 工厂方法名称/null:
    private String factoryName;
    // 工厂方法/null:
    private Method factoryMethod;
    // Bean的顺序:
    private int order;
    // 是否标识@Primary:
    private boolean primary;

    // 方法名和方法二者只能存在一个
    //init/destroy方法名
    private String initMethodName;
    private String destroyMethodName;

    //init/destroy方法
    private Method initMethod;
    private Method destroyMethod;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public void setBeanClass(Class<?> beanClass) {
        this.beanClass = beanClass;
    }

    public Object getInstance() {
        return instance;
    }

    public void setInstance(Object instance) {
        this.instance = instance;
    }

    public Constructor<?> getConstructor() {
        return constructor;
    }

    public void setConstructor(Constructor<?> constructor) {
        this.constructor = constructor;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String factoryName) {
        this.factoryName = factoryName;
    }

    public Method getFactoryMethod() {
        return factoryMethod;
    }

    public void setFactoryMethod(Method factoryMethod) {
        this.factoryMethod = factoryMethod;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public String getInitMethodName() {
        return initMethodName;
    }

    public void setInitMethodName(String initMethodName) {
        this.initMethodName = initMethodName;
    }

    public String getDestroyMethodName() {
        return destroyMethodName;
    }

    public void setDestroyMethodName(String destroyMethodName) {
        this.destroyMethodName = destroyMethodName;
    }

    public Method getInitMethod() {
        return initMethod;
    }

    public void setInitMethod(Method initMethod) {
        this.initMethod = initMethod;
    }

    public Method getDestroyMethod() {
        return destroyMethod;
    }

    public void setDestroyMethod(Method destroyMethod) {
        this.destroyMethod = destroyMethod;
    }
}
