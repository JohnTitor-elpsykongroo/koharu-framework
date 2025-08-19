package com.itranswarp.scan.destroy;

import com.johntitor.koharu.annotation.Component;
import com.johntitor.koharu.annotation.Value;
import jakarta.annotation.PreDestroy;

@Component
public class AnnotationDestroyBean {

    @Value("${app.title}")
    public String appTitle;

    @PreDestroy
    void destroy() {
        this.appTitle = null;
    }
}
