package com.azquo.memorydb.dao;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd.
 *
 * Created by edward on 05/11/15.
 *
 * Change calls to JDBC template to this, it will manage retrying and other stuff possibly in future
 * Same method signatures to NamedParameterJdbcTemplate as it proxies through
 *
 * 26/07/2016
 *
 * Intellij is complaining at me regarding auto wired classes and I've decided that dao and service instances are adding
 * little as we don't unit test or use dependency injection hence I want to go back to static daos and services a la Feefo v1.
 *
 * I suppose access to jdbcTemplate might go wrong but we'll be able to tell very quickly! We still have the ability to use
 * different sql databases via the spring config, if we need the option to configure e.g. hBase then I'll need to work out
 * an interface, maybe AzquoTransport, and that can be configured in spring before assigned to a static field.
 *
 */
public class JdbcTemplateUtils {
    private static NamedParameterJdbcTemplate jdbcTemplate;

    // after one instantiation static calls should be ready to go, yes I know it's dirty.
    public JdbcTemplateUtils(NamedParameterJdbcTemplate jdbcTemplate) throws Exception {
        // moved from spreadsheet service as it won't be instantiated any more, just logging
        String thost = ""; // Currently just to put in the log
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // Not exactly sure what it might be
        }
        System.out.println("host : " + thost);
        System.out.println("Setting JVM locale to UK");
        Locale.setDefault(Locale.UK);
        Locale currentLocale = Locale.getDefault();
        System.out.println(currentLocale.getDisplayLanguage());
        System.out.println(currentLocale.getDisplayCountry());
        System.out.println(currentLocale.getLanguage()); // still as US sometimes but the date issue is fixed?
        System.out.println(currentLocale.getCountry());

        System.out.println(System.getProperty("user.country"));
        System.out.println(System.getProperty("user.language"));
        if (jdbcTemplate == null){
            throw new Exception("Ack!, null data source passed to JdbcTemplateUtils, Azquo won't be able to access transport!");
        }
        JdbcTemplateUtils.jdbcTemplate = jdbcTemplate; // I realise that this is "naughty", see comments at the top.
    }

    public static int update(String sql, MapSqlParameterSource namedParams){
        try{
            return jdbcTemplate.update(sql, namedParams);
        } catch (DataAccessException e){
            e.printStackTrace();
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.update(sql, namedParams); // if it fails again then fail and throw the exception
        }
    }

    public static int update(String sql, Map<String, ?> namedParams){
        try{
            return jdbcTemplate.update(sql, namedParams);
        } catch (DataAccessException e){
            e.printStackTrace();
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.update(sql, namedParams); // if it fails again then fail and throw the exception
        }
    }

    static <T> List<T> query(String sql, RowMapper<T> rowMapper){
        try{
            return jdbcTemplate.query(sql, rowMapper);
        } catch (DataAccessException e){
            e.printStackTrace();
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.query(sql, rowMapper);
        }
    }

    // added for value history
    static <T> List<T> query(String sql, MapSqlParameterSource paramMap, RowMapper<T> rowMapper){
        try{
            return jdbcTemplate.query(sql, paramMap, rowMapper);
        } catch (DataAccessException e){
            e.printStackTrace();
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.query(sql, paramMap, rowMapper);
        }
    }

    static <T> T queryForObject(String sql, Map<String, ?> paramMap, Class<T> requiredType) {
        try{
            return jdbcTemplate.queryForObject(sql,paramMap,requiredType);
        } catch (DataAccessException e){
            e.printStackTrace();
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.queryForObject(sql, paramMap, requiredType);
        }
    }

    static int queryCount(String sql, MapSqlParameterSource namedParams){
        return jdbcTemplate.queryForList(sql, namedParams).size();
    }
}