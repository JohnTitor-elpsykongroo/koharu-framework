package com.johntitor.koharu.utils;

import com.johntitor.koharu.annotation.Component;
import com.johntitor.koharu.annotation.Configuration;
import com.johntitor.koharu.annotation.Order;
import com.johntitor.koharu.exception.BeanDefinitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AnnoUtilsTest {

    @Test
    public void noComponent() throws Exception {
        assertNull(ClassUtils.findAnnotation(Simple.class, Component.class));
    }


    @Test
    public void duplicateComponent() throws Exception {
        assertThrows(BeanDefinitionException.class, () -> {
            ClassUtils.findAnnotation(DuplicateComponent.class, Component.class);
        });
        assertThrows(BeanDefinitionException.class, () -> {
            ClassUtils.findAnnotation(DuplicateComponent2.class, Component.class);
        });
    }
}

@Order(1)
class Simple {
}

@Component
class SimpleComponent {
}

@Component("simpleName")
class SimpleComponentWithName {
}

@Configuration
class SimpleConfiguration {

}

@Configuration("simpleCfg")
class SimpleConfigurationWithName {

}

@CustomComponent
class Custom {

}

@CustomComponent("customName")
class CustomWithName {

}

@Component
@Configuration
class DuplicateComponent {

}

@CustomComponent
@Configuration
class DuplicateComponent2 {

}
