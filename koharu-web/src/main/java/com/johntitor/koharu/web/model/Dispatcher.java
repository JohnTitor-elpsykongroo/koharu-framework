package com.johntitor.koharu.web.model;

import com.johntitor.koharu.annotation.ResponseBody;
import com.johntitor.koharu.exception.ServerErrorException;
import com.johntitor.koharu.exception.ServerWebInputException;
import com.johntitor.koharu.web.utils.JsonUtils;
import com.johntitor.koharu.web.utils.PathUtils;
import com.johntitor.koharu.web.utils.WebUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dispatcher {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    final static Result NOT_PROCESSED = new Result(false, null);
    // 是否返回REST:
    private boolean isRest;
    // 是否有@ResponseBody:
    private boolean isResponseBody;
    // 是否返回void:
    private boolean isVoid;
    // URL正则匹配:
    private Pattern urlPattern;
    // Bean实例:
    private Object controller;
    // 处理方法:
    private Method handlerMethod;
    // 方法参数:
    private Param[] methodParameters;

    public Dispatcher(String httpMethod, boolean isRest, Object controller, Method method, String urlPattern) throws ServletException {
        this.isRest = isRest;
        this.isResponseBody = method.getAnnotation(ResponseBody.class) != null;
        this.isVoid = method.getReturnType() == void.class;
        this.urlPattern = PathUtils.compile(urlPattern);
        // 把传进来的字符串形式 URL 转换成正则表达式对象
        this.controller = controller;
        this.handlerMethod = method;

        Parameter[] params = method.getParameters();
        Annotation[][] paramsAnnos = method.getParameterAnnotations();
        this.methodParameters = new Param[params.length];
        for (int i = 0; i < params.length; i++) {
            this.methodParameters[i] = new Param(httpMethod, method, params[i], paramsAnnos[i]);
        }
        logger.debug("mapping {} to handler {}.{}", urlPattern, controller.getClass().getSimpleName(), method.getName());
        if (logger.isDebugEnabled()) {
            for (var p : this.methodParameters) {
                logger.debug("> parameter: {}", p);
            }
        }
    }

    // 找到 Controller 方法并执行
    public Result process(String url, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Matcher matcher = urlPattern.matcher(url);
        // 如果不匹配，就直接返回 NOT_PROCESSED
        if (! matcher.matches()) {
            return NOT_PROCESSED;
        }
        // 准备方法参数
        Object[] arguments = new Object[this.methodParameters.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = resolveArgument(methodParameters[i], matcher, request, response);
        }

        // 调用 Controller 方法
        Object result = null;
        try {
            result = this.handlerMethod.invoke(this.controller, arguments);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception ex) {
                throw ex;
            }
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new ServerErrorException(e);
        }
        return new Result(true, result);
    }

    private Object resolveArgument(Param param, Matcher matcher,
                                   HttpServletRequest request, HttpServletResponse response) throws Exception {
        return switch (param.paramType) {
            case PATH_VARIABLE -> {
                // 从 URL 的正则命名分组里取参数，比如 /user/123 → id = 123, 再转成目标类型
                try {
                    String s = matcher.group(param.name);
                    yield convertToType(param.classType, s);
                } catch (IllegalArgumentException e) {
                    throw new ServerWebInputException("Path variable '" + param.name + "' not found.");
                }
            }
            case REQUEST_BODY -> {
                // 从 HTTP 请求体读取数据（比如 JSON）,然后用 JsonUtils 反序列化成目标对象
                BufferedReader reader = request.getReader();
                yield JsonUtils.readJson(reader, param.classType);
            }
            case REQUEST_PARAM -> {
                // 从 query 参数或 form 表单里取值(类似 ?page=2),如果没有则用默认值，再转成目标类型
                String s = getOrDefault(request, param.name, param.defaultValue);
                yield convertToType(param.classType, s);
            }
            case SERVLET_VARIABLE -> {
                // 如果方法参数直接是 HttpServletRequest/HttpServletResponse/HttpSession 等，则直接注入 Servlet 原始对象。
                Class<?> classType = param.classType;
                if (classType == HttpServletRequest.class) {
                    yield request;
                } else if (classType == HttpServletResponse.class) {
                    yield response;
                } else if (classType == HttpSession.class) {
                    yield request.getSession();
                } else if (classType == ServletContext.class) {
                    yield request.getServletContext();
                } else {
                    throw new ServerErrorException("Could not determine argument type: " + classType);
                }
            }
        };
    }

    Object convertToType(Class<?> classType, String s) {
        if (classType == String.class) {
            return s;
        } else if (classType == boolean.class || classType == Boolean.class) {
            return Boolean.valueOf(s);
        } else if (classType == int.class || classType == Integer.class) {
            return Integer.valueOf(s);
        } else if (classType == long.class || classType == Long.class) {
            return Long.valueOf(s);
        } else if (classType == byte.class || classType == Byte.class) {
            return Byte.valueOf(s);
        } else if (classType == short.class || classType == Short.class) {
            return Short.valueOf(s);
        } else if (classType == float.class || classType == Float.class) {
            return Float.valueOf(s);
        } else if (classType == double.class || classType == Double.class) {
            return Double.valueOf(s);
        } else {
            throw new ServerErrorException("Could not determine argument type: " + classType);
        }
    }

    String getOrDefault(HttpServletRequest request, String name, String defaultValue) {
        String s = request.getParameter(name);
        if (s == null) {
            if (WebUtils.DEFAULT_PARAM_VALUE.equals(defaultValue)) {
                throw new ServerWebInputException("Request parameter '" + name + "' not found.");
            }
            return defaultValue;
        }
        return s;
    }

    public boolean isRest() {
        return isRest;
    }

    public boolean isResponseBody() {
        return isResponseBody;
    }

    public boolean isVoid() {
        return isVoid;
    }
}

