package com.johntitor.koharu.web;

import com.johntitor.koharu.annotation.Autowired;
import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.annotation.Value;
import jakarta.servlet.ServletContext;

import java.util.Objects;

@Configuration
public class WebMvcConfiguration {

    private static ServletContext servletContext = null;

    /**
     * Set by web listener.
     */
    public static void setServletContext(ServletContext ctx) {
        servletContext = ctx;
    }

    @Bean(initMethod = "init")
    ViewResolver viewResolver( //
                               @Autowired ServletContext servletContext, //
                               @Value("${koharu.web.freemarker.template-path:/WEB-INF/templates}") String templatePath, //
                               @Value("${koharu.web.freemarker.template-encoding:UTF-8}") String templateEncoding) {
        return new FreeMarkerViewResolver(servletContext, templatePath, templateEncoding);
    }

    @Bean
    ServletContext servletContext() {
        return Objects.requireNonNull(servletContext, "ServletContext is not set.");
    }
}
