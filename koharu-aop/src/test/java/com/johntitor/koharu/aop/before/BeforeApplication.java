package com.johntitor.koharu.aop.before;

import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.ComponentScan;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.aop.AroundProxyBeanPostProcessor;
import com.johntitor.koharu.aop.BeforeProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class BeforeApplication {

    @Bean
    BeforeProxyBeanPostProcessor createBeforeProxyBeanPostProcessor() {
        return new BeforeProxyBeanPostProcessor();
    }
}
