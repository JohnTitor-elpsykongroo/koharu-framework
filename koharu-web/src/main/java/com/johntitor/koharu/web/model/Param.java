package com.johntitor.koharu.web.model;

import com.johntitor.koharu.annotation.PathVariable;
import com.johntitor.koharu.annotation.RequestBody;
import com.johntitor.koharu.annotation.RequestParam;
import com.johntitor.koharu.exception.ServerErrorException;
import com.johntitor.koharu.utils.ClassUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class Param {
    // 参数名称:
    String name;
    // 参数类型:
    ParamType paramType;
    // 参数Class类型:
    Class<?> classType;
    // 参数默认值
    String defaultValue;

    public Param(String httpMethod, Method method, Parameter parameter, Annotation[] annotations) throws ServletException {
        PathVariable pv = ClassUtils.getAnnotation(annotations, PathVariable.class);
        RequestParam rp = ClassUtils.getAnnotation(annotations, RequestParam.class);
        RequestBody rb = ClassUtils.getAnnotation(annotations, RequestBody.class);
        // should only have 1 annotation:
        int total = (pv == null ? 0 : 1) + (rp == null ? 0 : 1) + (rb == null ? 0 : 1);
        if (total > 1) {
            throw new ServletException("Annotation @PathVariable, @RequestParam and @RequestBody cannot be combined at method: " + method);
        }
        this.classType = parameter.getType();
        if (pv != null) {
            this.name = pv.value();
            this.paramType = ParamType.PATH_VARIABLE;
        } else if (rp != null) {
            this.name = rp.value();
            this.defaultValue = rp.defaultValue();
            this.paramType = ParamType.REQUEST_PARAM;
        } else if (rb != null) {
            this.paramType = ParamType.REQUEST_BODY;
        } else {
            this.paramType = ParamType.SERVLET_VARIABLE;
            // check servlet variable type:
            if (this.classType != HttpServletRequest.class && this.classType != HttpServletResponse.class && this.classType != HttpSession.class
                    && this.classType != ServletContext.class) {
                throw new ServerErrorException("(Missing annotation?) Unsupported argument type: " + classType + " at method: " + method);
            }
        }
    }

    @Override
    public String toString() {
        return "Param [name=" + name + ", paramType=" + paramType + ", classType=" + classType + ", defaultValue=" + defaultValue + "]";
    }
}
