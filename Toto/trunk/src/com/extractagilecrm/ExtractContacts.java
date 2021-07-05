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

    public static String extract() {
        System.out.print("current time millis : " + System.currentTimeMillis());
        Gson gson = new Gson();
        try {
            List<String> config = Files.readAllLines(Paths.get("/home/edward/bonza.txt"), Charset.defaultCharset());
            String baseUrl = config.get(0);
            String userEmail = config.get(1);
            String restAPIKey = config.get(2);
            // Create a connection to Agile CRM
            APIManager apiManager = new APIManager(baseUrl, userEmail, restAPIKey);
            ContactAPI contactApi = apiManager.getContactAPI();
            String cursor = "first_page";
//            StringBuilder contactsJson = new StringBuilder();
            // remove campaign status + a certain amount? there are a lot . . .
            while (cursor != null){
                // List of tags to add it to contact
                // --------------------- Get contacts -----------------------------

                JSONArray contacts = getDeals("250", cursor);
                FileUtils.writeStringToFile(new File("/home/edward/Downloads/" + System.currentTimeMillis() + "deals.json"), contacts.toString());
                cursor = null;
                for (Object contact : contacts){
                    JSONObject jcontact = (JSONObject) contact;
/*                    XSSFWorkbook wb = new XSSFWorkbook();
                    XSSFSheet sheet = wb.createSheet("Contact");
                    AtomicInteger rownum = new AtomicInteger(0);
                    for (String key : jcontact.keySet()){
                        writeRow(sheet, rownum, 0, key, jcontact.get(key));
                    }
                    FileOutputStream fos = new FileOutputStream("/home/edward/Downloads/" + System.currentTimeMillis() + "bonzatest.xlsx");
                    wb.write(fos);
                    fos.close();*/
                    if (jcontact.has("cursor")){
                        cursor = jcontact.getString("cursor");
                    }
                    //System.out.println(contact);
                }
                System.out.println("+++++++++++++++++++ cursor : " + cursor);
            }

/*            Iterator<String> headingsIterator = headings.iterator();
            while (headingsIterator.hasNext()){
                String heading  = headingsIterator.next();
                if (heading.contains("[")){
                    if (Integer.parseInt(heading.substring(heading.indexOf("[") + 1, heading.indexOf("]"))) > 10){
                        headingsIterator.remove();
                    }
                }
            }

            TestController.writeCSV("/home/edward/bonzacontacts.tsv",new ArrayList<>(headings), lines);*/

/*
            cursor = "first_page";
            headings = new HashSet<>();
            lines = new ArrayList<>();
            // remove campaign status + a certain amount? there are a lot . . .
            while (cursor != null){
                // List of tags to add it to contact
                // --------------------- Get contacts -----------------------------
                JSONArray deals = getDeals("500", cursor);
                cursor = null;
                for (Object deal : deals){
                    //System.out.println(contact);
                    JSONObject deal1 = (JSONObject) deal;
                    Map<String, Object> stringObjectMap = JsonFlattener.flattenAsMap(deal1.toString());
                    //System.out.println(contacts.toString());
                    Map<String, String> line = new HashMap<>();
                    for (String key : stringObjectMap.keySet()){
                        headings.add(key);
                        //System.out.println(key + " : " + stringObjectMap.get(key));
                        line.put(key, stringObjectMap.get(key) + "");
                    }
                    cursor = line.get("cursor");
                    lines.add(line);
                }
                System.out.println("+++++++++++++++++++ cursor : " + cursor);
            }
            TestController.writeCSV("/home/edward/bonzadeals.tsv",new ArrayList<>(headings), lines);*/
        } catch (Exception e) {
            System.out.println("message" + e.getMessage());
            e.printStackTrace();
        }
        return "";
    }


    public static JSONArray getDeals(String page_size, String cursor)
            throws Exception {
        // todo - don't do this every time
        List<String> config = Files.readAllLines(Paths.get("/home/edward/bonza.txt"), Charset.defaultCharset());
        String baseUrl = config.get(0);
        String userEmail = config.get(1);
        String restAPIKey = config.get(2);
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