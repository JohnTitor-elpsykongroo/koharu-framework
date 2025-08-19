package com.johntitor.koharu.utils;
import com.johntitor.koharu.exception.BeanDefinitionException;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

public class ClassUtils {

    private final static String JDK_ANNO = "java.lang.annotation";
    /*
    * 递归查找Annotation
    * */
    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        return findAnnotationInternal(target, annoClass, new HashSet<>());
    }

    private static <A extends Annotation> A findAnnotationInternal(Class<?> target,
                                                                   Class<A> annoClass,
                                                                   Set<Class<?>> visited) {
        // 如果已经访问过这个注解类型，避免无限递归
        if (!visited.add(target)) {
            return null;
        }

        A a = target.getAnnotation(annoClass);
        for (Annotation annotation : target.getAnnotations()) {
            Class<? extends Annotation> annoType = annotation.annotationType();

            // 排除 JDK 自带注解
            if (!annoType.getPackageName().equals(JDK_ANNO)) {
                A foundAnno = findAnnotationInternal(annoType, annoClass, visited);
                if (foundAnno == null){
                    continue; // 没找到，跳过
                }
                // 已经存在注解，再次发现 -> 冲突
                if (a != null) {
                    throw new BeanDefinitionException("Duplicate @"
                            + annoClass.getSimpleName()
                            + " found on class "
                            + target.getSimpleName());
                }
                // 第一次发现 -> 赋值
                a = foundAnno;
            }
        }
        return a;
    }
}
