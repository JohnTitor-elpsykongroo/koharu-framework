package com.johntitor.koharu.web.utils;

import com.johntitor.koharu.context.ApplicationContextContainer;
import com.johntitor.koharu.io.PropertyResolver;
import com.johntitor.koharu.utils.ClassPathUtils;
import com.johntitor.koharu.utils.YamlUtils;
import com.johntitor.koharu.web.DispatcherServlet;
import com.johntitor.koharu.web.FilterRegistrationBean;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.*;

public class WebUtils {
    protected static final Logger logger = LoggerFactory.getLogger(WebUtils.class);

    private static final String CONFIG_APP_YAML = "/application.yml";
    private static final String CONFIG_APP_PROP = "/application.properties";

    public static final String DEFAULT_PARAM_VALUE = "\0\t\0\t\0";

    /**
     * 加载配置文件并封装成 PropertyResolver
     * Try load property resolver from /application.yml or /application.properties.
     */
    public static PropertyResolver createPropertyResolver() {
        final Properties props = new Properties();
        // 优先加载 application.yml
        try {
            Map<String, Object> ymlMap = YamlUtils.loadYamlAsPlainMap(CONFIG_APP_YAML);
            logger.info("load config: {}", CONFIG_APP_YAML);
            for (String key : ymlMap.keySet()) {
                Object value = ymlMap.get(key);
                if (value instanceof String strValue) {
                    props.put(key, strValue);
                }
            }
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                // 尝试加载 application.properties；
                ClassPathUtils.readInputStream(CONFIG_APP_PROP, (input) -> {
                    logger.info("load config: {}", CONFIG_APP_PROP);
                    props.load(input);
                    return true;
                });
            }
        }
        return new PropertyResolver(props);
    }

    // 注册DispatcherServlet
    public static void registerDispatcherServlet(ServletContext servletContext, PropertyResolver propertyResolver) {
        // 实例化DispatcherServlet:
        var dispatcherServlet = new DispatcherServlet(ApplicationContextContainer.getRequiredApplicationContext(), propertyResolver);
        logger.info("register servlet {} for URL '/'", dispatcherServlet.getClass().getName());
        // 注册DispatcherServlet:
        var dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet);
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
    }

    // 注册Filter
    public static void registerFilters(ServletContext servletContext) {
        // 获取应用上下文
        var applicationContext = ApplicationContextContainer.getRequiredApplicationContext();
        for (var filterRegBean : applicationContext.getBeans(FilterRegistrationBean.class)) {
            // 获取URL模式，每个Filter必须有URL匹配规则，否则抛异常。
            List<String> urlPatterns = filterRegBean.getUrlPatterns();
            if (urlPatterns == null || urlPatterns.isEmpty()) {
                throw new IllegalArgumentException("No url patterns for {}" + filterRegBean.getClass().getName());
            }
            // 子类必须返回具体的Filter
            var filter = Objects.requireNonNull(filterRegBean.getFilter(), "FilterRegistrationBean.getFilter() must not return null.");
            logger.info("register filter '{}' {} for URLs: {}", filterRegBean.getName(), filter.getClass().getName(), String.join(", ", urlPatterns));
            // 注册 Filter 到 ServletContext
            var filterReg = servletContext.addFilter(filterRegBean.getName(), filter);
            // 指定 Filter 拦截哪些 URL
            filterReg.addMappingForUrlPatterns(
                    // 只拦截普通请求
                    EnumSet.of(DispatcherType.REQUEST),
                    //true → isMatchAfter，表示是否在已有 Filter 之后匹配
                    true,
                    // 指定获取到的URL模式
                    urlPatterns.toArray(String[]::new)
            );
        }
    }
}
