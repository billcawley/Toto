package com.azquo.app.magento.service;

import java.io.*;
import java.util.*;

/**
 * Created by cawley on 07/08/14.
 */
public final class DataLoadService {

    private final Map<String, List<Map<String, String>>> tableMap;

    public DataLoadService() throws IOException {
        tableMap = new HashMap<String, List<Map<String, String>>>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("/home/cawley/magentodatadump.txt")));

        String line;
        List<Map<String,String>> currentTableDataMap = null;
        List<String> currentColumnNames = new ArrayList<String>();
        while ((line = br.readLine()) != null) {
            if (line.startsWith("||||TABLE:")){
                String tableName = line.substring(10);
                System.out.println("Initial load of : " + tableName);
                // I'm not going to support tables being loaded in two chunks I see no point. THis would overwrite data if a table were referenced twice
                currentTableDataMap = new ArrayList<Map<String, String>>(); // and I know this repeats keys for each row, the goal here is ease of use for importing, not efficiency
                tableMap.put(tableName, currentTableDataMap);
                currentColumnNames = new ArrayList<String>();
            } else { // data, is it the first one?
                if (currentColumnNames.isEmpty()){
                    StringTokenizer st = new StringTokenizer(line, "\t");
                    while (st.hasMoreTokens()){
                        currentColumnNames.add(st.nextToken());
                    }
                } else {
                    int index = 0;
                    StringTokenizer st = new StringTokenizer(line, "\t");
                    Map<String, String> dataRowMap = new HashMap<String, String>();
                    while (st.hasMoreTokens()){
                        String value = st.nextToken();
                        if (!value.isEmpty() && !value.equals("NULL")){
                            if (index >= currentColumnNames.size()){
                                System.out.println("things not as expected, extra tab?? " + line);
                            } else {
                                dataRowMap.put(currentColumnNames.get(index), value);
                            }
                        }
                        index++;
                    }
                    currentTableDataMap.add(dataRowMap);
                }
            }
        }
        System.out.println("initial load of magento data done");
        System.out.println("Trying to make some product objects");
        Map<String, String> productEntityTypeRecord = null;
        for (Map<String, String> entityTypeRecord : tableMap.get("eav_entity_type")){
            if (entityTypeRecord.get("entity_type_code").equals("catalog_product")){
                productEntityTypeRecord = entityTypeRecord;
            }
        }

        List<Map<String, String>> productAttributes = new ArrayList<Map<String, String>>();
        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            if (attribute.get("entity_type_id").equals(productEntityTypeRecord.get("entity_type_id"))) { // ok we're going to get a list of relevant attributes for the product then . . .
                productAttributes.add(attribute);
            }
        }

        ArrayList<MagentoProduct> products = new ArrayList<MagentoProduct>();
        if (productEntityTypeRecord != null){
            for (Map<String, String> entityTypeRecord : tableMap.get("catalog_product_entity")){
                MagentoProduct magentoProduct = new MagentoProduct();
                magentoProduct.id = Integer.parseInt(entityTypeRecord.get("entity_id"));
                magentoProduct.sku = entityTypeRecord.get("sku");
                Map<String, String> productAttributesAndValues = new HashMap<String, String>();
                // ok now some brain ache to set up all the appropriate attributes . . .
                for (Map<String, String> attribute : productAttributes){
                        String attributeName = attribute.get("frontend_label");
                        // now see if there's a value for this thing???
                        if (!attribute.get("backend_type").equals("static")){ // static means the value is in teh entity table
                            for (Map<String, String> possibleValueRow : tableMap.get("catalog_product_entity" + "_" + attribute.get("backend_type"))){ // should (!) have us looking in teh right place
                                if (possibleValueRow.get("attribute_id").equals(attribute.get("attribute_id")) && possibleValueRow.get("entity_id").equals(entityTypeRecord.get("entity_id"))){ // then it should (!) be the value we're looking for?
                                    productAttributesAndValues.put(attributeName, possibleValueRow.get("value"));
                                }
                            }
                        }
                }
                magentoProduct.attributes = productAttributesAndValues;
                System.out.println("Adding product : " + magentoProduct.attributes.get("Name") + " atts : " + magentoProduct.attributes);
                products.add(magentoProduct);
            }
        }
        System.out.println("attempted to organise some product data");
    }

    public class MagentoProduct {
        public int id;
        public String sku;
        public Map<String, String> attributes;
    }

}
