package com.johntitor.koharu.aop;

import com.johntitor.koharu.annotation.Around;
import com.johntitor.koharu.context.ApplicationContextContainer;
import com.johntitor.koharu.context.BeanDefinition;
import com.johntitor.koharu.context.BeanPostProcessor;
import com.johntitor.koharu.context.ConfigurableApplicationContext;
import com.johntitor.koharu.exception.AopConfigException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public abstract class AnnotationProxyBeanPostProcessor<A extends Annotation> implements BeanPostProcessor {

    private final Map<String, Object> originBeans = new HashMap<String, Object>();
    Class<A> annotationClass = this.getParameterizedType();

    public AnnotationProxyBeanPostProcessor() {
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName){

        A anno = bean.getClass().getAnnotation(annotationClass);

        if (anno == null) {
            return bean;
        }

        String handlerName;
        try {
            handlerName = (String) anno.annotationType().getMethod("value").invoke(anno);
        } catch (ReflectiveOperationException e) {
            throw new AopConfigException(String.format("@%s must have value() returned String type.", this.annotationClass.getSimpleName()), e);
        }
        Object proxy =  createProxy(bean,handlerName);
        originBeans.put(beanName,bean);
        return proxy;
    }

    private Object createProxy(Object bean, String handlerName){
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) ApplicationContextContainer.getApplicationContext();
        if (ctx == null) {
            throw new AopConfigException("No application context found");
        }

        BeanDefinition def = ctx.findBeanDefinition(handlerName);
        if (def == null) {
            throw new AopConfigException("No definition found for handler " + handlerName);
        }

        Object handlerBean = def.getInstance();
        if (handlerBean == null) {
            handlerBean = ctx.createBeanAsEarlySingleton(def);
        }

        if (handlerBean instanceof InvocationHandler) {
            InvocationHandler handler = (InvocationHandler) handlerBean;
            return ProxyResolver.getInstance().createProxy(bean, handler);
        }else {
            throw new AopConfigException("Handler " + handlerName + " is not a supported type");
        }
    }

    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName){
        Object originalBean = originBeans.get(beanName);
        return originalBean !=null ? originalBean : bean;
    }


    // 通过反射获取当前类的泛型类型参数的 Class 对象
    private Class<A> getParameterizedType() {
        // 获取父类的泛型定义
        Type type = getClass().getGenericSuperclass();
        // 检查父类是否带泛型
        if (!(type instanceof ParameterizedType)){
            throw new IllegalArgumentException("Class " + getClass().getName() + " does not have parameterized type.");
        }

        ParameterizedType parameterizedType = (ParameterizedType) type;
        Type[] types = parameterizedType.getActualTypeArguments();
        // 强制只允许一个泛型参数
        if (types.length != 1){
            throw new IllegalArgumentException("Class " + getClass().getName() + " has more than one parameterized type.");
        }
        Type r = types[0];
        // 检查泛型实参是否是 Class
        if (!(r instanceof Class<?>)){
            throw new IllegalArgumentException("Class " + getClass().getName() + " has no parameterized type.");
        }
        return (Class<A>) r;
    }








}
