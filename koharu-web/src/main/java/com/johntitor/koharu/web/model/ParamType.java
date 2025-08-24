package com.johntitor.koharu.web.model;

public enum ParamType {
    // 路径参数，从URL中提取
    PATH_VARIABLE,
    // URL参数，从URL Query或Form表单提取
    REQUEST_PARAM,
    // REST请求参数，从Post传递的JSON提取
    REQUEST_BODY,
    // HttpServletRequest等Servlet API提供的参数，直接从DispatcherServlet的方法参数获得
    SERVLET_VARIABLE;
}
