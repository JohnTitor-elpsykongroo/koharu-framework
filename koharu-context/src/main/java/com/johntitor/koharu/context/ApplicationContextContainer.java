package com.johntitor.koharu.context;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

public class ApplicationContextContainer {

    private static ApplicationContext applicationContext = null;

    @Nullable
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Nonnull
    public static ApplicationContext getRequiredApplicationContext() {
        return Objects.requireNonNull(getApplicationContext(),"ApplicationContext is not set");
    }

    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

}
