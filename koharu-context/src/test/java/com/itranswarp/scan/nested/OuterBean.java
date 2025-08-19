package com.itranswarp.scan.nested;

import com.johntitor.koharu.annotation.Component;

@Component
public class OuterBean {

    @Component
    public static class NestedBean {

    }
}
