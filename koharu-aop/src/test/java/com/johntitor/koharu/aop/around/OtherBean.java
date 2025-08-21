package com.johntitor.koharu.aop.around;

import com.johntitor.koharu.annotation.Autowired;
import com.johntitor.koharu.annotation.Component;
import com.johntitor.koharu.annotation.Order;

@Order(0)
@Component
public class OtherBean {

    public OriginBean origin;

    public OtherBean(@Autowired OriginBean origin) {
        this.origin = origin;
    }
}

