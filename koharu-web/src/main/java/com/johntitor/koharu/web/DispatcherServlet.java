package com.johntitor.koharu.web;

import com.johntitor.koharu.context.ApplicationContext;
import com.johntitor.koharu.io.PropertyResolver;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;

public class DispatcherServlet extends HttpServlet {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final ApplicationContext applicationContext;

    public DispatcherServlet(final ApplicationContext applicationContext, PropertyResolver propertyResolver) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        logger.info("{} {}", req.getMethod(), req.getRequestURI());
        PrintWriter pw = resp.getWriter();
        pw.write("<h1>Hello, world!</h1>");
        pw.flush();
    }

    @Override
    public void destroy() {
        this.applicationContext.close();
    }


}
