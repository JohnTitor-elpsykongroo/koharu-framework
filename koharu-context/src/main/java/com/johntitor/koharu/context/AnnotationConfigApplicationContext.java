package com.johntitor.koharu.context;

import com.johntitor.koharu.annotation.ComponentScan;
import com.johntitor.koharu.annotation.Import;
import com.johntitor.koharu.io.PropertyResolver;
import com.johntitor.koharu.io.ResourceResolver;
import com.johntitor.koharu.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Map<String, BeanDefinition> beans = new HashMap<String, BeanDefinition>();
    protected final PropertyResolver propertyResolver;

    private final String SUFFIX = ".class";
    private final int SUFFIX_LENGTH = SUFFIX.length();

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {

        this.propertyResolver = propertyResolver;

        // 扫描获取所有Bean的Class类型:
        final Set<String> beanClassNames = scanForClassNames(configClass);

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


}
