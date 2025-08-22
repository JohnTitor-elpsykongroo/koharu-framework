package com.johntitor.koharu.jdbc.mapper.impl;

import com.johntitor.koharu.exception.DataAccessException;
import com.johntitor.koharu.jdbc.mapper.RowMapper;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BeanRowMapper<T> implements RowMapper<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private Class<T> clazz;
    private Constructor<T> constructor;
    private Map<String, Field> fields = new HashMap<>();
    private Map<String, Method> methods = new HashMap<>();


    public BeanRowMapper(Class<T> clazz) {
        this.clazz = clazz;

        // 要求 Bean 必须有 public 无参构造方法
        try {
            this.constructor = clazz.getConstructor();
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException(String.format("No public default constructor found for class %s when build BeanRowMapper.", clazz.getName()), e);
        }

        // 收集可用的字段
        for (Field f : clazz.getFields()) {
            String name = f.getName();
            this.fields.put(name, f);
            logger.debug("Add row mapping: {} to field {}", name, name);
        }

        // 收集可用的setter方法
        for (Method m : clazz.getMethods()) {
            Parameter[] ps = m.getParameters();
            if (ps.length != 1){
                continue;
            }
            String name = m.getName();
            if (name.length() >= 4 && name.startsWith("set")) {
                String prop = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                this.methods.put(prop, m);
                logger.debug("Add row mapping: {} to {}({})", prop, name, ps[0].getType().getSimpleName());
            }
        }
    }

    @Nullable
    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        T bean;
        try {
            bean = this.constructor.newInstance();
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            for (int i = 1; i <= columns; i++) {
                String label = meta.getColumnLabel(i); // 拿到列名
                Method method = this.methods.get(label);
                if (method != null) {
                    method.invoke(bean, rs.getObject(label)); // 优先走 setter 方法
                } else {
                    Field field = this.fields.get(label);
                    if (field != null) {
                        field.set(bean, rs.getObject(label)); // 如果没有 setter，就直接写字段
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException(String.format("Could not map result set to class %s", this.clazz.getName()), e);
        }
        return bean;
    }
}
