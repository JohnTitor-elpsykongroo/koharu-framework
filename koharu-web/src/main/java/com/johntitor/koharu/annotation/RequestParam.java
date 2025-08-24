package com.johntitor.koharu.annotation;

import java.lang.annotation.*;
import com.johntitor.koharu.web.utils.WebUtils;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {

    String value();

    String defaultValue() default WebUtils.DEFAULT_PARAM_VALUE;
}
