package com.azquo;

/*
EFC 15/02/22

We want to be able to pull data from the likes of Snowflake (or wherever) directly into Azquo

for the moment I'll just use this class

 */

import com.azquo.admin.AdminService;
import com.azquo.admin.onlinereport.ExternalDatabaseConnection;
import com.azquo.spreadsheet.LoggedInUser;

import java.rmi.RemoteException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class ExternalConnector {


    public static List<List<String>> getData(LoggedInUser loggedInUser, int connectionId, String query, Map<String, String> valueMap, String keyField) throws RemoteException {
        List<List<String>> toReturn = new ArrayList<>();
        // intial test code hard coded to snowflake
        Connection con = null;
        try {
            ExternalDatabaseConnection externalDatabaseConnection = AdminService.getExternalDatabaseConnectionById(connectionId, loggedInUser);
            if (externalDatabaseConnection != null) {
                if (externalDatabaseConnection.getConnectionString().startsWith("jdbc:snowflake")) {
                    Class.forName("com.snowflake.client.jdbc.SnowflakeDriver");
                    con = DriverManager.getConnection(externalDatabaseConnection.getConnectionString(), externalDatabaseConnection.getUser(), externalDatabaseConnection.getPassword());

                    if (valueMap != null || keyField!=null) {
                        save(externalDatabaseConnection, con, query, valueMap, keyField);
                        return null;
                    }
                    //con = StandardDAO.sfDataSource.getConnection();
                    Statement stat = con.createStatement();
                    if (externalDatabaseConnection.getDatabase() != null && !externalDatabaseConnection.getDatabase().isEmpty()) {
                        stat.executeQuery("use database " + externalDatabaseConnection.getDatabase());
                    }
                    ResultSet res = stat.executeQuery(query);
                    ResultSetMetaData rsmd = res.getMetaData();
                    int columnCount = rsmd.getColumnCount();
                    List<String> headings = new ArrayList<>();
                    // The column count starts from 1
                    for (int i = 1; i <= columnCount; i++) {
                        headings.add(rsmd.getColumnName(i));
                    }
                    toReturn.add(headings);
                    while (res.next()) {
                        List<String> line = new ArrayList<>();
                        for (String heading : headings) {
                            try {
                                line.add(res.getObject(heading).toString());
                            }catch(Exception e){
                                line.add("");
                            }
                        }
                        toReturn.add(line);
                    }
                    con.close();

                }

            }
        } catch (SQLException | ClassNotFoundException throwables) {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            throwables.printStackTrace();
            throw new RemoteException(throwables.getMessage());
        }
        return toReturn;
    }


    public static void save(ExternalDatabaseConnection externalDatabaseConnection, Connection con, String query, Map<String, String> valueMap, String keyField) throws SQLException {
        List<String> params = new ArrayList<>();
        // NOTE THIS IS UNSAFE - I can't work out how to use prepared statements if they need to be preceded by 'USE DATABASE'

        if (!query.toLowerCase(Locale.ROOT).startsWith("update ")) {
            //UPDATE holds the file name if it is not a SQL instruction
            StringBuffer newQuery = new StringBuffer();
            if (valueMap.size()==1){
                newQuery.append("delete from " + query);
                newQuery.append(" where \"" + keyField + "\" = " + valueMap.get(keyField));
            }else if (keyField == null){  // inserting
               // insert <filename> -> insert into <filename> a=1,b=2,etc
                newQuery.append("insert into ");
                newQuery.append(query);
                newQuery.append(" (");
                int i=0;
                for (String fieldName : valueMap.keySet()) {
                    newQuery.append("\"" + fieldName + "\"");
                    if (++i < valueMap.size()) {
                        newQuery.append(",");
                    }
                }
                newQuery.append(") VALUES (");
                i= 0;
                for (String fieldName : valueMap.keySet()) {
                    newQuery.append(valueMap.get(fieldName));
                    if (++i < valueMap.size()) {
                        newQuery.append(",");
                    }
                }
                newQuery.append(")");
            }else {
                //update all fields
                newQuery.append("update ");
                newQuery.append(query);

                newQuery.append(" set ");
                int i = 0;
                for (String fieldName : valueMap.keySet()) {
                    newQuery.append("\"" + fieldName + "\"");
                    newQuery.append(" = ");
                    newQuery.append(valueMap.get(fieldName));
                    if (++i < valueMap.size()) {
                        newQuery.append(",");
                    } else {
                        newQuery.append(" WHERE ");
                        newQuery.append("\"" + keyField + "\"");
                        newQuery.append(" = ");
                        newQuery.append(valueMap.get(keyField));
                    }
                }
            }
            query = newQuery.toString();
        }else{
            int cursorPos = 0;
            while (query.substring(cursorPos).contains(":")) {
                int colonPos = query.indexOf(":", cursorPos);
                int endField = query.indexOf(" ", colonPos + 2);
                if (endField < 0) {
                    endField = query.length();
                }
                String fieldName = query.substring(colonPos + 1, endField).trim();
                String fieldValue = valueMap.get(fieldName);
                if (fieldValue == null) {
                    throw new SQLException("no field named: " + fieldName);
                }
                params.add(fieldValue);
                query = query.substring(0, colonPos) + fieldValue + query.substring(endField);
                cursorPos = colonPos + fieldValue.length();
            }
        }
        Statement stat = con.createStatement();
        ResultSet res = stat.executeQuery(query);
    }
}
