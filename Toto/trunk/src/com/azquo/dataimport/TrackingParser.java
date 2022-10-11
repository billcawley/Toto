package com.azquo.dataimport;

import com.azquo.admin.StandardDAO;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * Changed October 2022 due to new tomp messages table . . . .
 *
 */
public class TrackingParser {

    public static final String MESSAGEID = "MessageID";
    public static final String STATUSID = "StatusID";
    public static final String CONFIGID = "ConfigID";
    public static final String PROCESSDATE = "ProcessDate";
    public static final String INTERCHANGEID = "InterchangeID";
    public static final String MESSAGEDESCRIPTION = "MessageDescription";
    public static final String MESSAGEBODY = "MessageBody";
    public static final String MESSAGENUMBER = "MessageNumber";
    public static final String PARENTMESSAGEID = "ParentMessageID";
    public static final String TRANSMITDATE = "TransmitDate";
    public static final String SEQUENCENUMBER = "SequenceNumber";
    public static final String UMR = "UMR";
    public static final String UCR = "UCR";
    public static final String TR = "TR";
    public static final String CLASSID = "ClassID";
    public static final String SENDUSERID = "SendUserId";


    private static final PairRowMapper userRowMapper = new PairRowMapper();

    static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    // I know the Map is a bit hacky but it fine for this more simple use. See little help in rolling another object
    private static final class PairRowMapper implements RowMapper<Map<String, String>> {
        @Override
        public Map<String, String> mapRow(final ResultSet rs, final int row) {
            try {
                Map<String, String> toReturn = new HashMap<>();
                toReturn.put(MESSAGEID, rs.getString(MESSAGEID));
                toReturn.put(STATUSID, rs.getString(STATUSID));
                toReturn.put(CONFIGID, rs.getString(CONFIGID));
                if (rs.getTimestamp(PROCESSDATE) != null){
                    toReturn.put(PROCESSDATE, df.format(new Date(rs.getTimestamp(PROCESSDATE).getTime())));
                }
                toReturn.put(INTERCHANGEID, rs.getString(INTERCHANGEID));
                toReturn.put(MESSAGEDESCRIPTION, rs.getString(MESSAGEDESCRIPTION));
                toReturn.put(MESSAGEBODY, rs.getString(MESSAGEBODY));
                toReturn.put(MESSAGENUMBER, rs.getString(MESSAGENUMBER));
                toReturn.put(PARENTMESSAGEID, rs.getString(PARENTMESSAGEID));
                if (rs.getDate(TRANSMITDATE) != null){
                    toReturn.put(TRANSMITDATE, df.format(new Date(rs.getDate(TRANSMITDATE).getTime())));
                }
                toReturn.put(SEQUENCENUMBER, rs.getString(SEQUENCENUMBER));
                toReturn.put(UMR, rs.getString(UMR));
                toReturn.put(UCR, rs.getString(UCR));
                toReturn.put(TR, rs.getString(TR));
                toReturn.put(CLASSID, rs.getString(CLASSID));
                toReturn.put(SENDUSERID, rs.getString(SENDUSERID));
                return toReturn;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    // ok the tracking db is going to be big. Unless I start zapping what's in there I need to select where TRACKMESSKEY > something or it will be selecting 2 million records
    public static List<Map<String, String>> findGreaterThan(int messageId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(MESSAGEID, messageId);
        final String SQL_SELECT = "Select `" + SpreadsheetService.getTrackingDb() + "`.`" + SpreadsheetService.getTrackingTable() + "`.* from `" + SpreadsheetService.getTrackingDb() + "`.`" + SpreadsheetService.getTrackingTable() + "` where " + MESSAGEID + " >  :" + MESSAGEID;
        return StandardDAO.getJdbcTemplate().query(SQL_SELECT, namedParams, userRowMapper);
    }

}