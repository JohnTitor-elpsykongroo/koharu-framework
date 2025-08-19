package com.itranswarp.scan.init;

import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.annotation.Value;

@Configuration
public class SpecifyInitConfiguration {

    @Bean(initMethod = "init")
    SpecifyInitBean createSpecifyInitBean(@Value("${app.title}") String appTitle, @Value("${app.version}") String appVersion) {
        return new SpecifyInitBean(appTitle, appVersion);
    }
}
