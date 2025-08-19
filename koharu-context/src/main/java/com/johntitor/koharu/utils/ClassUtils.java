package com.johntitor.koharu.utils;
import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.Component;
import com.johntitor.koharu.annotation.Value;
import com.johntitor.koharu.exception.BeanDefinitionException;
import jakarta.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Nullable
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A getAnnotation(Annotation[] annos, Class<A> annoClass) {
        for (Annotation anno : annos) {
            if (annoClass.isInstance(anno)) {
                return (A) anno;
            }
        }
        return null;
    }


    /**
     * Get bean name by:
     * <p>
     * <code>
     * &#064;Component
     * public class Hello {}
     * </code>
     */
    public static String getBeanName(Class<?> clazz) {
        String name = "";
        // 1. 先直接查找 @Component 注解
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            // 如果类上有 @Component 注解，取注解的 value 作为 beanName
            name = component.value();
        } else {
            // 2. 没有 @Component，就去遍历类上所有注解
            for (Annotation anno : clazz.getAnnotations()) {
                // 判断这个注解是不是“带有 @Component 的元注解”
                if (findAnnotation(anno.annotationType(), Component.class) != null) {
                    try {
                        // 如果是，就通过反射获取这个注解的 value() 方法
                        // 比如 @Service、@Controller 都是 @Component 的“派生注解”
                        name = (String) anno.annotationType().getMethod("value").invoke(anno);
                    } catch (ReflectiveOperationException e) {
                        throw new BeanDefinitionException("Cannot get annotation value.", e);
                    }
                }
            }
        }
        // 3. 如果前面都没拿到名字，就用默认规则：小驼峰命名
        if (name.isEmpty()) {
            // 比如 "HelloWorld" -> "helloWorld"
            name = clazz.getSimpleName();
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    /**
     * Get bean name by:
     * <p>
     * <code>
     * &#064;Bean
     * Hello createHello() {}
     * </code>
     */
    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        String name = bean.value();
        if (name.isEmpty()) {
            name = method.getName();
        }
        return name;
    }


    /**
     * 在某个类里查找带指定注解的方法，不查找父类的
     * Get non-arg method by @PostConstruct or @PreDestroy. Not search in super
     * class.
     * <p>
     * <code>
     * &#064;PostConstruct  void init() {}
     * </code>
     */
    @Nullable
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> annoClass) {
        // 1. 遍历 clazz 的所有声明方法，筛选带有 annoClass 注解的
        List<Method> ms = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(annoClass)).map(m -> {
            // 2. 如果方法带参数，抛异常（强制要求无参方法）
            if (m.getParameterCount() != 0) {
                throw new BeanDefinitionException(
                        String.format("Method '%s' with @%s must not have argument: %s", m.getName(), annoClass.getSimpleName(), clazz.getName()));
            }
            return m;
        }).toList();

        // 3. 没找到 → 返回 null
        if (ms.isEmpty()) {
            return null;
        }

        // 4. 找到一个 → 返回它
        if (ms.size() == 1) {
            return ms.getFirst();
        }

        // 5. 找到多个 → 抛异常
        throw new BeanDefinitionException(String.format("Multiple methods with @%s found in class: %s", annoClass.getSimpleName(), clazz.getName()));
    }

}
