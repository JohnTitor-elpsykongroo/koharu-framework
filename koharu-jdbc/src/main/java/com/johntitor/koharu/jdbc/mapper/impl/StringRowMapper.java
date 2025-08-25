package com.johntitor.koharu.jdbc.mapper.impl;

import com.johntitor.koharu.jdbc.mapper.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StringRowMapper implements RowMapper<String> {

    public static StringRowMapper instance = new StringRowMapper();

    private StringRowMapper() {}

    @Override
    public String mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getString(1);
    }
}
