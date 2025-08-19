package com.johntitor.koharu.context;

import com.johntitor.koharu.io.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Map<String, BeanDefinition> beans = new HashMap<String, BeanDefinition>();



}
