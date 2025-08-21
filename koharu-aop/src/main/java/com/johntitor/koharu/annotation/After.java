package com.johntitor.koharu.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface After {

    /**
     * Invocation handler bean name.
     */
    String value();
}
