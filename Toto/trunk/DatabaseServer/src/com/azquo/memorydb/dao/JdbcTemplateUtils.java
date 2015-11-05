package com.azquo.memorydb.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Created by edward on 05/11/15.
 *
 * Change calls to JDBC template to this, it will manage retrying and other stuff possibly in future
 * Same method signatures to NamedParameterJdbcTemplate as it proxies through
 */
public class JdbcTemplateUtils {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    public int update(String sql, MapSqlParameterSource namedParams){
        try{
            return jdbcTemplate.update(sql, namedParams);
        } catch (DataAccessException e){
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.update(sql, namedParams); // if it fails again then fail and throw the exception
        }
    }

    public int update(String sql, Map<String, ?> namedParams){
        try{
            return jdbcTemplate.update(sql, namedParams);
        } catch (DataAccessException e){
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.update(sql, namedParams); // if it fails again then fail and throw the exception
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper){
        try{
            return jdbcTemplate.query(sql, rowMapper);
        } catch (DataAccessException e){
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.query(sql, rowMapper);
        }
    }

    public <T> T queryForObject(String sql, Map<String, ?> paramMap, Class<T> requiredType) {
        try{
            return jdbcTemplate.queryForObject(sql,paramMap,requiredType);
        } catch (DataAccessException e){
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.queryForObject(sql, paramMap, requiredType);
        }
    }

    public List<Map<String, Object>> queryForList(String sql, Map<String, ?> paramMap) {
        try{
            return jdbcTemplate.queryForList(sql,paramMap);
        } catch (DataAccessException e){
            System.out.println("JDBC Error on " + sql);
            System.out.println("\ntrying again");
            return jdbcTemplate.queryForList(sql,paramMap);
        }
    }
}
