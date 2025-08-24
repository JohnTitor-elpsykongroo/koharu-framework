package com.johntitor.koharu.web.utils;

import com.johntitor.koharu.context.ApplicationContextContainer;
import com.johntitor.koharu.io.PropertyResolver;
import com.johntitor.koharu.utils.ClassPathUtils;
import com.johntitor.koharu.utils.YamlUtils;
import com.johntitor.koharu.web.DispatcherServlet;
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
        var dispatcherServlet = new DispatcherServlet(ApplicationContextContainer.getRequiredApplicationContext(),propertyResolver);
        logger.info("register servlet {} for URL '/'", dispatcherServlet.getClass().getName());
        // 注册DispatcherServlet:
        var dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet);
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
    }
}
