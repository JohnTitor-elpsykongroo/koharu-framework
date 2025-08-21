package com.johntitor.koharu.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Before {

    /**
     * Invocation handler bean name.
     */
    String value();

}

