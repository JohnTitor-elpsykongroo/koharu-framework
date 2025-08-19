package com.johntitor.koharu.context;

import com.johntitor.koharu.annotation.*;
import com.johntitor.koharu.exception.BeanCreationException;
import com.johntitor.koharu.exception.BeanDefinitionException;
import com.johntitor.koharu.io.PropertyResolver;
import com.johntitor.koharu.io.ResourceResolver;
import com.johntitor.koharu.utils.ClassUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected Map<String, BeanDefinition> beans = new HashMap<String, BeanDefinition>();
    protected final PropertyResolver propertyResolver;

    private final String SUFFIX = ".class";
    private final int SUFFIX_LENGTH = SUFFIX.length();

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {

        this.propertyResolver = propertyResolver;

        // 扫描获取所有Bean的Class类型:
        final Set<String> beanClassNames = scanForClassNames(configClass);

        // 创建Bean的定义:
        this.beans = createBeanDefinitions(beanClassNames);

    }

    /**
     * Do component scan and return class names.
     */
    protected Set<String> scanForClassNames(Class<?> configClass) {
        // 获取要扫描的package名称:
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        final String[] scanPackages = (scan != null && scan.value().length > 0) ? scan.value() : new String[]{configClass.getPackage().getName()};
        logger.info("component scan in packages: {}", Arrays.toString(scanPackages));

        Set<String> classNameSet = new HashSet<>();
        for (String pkg : scanPackages) {
            // 扫描package:
            logger.debug("scan package: {}", pkg);
            ResourceResolver rr = new ResourceResolver(pkg);
            List<String> classList = rr.scan(res -> {
                String name = res.name();
                if (name.endsWith(SUFFIX)) {
                    return name.substring(0, name.length() - SUFFIX_LENGTH).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            if (logger.isDebugEnabled()) {
                classList.forEach((className) -> {
                    logger.debug("class found by component scan: {}", className);
                });
            }
            classNameSet.addAll(classList);
        }

        // 查找@Import(Xyz.class):
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig != null) {
            for (Class<?> importConfigClass : importConfig.value()) {
                String importClassName = importConfigClass.getName();
                if (classNameSet.contains(importClassName)) {
                    logger.warn("ignore import: " + importClassName + " for it is already been scanned.");
                } else {
                    logger.debug("class found by import: {}", importClassName);
                    classNameSet.add(importClassName);
                }
            }
        }
        return classNameSet;
    }

    /**
     * 根据扫描的 ClassName 集合创建 BeanDefinition
     */
    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet) {
        // 用于存放最终生成的 BeanDefinition
        Map<String, BeanDefinition> defs = new HashMap<>();

        for (String className : classNameSet) {
            /*
             * 1. 加载类对象
             */
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                // 如果类加载失败，直接抛异常（不允许扫描结果里有无效类）
                throw new BeanCreationException(e);
            }

            /*
             * 2. 排除不符合条件的类型：
             *    - 注解类 (annotation)
             *    - 枚举类 (enum)
             *    - 接口 (interface)
             *    - 记录 (record, Java 14+)
             */
            if (clazz.isAnnotation() || clazz.isEnum() || clazz.isInterface() || clazz.isRecord()) {
                continue;
            }

            /*
             * 3. 判断类是否带有 @Component 注解
             *    （支持元注解，即 @Service/@Controller/@Repository 这种继承自 @Component 的注解）
             */
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if (component != null) {
                logger.debug("found component: {}", clazz.getName());

                /*
                 * 4. 校验类修饰符
                 *    - 抽象类 (abstract) 不允许标注 @Component（无法实例化）
                 *    - 私有类 (private) 也不允许（IOC 容器无法访问）
                 */
                int mod = clazz.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be abstract.");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be private.");
                }

                /*
                 * 5. 构建 BeanDefinition
                 *    - beanName：从 @Component/@Service/... 的 value 或者类名首字母小写生成
                 *    - beanClass：类对象本身
                 *    - constructor：选择合适的构造函数
                 *    - order：顺序（可能来自 @Order 注解）
                 *    - primary：是否标记为 @Primary
                 *    - initMethod/destroyMethod：查找 @PostConstruct / @PreDestroy 方法
                 */
                String beanName = ClassUtils.getBeanName(clazz);
                BeanDefinition def = new BeanDefinition(
                        beanName,
                        clazz,
                        getSuitableConstructor(clazz),
                        getOrder(clazz),
                        clazz.isAnnotationPresent(Primary.class),
                        // 用户自定义的 init/destroy 方法名（此处先留空）
                        null, null,
                        // 生命周期回调方法
                        ClassUtils.findAnnotationMethod(clazz, PostConstruct.class), // init
                        ClassUtils.findAnnotationMethod(clazz, PreDestroy.class)     // destroy
                );

                // 6. 注册 BeanDefinition到defs中
                addBeanDefinitions(defs, def);
                logger.debug("define bean: {}", def);

                /*
                 * 7. 如果是 @Configuration 类
                 *    - 扫描其中的工厂方法（@Bean 标注的方法）
                 *    - 这些方法返回的对象也需要注册为 BeanDefinition
                 */
                Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
                if (configuration != null) {
                    scanFactoryMethods(beanName, clazz, defs);
                }
            }
        }

        // 返回构建好的 BeanDefinition 集合
        return defs;
    }

    /**
     * 扫描指定类中标注了 @Bean 的方法，将其转换为 BeanDefinition 并注册到 defs 中
     * <p>
     * <code>
     * &#64;Configuration
     * public class Hello {
     * &#064;Bean
     * ZoneId createZone() {
     * return ZoneId.of("Z");
     * }
     * }
     * </code>
     */
    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> defs) {
        // 遍历类中声明的所有方法，包括 private、protected、public
        for (Method method : clazz.getDeclaredMethods()) {
            // 检查方法上是否有 @Bean 注解
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                // 获取方法修饰符
                int mod = method.getModifiers();

                // 校验方法不能是抽象方法
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract.");
                }
                // 校验方法不能是 final 方法（Spring 可能需要代理）
                if (Modifier.isFinal(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be final.");
                }
                // 校验方法不能是 private 方法（无法外部调用）
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
                }

                // 获取方法返回类型
                Class<?> beanClass = method.getReturnType();

                // 校验返回类型不能是基本类型
                if (beanClass.isPrimitive()) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return primitive type.");
                }
                // 校验返回类型不能是 void
                if (beanClass == void.class || beanClass == Void.class) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
                }

                // 构建 BeanDefinition
                var def = new BeanDefinition(
                        ClassUtils.getBeanName(method),   // bean 名称，默认根据方法名生成
                        beanClass,                        // bean 类型
                        factoryBeanName,                  // 工厂 bean 名称
                        method,                           // 对应的工厂方法
                        getOrder(method),                 // bean 初始化顺序
                        method.isAnnotationPresent(Primary.class), // 是否是主 Bean
                        // init 方法，如果注解中指定为空则为 null
                        bean.initMethod().isEmpty() ? null : bean.initMethod(),
                        // destroy 方法，如果注解中指定为空则为 null
                        bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                        // @PostConstruct / @PreDestroy 方法，这里暂时为 null
                        null, null
                );

                // 将 BeanDefinition 注册到 defs map
                addBeanDefinitions(defs, def);

                // 打印调试日志，输出定义的 Bean 信息
                logger.debug("define bean: {}", def);
            }
        }
    }

    /**
     * 根据规则获取类的合适构造函数。
     * <p>
     * 规则：
     * 1. 优先尝试获取所有 public 构造函数；
     * - 如果有且仅有 1 个 public 构造函数，则直接返回。
     * - 如果有多个 public 构造函数，则抛出异常（因为框架没法自动决定用哪个）。
     * <p>
     * 2. 如果没有 public 构造函数，则退而求其次，获取 declared 构造函数（包括 private/protected/package-private）。
     * - 如果只有 1 个 declared 构造函数，则返回它。
     * - 如果有多个 declared 构造函数，则抛出异常（因为存在歧义）。
     * <p>
     * 总结：只能接受“唯一可选”的构造函数，否则抛出异常。
     */
    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        // 1. 获取所有 public 构造函数
        Constructor<?>[] cons = clazz.getConstructors();

        // 2. 如果没有 public 构造函数，则获取 declared 构造函数
        if (cons.length == 0) {
            cons = clazz.getDeclaredConstructors();

            // declared 构造函数数量必须唯一，否则抛异常
            if (cons.length != 1) {
                throw new BeanDefinitionException("More than one constructor found in class " + clazz.getName() + ".");
            }
        }

        // 3. 如果有多个 public 构造函数，也不允许（必须唯一）
        if (cons.length != 1) {
            throw new BeanDefinitionException("More than one public constructor found in class " + clazz.getName() + ".");
        }

        // 4. 返回唯一可用的构造函数
        return cons[0];
    }

    /**
     * 添加bean定义类至beans，避免重复添加
     */
    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if (defs.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }


    /**
     * Get order by:
     * <p>
     * <code>
     * &#64;Order(100)
     * &#64;Component
     * public class Hello {}
     * </code>
     */
    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    /**
     * Get order by:
     * <p>
     * <code>
     * &#64;Order(100)
     * &#64;Bean
     * Hello createHello() {
     * return new Hello();
     * }
     * </code>
     */
    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }


}
