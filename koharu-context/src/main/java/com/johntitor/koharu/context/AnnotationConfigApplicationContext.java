package com.johntitor.koharu.context;

import com.johntitor.koharu.annotation.*;
import com.johntitor.koharu.exception.*;
import com.johntitor.koharu.io.PropertyResolver;
import com.johntitor.koharu.io.ResourceResolver;
import com.johntitor.koharu.utils.ClassUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private Map<String, BeanDefinition> beans = new HashMap<String, BeanDefinition>();
    private Set<String> creatingBeanNames;
    private final PropertyResolver propertyResolver;

    private final String SUFFIX = ".class";
    private final int SUFFIX_LENGTH = SUFFIX.length();

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {

        this.propertyResolver = propertyResolver;

        // 扫描获取所有Bean的Class类型:
        final Set<String> beanClassNames = scanForClassNames(configClass);

        // 创建Bean的定义:
        this.beans = createBeanDefinitions(beanClassNames);

        createBeanInstances();

        // 通过字段和set方法注入依赖:
        this.beans.values().forEach(this::injectBean);

        // 调用init方法:
        this.beans.values().forEach(this::initBean);
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
     * 添加bean定义类至beans，避免重复添加
     */
    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if (defs.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }

    /**
     * 创建Configuration的Bean
     */
    void createBeanInstances() {
        this.creatingBeanNames = new HashSet<>();

        createConfigurationBeans();
        createNormalBeans();

        if (logger.isDebugEnabled()) {
            this.beans.values().stream().sorted().forEach(def -> {
                logger.debug("bean initialized: {}", def);
            });
        }
    }

    /**
     * 创建Configuration的Bean
     */
    void createConfigurationBeans() {
        this.beans.values().stream()
                // 过滤出 @Configuration 类
                .filter(this::isConfigurationDefinition)
                // 按顺序排序
                .sorted()
                // 遍历每个 BeanDefinition，进行早期实例化
                .forEach(this::createBeanAsEarlySingleton);
    }

    /**
     * 创建普通的Bean
     */
    void createNormalBeans() {
        this.beans.values().stream()
                // filter bean definitions by not instantiation:
                .filter(def -> def.getInstance() == null)
                .sorted()
                .forEach(def -> {
                    // 如果Bean未被创建(可能在其他Bean的构造方法注入前被创建):
                    if (def.getInstance() == null) {
                        // 创建Bean:
                        createBeanAsEarlySingleton(def);
                    }
                });
    }

    /**
     * 创建一个Bean，但不进行字段和方法级别的注入。如果创建的Bean不是Configuration，则在构造方法中注入的依赖Bean会自动创建。
     */
    public Object createBeanAsEarlySingleton(BeanDefinition def) {
        logger.atDebug().log("Try create bean '{}' as early singleton: {}", def.getName(), def.getBeanClass().getName());
        if (!this.creatingBeanNames.add(def.getName())) {
            throw new UnsatisfiedDependencyException(String.format("Circular dependency detected when create bean '%s'", def.getName()));
        }

        // 创建方式：构造方法或工厂方法:
        Executable createFn = null;
        if (def.getFactoryName() == null) {
            // by constructor:
            createFn = def.getConstructor();
        } else {
            // by factory method:
            createFn = def.getFactoryMethod();
        }

        // 创建参数:
        final Parameter[] parameters = createFn.getParameters();
        final Annotation[][] parametersAnnos = createFn.getParameterAnnotations();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            final Parameter param = parameters[i];
            final Annotation[] paramAnnos = parametersAnnos[i];
            final Value value = ClassUtils.getAnnotation(paramAnnos, Value.class);
            final Autowired autowired = ClassUtils.getAnnotation(paramAnnos, Autowired.class);

            // @Configuration类型的Bean是工厂，不允许使用@Autowired创建:
            final boolean isConfiguration = isConfigurationDefinition(def);
            if (isConfiguration && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify @Autowired when create @Configuration bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }

            // 参数需要@Value或@Autowired两者之一:
            if (value != null && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify both @Autowired and @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            if (value == null && autowired == null) {
                throw new BeanCreationException(
                        String.format("Must specify @Autowired or @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            // 参数类型:
            final Class<?> type = param.getType();
            if (value != null) {
                // 参数是@Value:
                args[i] = this.propertyResolver.getRequiredProperty(value.value(), type);
            } else {
                // 参数是@Autowired:
                String name = autowired.name();
                boolean required = autowired.value();
                // 依赖的BeanDefinition:
                BeanDefinition dependsOnDef = name.isEmpty() ? findBeanDefinition(type) : findBeanDefinition(name, type);
                // 检测required==true?
                if (required && dependsOnDef == null) {
                    throw new BeanCreationException(String.format("Missing autowired bean with type '%s' when create bean '%s': %s.", type.getName(),
                            def.getName(), def.getBeanClass().getName()));
                }
                if (dependsOnDef != null) {
                    // 获取依赖Bean:
                    Object autowiredBeanInstance = dependsOnDef.getInstance();
                    if (autowiredBeanInstance == null && !isConfiguration) {
                        // 当前依赖Bean尚未初始化，递归调用初始化该依赖Bean:
                        autowiredBeanInstance = createBeanAsEarlySingleton(dependsOnDef);
                    }
                    args[i] = autowiredBeanInstance;
                } else {
                    args[i] = null;
                }
            }
        }

        // 创建Bean实例:
        Object instance = null;
        if (def.getFactoryName() == null) {
            // 用构造方法创建:
            try {
                instance = def.getConstructor().newInstance(args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        } else {
            // 用@Bean方法创建:
            Object configInstance = getBean(def.getFactoryName());
            try {
                instance = def.getFactoryMethod().invoke(configInstance, args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        }
        def.setInstance(instance);
        return def.getInstance();
    }

    /**
     * 注入依赖但不调用init方法
     */
    void injectBean(BeanDefinition def) {
        try {
            injectProperties(def, def.getBeanClass(), def.getInstance());
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException(e);
        }
    }

    /**
     * 注入属性
     */
    void injectProperties(BeanDefinition def, Class<?> clazz, Object bean) throws ReflectiveOperationException {
        // 在当前类查找Field和Method并注入:
        for (Field f : clazz.getDeclaredFields()) {
            tryInjectProperties(def, clazz, bean, f);
        }
        for (Method m : clazz.getDeclaredMethods()) {
            tryInjectProperties(def, clazz, bean, m);
        }
        // 在父类查找Field和Method并注入:
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            injectProperties(def, superClazz, bean);
        }
    }

    /**
     * 注入单个属性
     */
    // ================== Field 注入 ==================
    void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object bean, Field field) throws ReflectiveOperationException {
        Value value = field.getAnnotation(Value.class);
        Autowired autowired = field.getAnnotation(Autowired.class);

        if (value == null && autowired == null) {
            return;
        }

        checkFieldOrMethod(field);
        field.setAccessible(true);

        doInject(def, clazz, bean,
                field.getName(),
                field.getType(),
                value,
                autowired,
                (target, val) -> {
                    try {
                        field.set(target, val);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                },
                "Field");
    }

    // ================== Method 注入 ==================
    void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object bean, Method method) throws ReflectiveOperationException {
        Value value = method.getAnnotation(Value.class);
        Autowired autowired = method.getAnnotation(Autowired.class);

        if (value == null && autowired == null) {
            return;
        }

        checkFieldOrMethod(method);
        if (method.getParameterCount() != 1) {
            throw new BeanDefinitionException(String.format(
                    "Cannot inject a non-setter method %s for bean '%s': %s",
                    method.getName(), def.getName(), def.getBeanClass().getName()));
        }
        method.setAccessible(true);

        doInject(def, clazz, bean,
                method.getName(),
                method.getParameterTypes()[0],
                value,
                autowired,
                (target, val) -> {
                    try {
                        method.invoke(target, val);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                },
                "Method");
    }


    // ================== 公共注入逻辑 ==================

    /**
     * 处理 @Value 和 @Autowired 注入逻辑的公共方法。
     *
     * @param def            BeanDefinition 定义
     * @param clazz          当前正在注入的类
     * @param bean           需要注入属性的实例对象
     * @param accessibleName 可注入成员名称（field 名称或 method 名称）
     * @param accessibleType 可注入成员类型（field 类型或 setter 参数类型）
     * @param value          @Value 注解（可能为 null）
     * @param autowired      @Autowired 注解（可能为 null）
     * @param injector       最终执行注入的动作 (targetBean, value) -> {}
     * @param injectType     执行任务类型
     */
    private void doInject(BeanDefinition def, Class<?> clazz, Object bean,
                          String accessibleName, Class<?> accessibleType,
                          Value value, Autowired autowired,
                          BiConsumer<Object, Object> injector,
                          String injectType) throws ReflectiveOperationException {

        // 如果没有注解，直接返回
        if (value == null && autowired == null) {
            return;
        }

        // 同时存在 @Value 和 @Autowired 属于非法用法
        if (value != null && autowired != null) {
            throw new BeanCreationException(String.format(
                    "Cannot specify both @Autowired and @Value when inject %s.%s for bean '%s': %s",
                    clazz.getSimpleName(), accessibleName, def.getName(), def.getBeanClass().getName()));
        }

        // @Value 注入配置属性
        if (value != null) {
            Object propValue = this.propertyResolver.getRequiredProperty(value.value(), accessibleType);
            logger.debug("{} Injection via @Value: {}.{} = {}", injectType, def.getBeanClass().getName(), accessibleName, propValue);
            injector.accept(bean, propValue);
        }

        // @Autowired 注入依赖 Bean
        if (autowired != null) {
            String name = autowired.name();
            boolean required = autowired.value();
            Object depends = name.isEmpty() ? findBean(accessibleType) : findBean(name, accessibleType);

            // 必须依赖缺失则抛异常
            if (required && depends == null) {
                throw new UnsatisfiedDependencyException(String.format(
                        "Dependency bean not found when inject %s.%s for bean '%s': %s",
                        clazz.getSimpleName(), accessibleName, def.getName(), def.getBeanClass().getName()));
            }

            // 找到依赖则注入
            if (depends != null) {
                logger.debug("{} Injection via @Autowired: {}.{} = {}", injectType, def.getBeanClass().getName(), accessibleName, depends);
                injector.accept(bean, depends);
            }
        }
    }


    void checkFieldOrMethod(Member m) {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new BeanDefinitionException("Cannot inject static field: " + m);
        }
        if (Modifier.isFinal(mod)) {
            if (m instanceof Field field) {
                throw new BeanDefinitionException("Cannot inject final field: " + field);
            }
            if (m instanceof Method method) {
                logger.warn(
                        "Inject final method should be careful because it is not called on target bean when bean is proxied and may cause NullPointerException.");
            }
        }
    }

    /**
     * 调用init方法
     */
    void initBean(BeanDefinition def) {
        // 调用init方法:
        callMethod(def.getInstance(), def.getInitMethod(), def.getInitMethodName());
    }

    private void callMethod(Object beanInstance, Method method, String namedMethod) {
        // 调用init/destroy方法:
        if (method != null) {
            try {
                method.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else if (namedMethod != null) {
            // 查找initMethod/destroyMethod="xyz"，注意是在实际类型中查找:
            Method named = ClassUtils.getNamedMethod(beanInstance.getClass(), namedMethod);
            named.setAccessible(true);
            try {
                named.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }

    boolean isConfigurationDefinition(BeanDefinition def) {
        return ClassUtils.findAnnotation(def.getBeanClass(), Configuration.class) != null;
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

    /**
     * 根据Name查找BeanDefinition，如果Name不存在，返回null
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    /**
     * 根据Name和Type查找BeanDefinition，如果Name不存在，返回null，如果Name存在，但Type不匹配，抛出异常。
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        if (!requiredType.isAssignableFrom(def.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format("Autowire required type '%s' but bean '%s' has actual type '%s'.", requiredType.getName(),
                    name, def.getBeanClass().getName()));
        }
        return def;
    }

    /**
     * 根据Type查找若干个BeanDefinition，返回0个或多个。
     */
    public List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beans.values().stream()
                // filter by type and sub-type:
                .filter(def -> type.isAssignableFrom(def.getBeanClass()))
                // 排序:
                .sorted().collect(Collectors.toList());
    }

    /**
     * 根据Type查找某个BeanDefinition，如果不存在返回null，如果存在多个返回@Primary标注的一个，如果有多个@Primary标注，或没有@Primary标注但找到多个，均抛出NoUniqueBeanDefinitionException
     */
    @Nullable
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> defs = findBeanDefinitions(type);
        if (defs.isEmpty()) {
            return null;
        }
        if (defs.size() == 1) {
            return defs.get(0);
        }
        // more than 1 beans, require @Primary:
        List<BeanDefinition> primaryDefs = defs.stream().filter(def -> def.isPrimary()).collect(Collectors.toList());
        if (primaryDefs.size() == 1) {
            return primaryDefs.get(0);
        }
        if (primaryDefs.isEmpty()) {
            throw new NoUniqueBeanDefinitionException(String.format("Multiple bean with type '%s' found, but no @Primary specified.", type.getName()));
        } else {
            throw new NoUniqueBeanDefinitionException(String.format("Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }

    /**
     * 通过Name查找Bean，不存在时抛出NoSuchBeanDefinitionException
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        BeanDefinition def = this.beans.get(name);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s'.", name));
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 通过Name和Type查找Bean，不存在抛出NoSuchBeanDefinitionException，存在但与Type不匹配抛出BeanNotOfRequiredTypeException
     */
    public <T> T getBean(String name, Class<T> requiredType) {
        T t = findBean(name, requiredType);
        if (t == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s' and type '%s'.", name, requiredType));
        }
        return t;
    }

    /**
     * 通过Type查找Beans
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getBeans(Class<T> requiredType) {
        List<BeanDefinition> defs = findBeanDefinitions(requiredType);
        if (defs.isEmpty()) {
            return List.of();
        }
        List<T> list = new ArrayList<>(defs.size());
        for (var def : defs) {
            list.add((T) def.getRequiredInstance());
        }
        return list;
    }

    /**
     * 通过Type查找Bean，不存在抛出NoSuchBeanDefinitionException，存在多个但缺少唯一@Primary标注抛出NoUniqueBeanDefinitionException
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with type '%s'.", requiredType));
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 检测是否存在指定Name的Bean
     */
    public boolean containsBean(String name) {
        return this.beans.containsKey(name);
    }

    // findXxx与getXxx类似，但不存在时返回null

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(String name, Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(name, requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> List<T> findBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream().map(def -> (T) def.getRequiredInstance()).collect(Collectors.toList());
    }


}
