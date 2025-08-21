package com.johntitor.koharu.aop.around;

import com.johntitor.koharu.context.AnnotationConfigApplicationContext;
import com.johntitor.koharu.io.PropertyResolver;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class AroundProxyTest {

    @Test
    public void testAroundProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(AroundApplication.class,createPropertyResolver())){
            OriginBean proxy = ctx.getBean(OriginBean.class);

            System.out.println(proxy.getClass().getName());

            assertNotSame(OriginBean.class, proxy.getClass());

            assertNull(proxy.name);

            assertEquals("Hello, Bob!", proxy.hello());

            // test injected proxy:
            OtherBean other = ctx.getBean(OtherBean.class);
            assertSame(proxy, other.origin);
            assertEquals("Hello, Bob!", other.origin.hello());
        }
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        ps.put("customer.name", "Bob");
        var pr = new PropertyResolver(ps);
        return pr;
    }
}
