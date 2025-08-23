package com.johntitor.koharu.jdbc.tx;

import com.johntitor.koharu.annotation.Transactional;
import com.johntitor.koharu.aop.AnnotationProxyBeanPostProcessor;

public class TransactionalBeanPostProcessor extends AnnotationProxyBeanPostProcessor<Transactional> {

}
