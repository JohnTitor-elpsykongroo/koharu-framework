package com.johntitor.koharu.jdbc.tx;

import jakarta.annotation.Nullable;

import java.sql.Connection;

public class TransactionalContainer {
    @Nullable
    public static Connection getCurrentConnection() {
        TransactionStatus ts = DataSourceTransactionManager.getTransactionStatus();
        return ts == null ? null : ts.getConnection();
    }
}

