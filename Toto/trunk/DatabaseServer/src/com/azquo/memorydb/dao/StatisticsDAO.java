package com.azquo.memorydb.dao;

/*

I want to store useful statistics. Initially this will be usage of heading clauses when importing but there will no doubt be other things

Going for timestamp, name, double, varchar 255. Key on timestamp and name I think.

Could this be shared

CREATE TABLE IF NOT EXISTS `statistics` (
  `ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `name` varchar(255) NOT NULL,
  `number` double NOT NULL DEFAULT '0',
  `value` varchar(255) DEFAULT NULL,UNIQUE KEY `ts` (`ts`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;


 */

import com.azquo.DateUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

public class StatisticsDAO {

    // db name - we need to do this as we don't want to introduce dependencies between the report and database servers. So here don't use master_db
    private static final String AZQUOSTATISTICS = "azquo_statistics";

    // table name
    private static final String STATISTICS = "statistics";

    private static final String ID = "id";
    private static final String TS = "ts";
    private static final String NAME = "name";
    private static final String NUMBER = "number";
    private static final String VALUE = "value";


    static {
        createDatabaseIfItDoesntExist();
        createTableIfItDoesntExist();
    }

    static void createDatabaseIfItDoesntExist() {
        JdbcTemplateUtils.update("CREATE database IF NOT EXISTS `" + AZQUOSTATISTICS + "`;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    static void createTableIfItDoesntExist() {
        JdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + AZQUOSTATISTICS + "`.`" + STATISTICS + "` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `ts` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `name` varchar(255) NOT NULL,\n" +
                "  `number` double NOT NULL DEFAULT '0',\n" +
                "  `value` varchar(255) DEFAULT '',\n" +
                "   PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=latin1;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    private static final class StatisticsRowMapper implements RowMapper<Statistics> {
        @Override
        public Statistics mapRow(final ResultSet rs, final int row) {
            try {
                return new Statistics(
                        rs.getInt(ID),
                        DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(TS))
                        , rs.getString(NAME)
                        , rs.getDouble(NUMBER)
                        ,rs.getString(VALUE));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static StatisticsRowMapper statisticsRowMapper = new StatisticsRowMapper();

    public static Statistics findMostRecentForName(final String name) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + AZQUOSTATISTICS + "`.`" + STATISTICS + "`.* from `" + AZQUOSTATISTICS + "`.`" + STATISTICS + "` where `" + NAME + "` = :" + NAME + "  order by `" + TS + "` DESC LIMIT 0,1";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NAME, name);
        List<Statistics> query = JdbcTemplateUtils.query(SQL_SELECT_ALL, namedParams, statisticsRowMapper);
        if (query.size() == 0) {
            return null;
        }
        return query.get(0);
    }

    public static void addToNumber(final String name, double toAdd) throws DataAccessException {
        Statistics statistics = findMostRecentForName(name);
        if (statistics != null){
            statistics.addToNumber(toAdd);
        } else {
            statistics = new Statistics(0, LocalDateTime.now(),name, toAdd, "");
        }
        storeStatistics(statistics);
    }

    public static void storeStatistics(Statistics statistics){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        if (statistics.getId() == 0){
            namedParams.addValue(TS, statistics.getTs());
            namedParams.addValue(NAME, statistics.getName());
            namedParams.addValue(NUMBER, statistics.getNumber());
            namedParams.addValue(VALUE, statistics.getValue());
            JdbcTemplateUtils.update("INSERT INTO `" + AZQUOSTATISTICS + "`.`" + STATISTICS + "` (" + TS + "," + NAME + "," + NUMBER + "," + VALUE + ") VALUES " + "(:" + TS + ",:" + NAME + ",:" + NUMBER + ",:" + VALUE + ") ", namedParams);
        } else {
            StringBuilder updateSql = new StringBuilder("UPDATE `" + AZQUOSTATISTICS + "`.`" + STATISTICS + "` set ");
            updateSql.append("`").append(TS).append("` = :").append(TS).append(", ");
            updateSql.append("`").append(NAME).append("` = :").append(NAME).append(", ");
            updateSql.append("`").append(NUMBER).append("` = :").append(NUMBER).append(", ");
            updateSql.append("`").append(VALUE).append("` = :").append(VALUE);
            namedParams.addValue(TS, statistics.getTs());
            namedParams.addValue(NAME, statistics.getName());
            namedParams.addValue(NUMBER, statistics.getNumber());
            namedParams.addValue(VALUE, statistics.getValue());
            updateSql.append(" where " + ID + " = :" + ID);
            namedParams.addValue(ID, statistics.getId());
            JdbcTemplateUtils.update(updateSql.toString(), namedParams);
        }
    }
}