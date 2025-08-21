package com.johntitor.koharu.aop.around;

import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.ComponentScan;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class AroundApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
