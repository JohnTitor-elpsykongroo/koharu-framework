package com.johntitor.koharu.web;

import com.johntitor.koharu.annotation.Controller;
import com.johntitor.koharu.annotation.GetMapping;
import com.johntitor.koharu.annotation.PostMapping;
import com.johntitor.koharu.annotation.RestController;
import com.johntitor.koharu.context.ApplicationContext;
import com.johntitor.koharu.context.ConfigurableApplicationContext;
import com.johntitor.koharu.exception.ErrorResponseException;
import com.johntitor.koharu.exception.NestedRuntimeException;
import com.johntitor.koharu.io.PropertyResolver;
import com.johntitor.koharu.web.model.Dispatcher;
import com.johntitor.koharu.web.model.Result;
import com.johntitor.koharu.web.utils.JsonUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class DispatcherServlet extends HttpServlet {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final ApplicationContext applicationContext;
    private ViewResolver viewResolver;

    private String resourcePath;
    private String faviconPath;

    List<Dispatcher> getDispatchers = new ArrayList<>();
    List<Dispatcher> postDispatchers = new ArrayList<>();

    public DispatcherServlet(final ApplicationContext applicationContext, PropertyResolver propertyResolver) {
        this.applicationContext = applicationContext;
        this.viewResolver = applicationContext.getBean(ViewResolver.class);
        this.resourcePath = propertyResolver.getProperty("${koharu.web.static-path:/static/}");
        this.faviconPath = propertyResolver.getProperty("${koharu.web.favicon-path:/favicon.ico}");
        if (!this.resourcePath.endsWith("/")) {
            this.resourcePath = this.resourcePath + "/";
        }
    }

    @Override
    public void init() throws ServletException {
        logger.info("init {}.", getClass().getName());
        // 扫描 @Controller and @RestController:
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) applicationContext;
        for (var def : ctx.findBeanDefinitions(Object.class)) {
            Class<?> beanClass = def.getBeanClass();
            Controller controller = beanClass.getAnnotation(Controller.class);
            RestController restController = beanClass.getAnnotation(RestController.class);
            if (controller != null && restController != null) {
                throw new ServletException("Found @Controller and @RestController on class: " + beanClass.getName());
            }
            if (controller != null) {
                addController(false, def.getName(), def.getRequiredInstance());
            }
            if (restController != null) {
                addController(true, def.getName(), def.getRequiredInstance());
            }
        }
    }

    @Override
    public void destroy() {
        this.applicationContext.close();
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String url = req.getRequestURI();
        // 判断请求是否是 favicon（网站图标）或静态资源（如 CSS、JS、图片等）
        if (url.equals(this.faviconPath) || url.startsWith(this.resourcePath)) {
            doResource(url, req, resp);
        } else {
            // 否则是动态请求，调用 doService 方法处理
            doService(req, resp, this.getDispatchers);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doService(req, resp, this.postDispatchers);
    }

    private void addController(boolean isRest, String beanName, Object beanInstance) throws ServletException {
        logger.info("add {} controller '{}': {}", isRest ? "REST" : "MVC", beanName, beanInstance.getClass().getName());
        addMethods(isRest, beanName, beanInstance, beanInstance.getClass());
    }

    private void addMethods(boolean isRest, String beanName, Object beanInstance, Class<?> type) throws ServletException {
        for (Method m : type.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null) {
                checkMethod(m);
                this.getDispatchers.add(new Dispatcher("GET", isRest, beanInstance, m, get.value()));
            }
            PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) {
                checkMethod(m);
                this.postDispatchers.add(new Dispatcher("POST", isRest, beanInstance, m, post.value()));
            }
        }
        Class<?> superClass = type.getSuperclass();
        if (superClass != null) {
            addMethods(isRest, beanName, beanInstance, superClass);
        }
    }

    private void checkMethod(Method m) throws ServletException {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new ServletException("Cannot do URL mapping to static method: " + m);
        }
        m.setAccessible(true);
    }

    private void doResource(String url, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 获取 ServletContext，用于访问 Web 应用中的资源
        ServletContext ctx = req.getServletContext();

        // 根据 URL 从 webapp 根目录读取资源
        try (InputStream input = ctx.getResourceAsStream(url)) {
            // 如果资源不存在，返回 404 错误
            if (input == null) {
                resp.sendError(404, "Not Found");
            } else {
                // 取出 URL 中的文件名部分，用于猜测 MIME 类型
                String file = url;
                int n = url.lastIndexOf('/');
                if (n >= 0) {
                    file = url.substring(n + 1);// 去掉路径，只保留文件名
                }

                // 根据文件名获取 MIME 类型（例如 "text/css"、"image/png"）
                String mime = ctx.getMimeType(file);
                // 如果无法识别类型，默认使用二进制流
                if (mime == null) {
                    mime = "application/octet-stream";
                }

                // 设置响应的 Content-Type
                resp.setContentType(mime);

                // 获取响应输出流
                ServletOutputStream output = resp.getOutputStream();
                // 将资源内容写入响应输出流
                input.transferTo(output);
                // 刷新输出流，确保数据发送到客户端
                output.flush();
            }
        }
    }

    private void doService(HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws ServletException, IOException {
        String url = req.getRequestURI();
        try {
            doService(url, req, resp, dispatchers);
        } catch (ErrorResponseException e) {
            logger.warn("process request failed with status " + e.statusCode + " : " + url, e);
            if (!resp.isCommitted()) {
                resp.resetBuffer();
                resp.sendError(e.statusCode);
            }
        } catch (RuntimeException | ServletException | IOException e) {
            logger.warn("process request failed: " + url, e);
            throw e;
        } catch (Exception e) {
            logger.warn("process request failed: " + url, e);
            throw new NestedRuntimeException(e);
        }
    }

    private void doService(String url, HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws Exception {
        for (Dispatcher dispatcher : dispatchers) {
            Result result = dispatcher.process(url, req, resp);
            if (!result.processed()) {
                continue;
            }
            Object r = result.returnObject();
            if (dispatcher.isRest()) {
                handleRestResult(url, resp, dispatcher, r);
            } else {
                // process MVC:
                handleMvcResult(url, req, resp, dispatcher, r);
            }
            return;
        }
        // not found:
        resp.sendError(404, "Not Found");
    }

    private void handleRestResult(String url, HttpServletResponse resp, Dispatcher dispatcher, Object resultObj) throws IOException, ServletException {
        // send rest response:
        if (!resp.isCommitted()) {
            resp.setContentType("application/json");
        }
        if (dispatcher.isResponseBody()) {
            if (resultObj instanceof String s) {
                // send as response body:
                PrintWriter pw = resp.getWriter();
                pw.write(s);
                pw.flush();
            } else if (resultObj instanceof byte[] data) {
                // send as response body:
                ServletOutputStream output = resp.getOutputStream();
                output.write(data);
                output.flush();
            } else {
                // error:
                throw new ServletException("Unable to process REST result when handle url: " + url);
            }
        } else if (!dispatcher.isVoid()) {
            PrintWriter pw = resp.getWriter();
            JsonUtils.writeJson(pw, resultObj);
            pw.flush();
        }
    }

    private void handleMvcResult(String url, HttpServletRequest req, HttpServletResponse resp, Dispatcher dispatcher, Object resultObj) throws Exception {
        if (!resp.isCommitted()) {
            resp.setContentType("text/html");
        }
        if (resultObj instanceof String s) {
            handleMvcString(url, resp, dispatcher, s);
        } else if (resultObj instanceof byte[] data) {
            handleMvcBytes(url, resp, dispatcher, data);
        } else if (resultObj instanceof ModelAndView mv) {
            handleModelAndView(req, resp, mv);
        } else if (!dispatcher.isVoid() && resultObj != null) {
            // error:
            throw new ServletException("Unable to process " + resultObj.getClass().getName() + " result when handle url: " + url);
        }
    }

    private void handleMvcString(String url, HttpServletResponse resp, Dispatcher dispatcher, String s) throws IOException, ServletException {
        if (dispatcher.isResponseBody()) {
            try (PrintWriter pw = resp.getWriter()) {
                pw.write(s);
            }
        } else if (s.startsWith("redirect:")) {
            resp.sendRedirect(s.substring(9));
        } else {
            throw new ServletException("Unable to process String result when handle url: " + url);
        }
    }

    private void handleMvcBytes(String url, HttpServletResponse resp, Dispatcher dispatcher, byte[] data) throws IOException, ServletException {
        if (dispatcher.isResponseBody()) {
            // send as response body:
            ServletOutputStream output = resp.getOutputStream();
            output.write(data);
            output.flush();
        } else {
            // error:
            throw new ServletException("Unable to process byte[] result when handle url: " + url);
        }

    }

    private void handleModelAndView(HttpServletRequest req, HttpServletResponse resp, ModelAndView mv) throws Exception {
        String view = mv.getViewName();
        if (view.startsWith("redirect:")) {
            resp.sendRedirect(view.substring(9));
        } else {
            this.viewResolver.render(view, mv.getModel(), req, resp);
        }
    }


}
