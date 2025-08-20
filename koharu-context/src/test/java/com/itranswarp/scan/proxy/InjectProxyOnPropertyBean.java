package com.itranswarp.scan.proxy;

import com.johntitor.koharu.annotation.Autowired;
import com.johntitor.koharu.annotation.Component;

@Component
public class InjectProxyOnPropertyBean {

    @Autowired
    public OriginBean injected;
}
