package com.johntitor.koharu.web;

import freemarker.cache.TemplateLoader;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Objects;

public class ServletTemplateLoader implements TemplateLoader {

    // 日志记录器
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // Servlet 上下文，用于访问 web 应用中的资源
    private final ServletContext servletContext;
    // 模板所在子目录路径，例如 "/WEB-INF/templates/"
    private final String subDirPath;

    /**
     * 构造方法，初始化 ServletContext 和模板目录路径
     * @param servletContext Servlet 上下文
     * @param subDirPath 模板所在子目录路径
     */
    public ServletTemplateLoader(ServletContext servletContext, String subDirPath) {
        // 确保参数不为 null
        Objects.requireNonNull(servletContext);
        Objects.requireNonNull(subDirPath);

        // 将路径分隔符统一为 '/'（兼容 Windows）
        subDirPath = subDirPath.replace('\\', '/');
        // 确保路径以 '/' 结尾
        if (!subDirPath.endsWith("/")) {
            subDirPath += "/";
        }
        // 确保路径以 '/' 开头
        if (!subDirPath.startsWith("/")) {
            subDirPath = "/" + subDirPath;
        }
        this.subDirPath = subDirPath;
        this.servletContext = servletContext;
    }

    /**
     * 查找模板文件
     * @param name 模板文件名
     * @return File 对象或者 null（找不到模板）
     */
    @Override
    public Object findTemplateSource(String name) throws IOException {
        // 拼接完整路径
        String fullPath = subDirPath + name;

        try {
            // 获取文件在服务器上的真实路径
            String realPath = servletContext.getRealPath(fullPath);
            logger.atDebug().log("load template {}: real path: {}", name, realPath);

            if (realPath != null) {
                File file = new File(realPath);
                // 判断文件是否可读且是普通文件
                if (file.canRead() && file.isFile()) {
                    return file;
                }
            }
        } catch (SecurityException e) {
            // 安全异常忽略（可能无法访问文件系统）
        }
        // 找不到模板
        return null;
    }

    /**
     * 获取模板的最后修改时间
     * @param templateSource 模板对象
     * @return 最后修改时间（毫秒）
     */
    @Override
    public long getLastModified(Object templateSource) {
        if (templateSource instanceof File) {
            return ((File) templateSource).lastModified();
        }
        // 非文件返回 0
        return 0;
    }

    /**
     * 获取模板的 Reader，用于 FreeMarker 渲染
     * @param templateSource 模板对象
     * @param encoding 模板编码
     * @return Reader 对象
     * @throws IOException 找不到模板时抛出
     */
    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        if (templateSource instanceof File) {
            return new InputStreamReader(new FileInputStream((File) templateSource), encoding);
        }
        throw new IOException("File not found.");
    }

    /**
     * 关闭模板资源（这里不需要关闭额外资源，所以空实现）
     */
    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
        // no-op
    }

    /**
     * URL 连接是否使用缓存（禁用缓存）
     */
    public Boolean getURLConnectionUsesCaches() {
        return Boolean.FALSE;
    }
}

