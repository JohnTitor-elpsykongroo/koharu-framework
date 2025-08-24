package com.johntitor.koharu.web;

import com.johntitor.koharu.exception.ServerErrorException;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.*;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * 自定义 FreeMarker 视图解析器:
 *      用于将逻辑视图名称映射为 FreeMarker 模板并渲染输出 HTML
 * */
public class FreeMarkerViewResolver implements ViewResolver {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // 模板文件所在路径
    private final String templatePath;
    // 模板编码（例如 UTF-8）
    private final String templateEncoding;

    // 获取模板文件资源
    private final ServletContext servletContext;

    // FreeMarker 配置对象 Configuration
    private Configuration config;

    public FreeMarkerViewResolver(ServletContext servletContext, String templatePath, String templateEncoding) {
        this.servletContext = servletContext;
        this.templatePath = templatePath;
        this.templateEncoding = templateEncoding;
    }

    /**
     * 初始化 FreeMarker 配置
     */
    @Override
    public void init() {
        logger.info("init {}, set template path: {}", getClass().getSimpleName(), this.templatePath);

        // 创建 FreeMarker 配置对象，指定 FreeMarker 版本
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        // 设置输出格式为 HTML
        cfg.setOutputFormat(HTMLOutputFormat.INSTANCE);
        // 设置默认编码
        cfg.setDefaultEncoding(this.templateEncoding);
        // 设置模板加载器，从 ServletContext 下指定路径加载模板
        cfg.setTemplateLoader(new ServletTemplateLoader(this.servletContext, this.templatePath));
        // 模板异常处理方式，调试模式下显示详细 HTML
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
        // 开启自动转义策略（如果模板支持）
        cfg.setAutoEscapingPolicy(Configuration.ENABLE_IF_SUPPORTED_AUTO_ESCAPING_POLICY);
        // 禁用本地化查找
        cfg.setLocalizedLookup(false);

        // 配置对象包装器，用于在模板中访问模型对象
        var ow = new DefaultObjectWrapper(Configuration.VERSION_2_3_32);
        // 设置允许直接访问对象的字段（非 getter 方法）
        ow.setExposeFields(true);
        cfg.setObjectWrapper(ow);

        // 保存配置对象
        this.config = cfg;
    }

    /**
     * 渲染模板
     * @param viewName 模板名称，例如 "index.ftl"
     * @param model 模型数据，模板中可以通过 ${key} 访问
     * @param req HTTP 请求对象
     * @param resp HTTP 响应对象
     */
    @Override
    public void render(String viewName, Map<String, Object> model, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Template templ = null;
        try {
            // 获取模板对象
            templ = this.config.getTemplate(viewName);
        } catch (Exception e) {
            // 模板不存在，抛出 500 错误
            throw new ServerErrorException("View not found: " + viewName);
        }

        // 获取响应输出流
        PrintWriter pw = resp.getWriter();
        try {
            // 模板渲染，将 model 数据填充到模板并写入响应
            templ.process(model, pw);
        } catch (TemplateException e) {
            // 模板处理异常，抛出 500 错误
            throw new ServerErrorException(e);
        }

        // 刷新输出流，确保内容发送到客户端
        pw.flush();
    }
}

