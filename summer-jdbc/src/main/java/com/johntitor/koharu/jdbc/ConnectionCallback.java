package com.johntitor.koharu.jdbc;

import jakarta.annotation.Nullable;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 用于封装「拿到一个 JDBC Connection 之后要做的事」
 * */
@FunctionalInterface
public interface ConnectionCallback<T> {

    /**
     * 调用者在这个方法里实现 具体的数据库操作逻辑
     * */
    @Nullable
    T doInConnection(Connection con) throws SQLException;

}

