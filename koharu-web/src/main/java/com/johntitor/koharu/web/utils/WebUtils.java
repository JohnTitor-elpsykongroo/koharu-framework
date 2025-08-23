package com.johntitor.koharu.web.utils;

import com.johntitor.koharu.context.ApplicationContext;
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
import java.util.Map;
import java.util.Properties;

public class WebUtils {
    protected static final Logger logger = LoggerFactory.getLogger(WebUtils.class);

    private static final String CONFIG_APP_YAML = "/application.yml";
    private static final String CONFIG_APP_PROP = "/application.properties";
    /**
     * Try load property resolver from /application.yml or /application.properties.
     */
    public static PropertyResolver createPropertyResolver() {
        final Properties props = new Properties();
        // try load application.yml:
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
                // try load application.properties:
                ClassPathUtils.readInputStream(CONFIG_APP_PROP, (input) -> {
                    logger.info("load config: {}", CONFIG_APP_PROP);
                    props.load(input);
                    return true;
                });
            }
        }
        return new PropertyResolver(props);
    }

    public static void registerDispatcherServlet(ServletContext servletContext, PropertyResolver propertyResolver) {
        var dispatcherServlet = new DispatcherServlet(ApplicationContextContainer.getRequiredApplicationContext(),propertyResolver);
        logger.info("register servlet {} for URL '/'", dispatcherServlet.getClass().getName());
        var dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet);
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
    }
}
