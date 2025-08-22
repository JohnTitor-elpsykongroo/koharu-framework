package com.johntitor.koharu.jdbc.mapper;

import jakarta.annotation.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface RowMapper<T> {

    @Nullable
    T mapRow(ResultSet rs, int rowNum) throws SQLException;

}
