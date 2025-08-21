package com.johntitor.koharu.aop.after;

import com.johntitor.koharu.annotation.After;
import com.johntitor.koharu.annotation.Component;

@Component
@After("politeInvocationHandler")
public class GreetingBean {

    public String hello(String name) {
        return "Hello, " + name + ".";
    }

    public String morning(String name) {
        return "Morning, " + name + ".";
    }
}

