package com.azquo;

/*
EFC 15/02/22

We want to be able to pull data from the likes of Snowflake (or wherever) directly into Azquo

for the moment I'll just use this class

 */

import com.azquo.admin.StandardDAO;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExternalConnector {

    public static List<List<String>> getData(String connectionName, String query) {
        List<List<String>> toReturn = new ArrayList<>();
        // intial test code hard coded to snowflake
        Connection con;
        try {
            con = StandardDAO.sfDataSource.getConnection();
            Statement stat = con.createStatement();
            ResultSet res = stat.executeQuery(query);
            ResultSetMetaData rsmd = res.getMetaData();
            int columnCount = rsmd.getColumnCount();
            List<String> headings = new ArrayList<>();
            // The column count starts from 1
            for (int i = 1; i <= columnCount; i++ ) {
                headings.add(rsmd.getColumnName(i));
            }
            toReturn.add(headings);
            while (res.next()){
                List<String> line = new ArrayList<>();
                for (String heading : headings){
                    line.add(res.getObject(heading).toString());
                }
                toReturn.add(line);
            }
            con.close();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return toReturn;
    }

}
