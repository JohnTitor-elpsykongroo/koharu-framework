package com.johntitor.koharu.io;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class ResourceResolver {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private String basePackage;

    public ResourceResolver(String basePackage) {
        this.basePackage = basePackage;
    }

    public <R> List<R> scan(Function<Resource, R> mapper) {
        String basePackagePath = this.basePackage.replace('.', '/');
        try {
            List<R> collector = new ArrayList<>();
            scan(basePackagePath,collector,mapper);
            return collector;
        }catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private <R> void scan(String basePackagePath, List<R> collector, Function<Resource,R> mapper) throws IOException, URISyntaxException {
        logger.debug("Scanning resources from {}", basePackagePath);
        Enumeration<URL> en = getContextClassLoader().getResources(basePackagePath);
        while (en.hasMoreElements()) {
            URL url = en.nextElement();
            URI uri = url.toURI();
            String fullPath  =removeTrailingSlash(uriToString(uri));

            String baseDir  = fullPath.substring(0, fullPath.length() - basePackagePath.length());
            baseDir = removeTrailingSlash(baseDir);
            if (baseDir.startsWith("file:")) {
                baseDir = baseDir.substring(5);
            }

            boolean isJarResource = fullPath.startsWith("jar:");
            Path scanTarget = isJarResource ? jarUriToPath(basePackagePath, uri) : Paths.get(uri);

            scanFile(isJarResource, baseDir, scanTarget, collector, mapper);
        }
    }

    <R> void scanFile(boolean isJarResource, String baseDir, Path scanTarget, List<R> collector, Function<Resource, R> mapper) throws IOException{
        Files.walk(scanTarget).filter(Files::isRegularFile).forEach(file->{
            Resource res = buildResource(isJarResource,baseDir,file);
            logger.debug("Found resource {}", res);
            R r = mapper.apply(res);
            if (r != null) {
                collector.add(r);
            }
        });
    }

    private Resource buildResource(boolean isJarResource, String baseDir, Path file) {
        if (isJarResource) {
            String name = removeLeadingSlash(file.toString());
            String path = baseDir + name;
            return new Resource(path, name);
        } else {
            String path = file.toString();
            String name = removeLeadingSlash(path.substring(baseDir.length()));
            return new Resource("file:" + path, name);
        }
    }



    ClassLoader getContextClassLoader(){
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null){
            cl = getClass().getClassLoader();
        }
        return cl;
    }

    String uriToString(URI uri) {
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
    }

    Path jarUriToPath(String basePackagePath, URI jarUri) throws IOException {
        return FileSystems.newFileSystem(jarUri, Map.of()).getPath(basePackagePath);
    }

    private String removeTrailingSlash(String s) {
        if (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String removeLeadingSlash(String s) {
        if (s.startsWith("/") || s.startsWith("\\")) {
            s = s.substring(1);
        }
        return s;
    }

}
