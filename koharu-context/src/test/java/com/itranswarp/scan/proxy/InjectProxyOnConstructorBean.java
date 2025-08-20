package com.itranswarp.scan.proxy;

import com.johntitor.koharu.annotation.Autowired;
import com.johntitor.koharu.annotation.Component;

@Component
public class InjectProxyOnConstructorBean {

    public final OriginBean injected;

    public InjectProxyOnConstructorBean(@Autowired OriginBean injected) {
        this.injected = injected;
    }
}
