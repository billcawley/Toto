package com.azquo.admin.database;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2018 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 04/02/2018
 *
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `business_id` int(11) NOT NULL,
 `identifier` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 `team` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 `text` text COLLATE utf8_unicode_ci DEFAULT NULL,
 PRIMARY KEY (`id`)
 *
 *
 */
public final class CommentDAO {

    private static final String TABLENAME = "comment";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String IDENTIFIER = "identifier";
    private static final String TEAM = "team";
    private static final String TEXT = "text";

    public static Map<String, Object> getColumnNameValueMap(final Comment comment) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, comment.getId());
        toReturn.put(BUSINESSID, comment.getBusinessId());
        toReturn.put(IDENTIFIER, comment.getIdentifier());
        toReturn.put(TEAM, comment.getTeam());
        toReturn.put(TEXT, comment.getText());
        return toReturn;
    }

    private static final class CommentRowMapper implements RowMapper<Comment> {
        @Override
        public Comment mapRow(final ResultSet rs, final int row) {
            // not pretty, just make it work for the moment
            try {
                return new Comment(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getString(IDENTIFIER)
                        , rs.getString(TEAM)
                        , rs.getString(TEXT)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final CommentRowMapper commentRowMapper = new CommentRowMapper();

    public static Comment findForBusinessIdAndIdentifierAndTeam(final int businessId, String identifier, String team) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(IDENTIFIER, identifier);
        namedParams.addValue(TEAM, team);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :" + BUSINESSID + " AND " + IDENTIFIER + " = :" + IDENTIFIER + " AND " + TEAM + " = :" + TEAM, TABLENAME, commentRowMapper, namedParams);
    }

    public static void removeById(Comment comment) {
        StandardDAO.removeById(comment, TABLENAME);
    }

    public static void store(Comment comment) {
        StandardDAO.store(comment, TABLENAME, getColumnNameValueMap(comment));
    }

}