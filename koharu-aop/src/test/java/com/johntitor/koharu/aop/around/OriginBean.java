package com.johntitor.koharu.aop.around;

import com.johntitor.koharu.annotation.Around;
import com.johntitor.koharu.annotation.Component;
import com.johntitor.koharu.annotation.Value;

@Component
@Around("aroundInvocationHandler")
public class OriginBean {

    @Value("${customer.name}")
    public String name;

    @Polite
    public String hello() {
        return "Hello, " + name + ".";
    }

    public String morning() {
        return "Morning, " + name + ".";
    }
}
