package com.extractagilecrm;

import com.agilecrm.api.APIManager;
import com.agilecrm.api.ContactAPI;
import com.google.gson.Gson;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.zkoss.poi.xssf.usermodel.XSSFRow;
import org.zkoss.poi.xssf.usermodel.XSSFSheet;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Edd yoinked from tinternet and modified
 */
public class ExtractContacts {

    public static void writeRow(XSSFSheet sheet, AtomicInteger row, int col, String name, Object o){
        if (o instanceof JSONArray){
            if (!name.isEmpty()){
                XSSFRow row1 = sheet.createRow(row.get());
                row1.createCell(col).setCellValue(name);
                row.incrementAndGet();
            }
            col++;
            JSONArray ja = (JSONArray)o;
            for (Object arrayEntry : ja){
                writeRow(sheet, row, col,"", arrayEntry);
            }
        } else if (o instanceof JSONObject){
            if (!name.isEmpty()){
                XSSFRow row1 = sheet.createRow(row.get());
                row1.createCell(col).setCellValue(name);
                row.incrementAndGet();
            }
            col++;
            JSONObject jo = (JSONObject)o;
            for (String key : jo.keySet()){
                writeRow(sheet, row, col, key, jo.get(key));
            }
        } else {
            String s = o.toString();
            if (NumberUtils.isNumber(s) && s.startsWith("16")){
                if (s.length() == 10){
                    LocalDateTime dateTime = LocalDateTime.ofEpochSecond(Long.parseLong(s), 0, ZoneOffset.UTC);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm", Locale.ENGLISH);
                    s = dateTime.format(formatter);
                }
                if (s.length() == 13){
                    Instant instance = java.time.Instant.ofEpochMilli(Long.parseLong(s));
                    LocalDateTime dateTime = LocalDateTime.ofInstant(instance, ZoneId.systemDefault());
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm", Locale.ENGLISH);
                    s = dateTime.format(formatter);
                }
            }
            XSSFRow row1 = sheet.createRow(row.get());
            row.incrementAndGet();
            if (!name.isEmpty()){
                row1.createCell(col).setCellValue(name);
                row1.createCell(col + 1).setCellValue(s);
            } else {
                row1.createCell(col).setCellValue(s);
            }
        }
    }

    public static void extractContacts(String url, String user, String password, String targetFile, int lastDays) {
        System.out.print("current time millis : " + System.currentTimeMillis());
        try {
            String cursor = "first_page";

            while (cursor != null){
                JSONArray contacts = getByFilter(100, cursor, lastDays, "PERSON",url,user,password);
                int tries = 0;
                while (contacts == null && tries < 5){
                    contacts = getByFilter(100, cursor, lastDays, "PERSON",url,user,password);
                    tries++;
                    System.out.println("tries : " + tries);
                }
                FileUtils.writeStringToFile(new File(targetFile + System.currentTimeMillis()), contacts.toString());
                cursor = null;
                for (Object contact : contacts){
                    JSONObject jcontact = (JSONObject) contact;
                    if (jcontact.has("cursor")){
                        cursor = jcontact.getString("cursor");
                    }
                }
                System.out.println("+++++++++++++++++++ cursor : " + cursor);
            }
        } catch (Exception e) {
            System.out.println("message" + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void extractDeals(String url, String user, String password, String targetFile) {
        System.out.print("current time millis : " + System.currentTimeMillis());
        int lastDays = 20;
        try {
            String cursor = "first_page";
            while (cursor != null){
                JSONArray deals = getDeals("100", cursor,url,user,password);
                int tries = 0;
                while (deals == null && tries < 5){
                    deals = getDeals("100", cursor,url,user,password);
                    tries++;
                    System.out.println("tries : " + tries);
                }
                FileUtils.writeStringToFile(new File(targetFile + System.currentTimeMillis()), deals.toString());
                cursor = null;
                for (Object deal : deals){
                    JSONObject jdeal = (JSONObject) deal;
                    if (jdeal.has("cursor")){
                        cursor = jdeal.getString("cursor");
                    }
                }
                System.out.println("+++++++++++++++++++ cursor : " + cursor);
            }

        } catch (Exception e) {
            System.out.println("message" + e.getMessage());
            e.printStackTrace();
        }
    }


    public static JSONArray getByFilter(int page_size, String cursor, int daysAgo, String type, String baseUrl, String userEmail, String restAPIKey)
            throws Exception {
        APIManager apiManager = new APIManager(baseUrl, userEmail, restAPIKey);
        System.out.println("Getting " + type + " by page and cursor-----------------------");
        // EFC commenting - we want a filter
/*
        params.add("page_size", page_size);
        params.add("global_sort_key", "-created_time");

        if (!cursor.isEmpty() && !cursor.equalsIgnoreCase("first_page"))
            params.add("cursor", cursor);
ClientResponse clientResponse = apiManager.getResource().path("/api/opportunity")
                .queryParams(params).accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);*/

        ClientResponse clientResponse = apiManager.getResource().path("/api/filters/filter/dynamic-filter").type("application/x-www-form-urlencoded")
                .accept(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, "page_size=" + page_size + "&global_sort_key=-created_time" + (cursor != null && !cursor.isEmpty() && !"first_page".equalsIgnoreCase(cursor) ? "&cursor=" + cursor : "") + "&filterJson={\"rules\":[{\"LHS\":\"updated_time\",\"CONDITION\":\"LAST\",\"RHS\":\"" + daysAgo + "\"}],\"or_rules\":[],\"contact_type\":\"" + type + "\"}");

        System.out.println("Status of API = " + clientResponse);
        JSONArray response = null;
        try {
            response = new JSONArray(clientResponse.getEntity(String.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }

    public static JSONArray getDeals(String page_size, String cursor, String baseUrl, String userEmail, String restAPIKey)
            throws Exception {
        APIManager apiManager = new APIManager(baseUrl, userEmail, restAPIKey);
        System.out
                .println("Getting opportunities by page and cursor-----------------------");
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();

        if (page_size == null || page_size.isEmpty())
            page_size = "20";

        params.add("page_size", page_size);
        params.add("global_sort_key", "-created_time");

        if (!cursor.isEmpty() && !cursor.equalsIgnoreCase("first_page"))
            params.add("cursor", cursor);

        ClientResponse clientResponse = apiManager.getResource().path("/api/opportunity")
                .queryParams(params).accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        System.out.println("Status of API = " + clientResponse);
        JSONArray contacts = null;
        try {
            contacts = new JSONArray(clientResponse.getEntity(String.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contacts;
    }

}