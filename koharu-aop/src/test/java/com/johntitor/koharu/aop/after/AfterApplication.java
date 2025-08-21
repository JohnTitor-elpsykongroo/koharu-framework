package com.johntitor.koharu.aop.after;

import com.johntitor.koharu.annotation.After;
import com.johntitor.koharu.annotation.Bean;
import com.johntitor.koharu.annotation.ComponentScan;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.aop.AfterProxyBeanPostProcessor;
import com.johntitor.koharu.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class AfterApplication {

    @Bean
    AfterProxyBeanPostProcessor createAfterProxyBeanPostProcessor() {
        return new AfterProxyBeanPostProcessor();
    }
}
