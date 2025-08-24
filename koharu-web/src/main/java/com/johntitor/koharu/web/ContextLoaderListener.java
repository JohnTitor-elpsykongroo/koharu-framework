package com.johntitor.koharu.web;

import com.johntitor.koharu.context.AnnotationConfigApplicationContext;
import com.johntitor.koharu.context.ApplicationContext;
import com.johntitor.koharu.exception.NestedRuntimeException;
import com.johntitor.koharu.io.PropertyResolver;
import com.johntitor.koharu.web.utils.WebUtils;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextLoaderListener implements ServletContextListener {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    // Web 应用启动时回调
    @Override
    public void contextInitialized(ServletContextEvent sce){
        logger.info("init {}.", getClass().getName());
        // 获取 ServletContext
        var servletContext = sce.getServletContext();
        // 设置 ServletContext
        WebMvcConfiguration.setServletContext(servletContext);
        // 创建配置解析器
        var propertyResolver = WebUtils.createPropertyResolver();
        // 设置请求/响应编码
        String encoding = propertyResolver.getProperty("${koharu.web.character-encoding:UTF-8}");
        servletContext.setRequestCharacterEncoding(encoding);
        servletContext.setResponseCharacterEncoding(encoding);
        // 创建 ApplicationContext（IOC 容器）
        var applicationContext = createApplicationContext(
                // 读取 web.xml 或 ServletContext init-param 里的 "configuration" 参数
                servletContext.getInitParameter("configuration"),
                propertyResolver);
        // 注册 filters:
        WebUtils.registerFilters(servletContext);
        // 注册 DispatcherServlet
        WebUtils.registerDispatcherServlet(servletContext, propertyResolver);
        // 把 ApplicationContext 保存到全局
        servletContext.setAttribute("applicationContext", applicationContext);

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (sce.getServletContext().getAttribute("applicationContext") instanceof ApplicationContext applicationContext) {
            applicationContext.close();
        }
    }


    // 创建 ApplicationContext（IOC 容器）具体方法
    private Object createApplicationContext(String configClassName, PropertyResolver propertyResolver) {
        logger.info("init ApplicationContext by configuration: {}", configClassName);
        if (configClassName == null || configClassName.isEmpty()) {
            throw new NestedRuntimeException("Cannot init ApplicationContext for missing init param name: configuration");
        }
        Class<?> configClass;
        try {
            configClass = Class.forName(configClassName);
        } catch (ClassNotFoundException e) {
            throw new NestedRuntimeException("Could not load class from init param 'configuration': " + configClassName);
        }
        return new AnnotationConfigApplicationContext(configClass, propertyResolver);
    }
}
