package com.johntitor.koharu.web;

import jakarta.servlet.Filter;

import java.util.List;

/**
 * FilterRegistrationBean抽象类 简化 Filter 的注册过程
 * 子类只需要实现：
 *      getFilter() 返回过滤器实例
 *      getUrlPatterns() 指定 URL 匹配
 * getName() 自动生成 Bean 名称，减少重复配置
 * */

public abstract class FilterRegistrationBean {

    private static final String FILTER_REGISTRATION_BEAN_NAME = "FilterRegistrationBean";
    private static final String FILTER_REGISTRATION_NAME = "FilterRegistration";

    /**
     * Get name by class name. Example:
     *
     * ApiFilterRegistrationBean -> apiFilter
     *
     * ApiFilterRegistration -> apiFilter
     *
     * ApiFilterReg -> apiFilterReg
     */
    public String getName() {
        String name = getClass().getSimpleName();
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        if (name.endsWith(FILTER_REGISTRATION_BEAN_NAME) && name.length() > FILTER_REGISTRATION_BEAN_NAME.length()) {
            return name.substring(0, name.length() - FILTER_REGISTRATION_BEAN_NAME.length());
        }
        if (name.endsWith(FILTER_REGISTRATION_NAME) && name.length() > FILTER_REGISTRATION_NAME.length()) {
            return name.substring(0, name.length() - FILTER_REGISTRATION_NAME.length());
        }
        return name;
    };

    public abstract Filter getFilter();

    public abstract List<String> getUrlPatterns();

}

