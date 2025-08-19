package com.itranswarp.scan.destroy;

import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.annotation.Value;

@Configuration
public class SpecifyDestroyConfiguration {

    @Bean(destroyMethod = "destroy")
    SpecifyDestroyBean createSpecifyDestroyBean(@Value("${app.title}") String appTitle) {
        return new SpecifyDestroyBean(appTitle);
    }
}
