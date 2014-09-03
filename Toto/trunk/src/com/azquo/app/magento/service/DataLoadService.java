package com.azquo.app.magento.service;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by cawley on 07/08/14.
 *
 */
public final class DataLoadService {

    private final Map<String, List<Map<String, String>>> tableMap;
    final Map<Integer, MagentoProduct> products;
    final Map<Integer, MagentoCategory> categories;
    final Map<Integer, MagentoOrderLineItem> orderLineItems;
    final Map<String, String> optionValueLookup;

    public DataLoadService() throws IOException {
        tableMap = new HashMap<String, List<Map<String, String>>>();
        products = new HashMap<Integer, MagentoProduct>();
        categories = new HashMap<Integer, MagentoCategory>();
        orderLineItems = new HashMap<Integer, MagentoOrderLineItem>();
        optionValueLookup = new HashMap<String, String>();
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

        // lookup for option values
        // NOTE! I'm currently ignoring store id here

        for (Map<String, String> optionValue : tableMap.get("eav_attribute_option_value")){
            optionValueLookup.put(optionValue.get("option_id"), optionValue.get("value"));
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

        if (productEntityTypeRecord != null){
            for (Map<String, String> entityTypeRecord : tableMap.get("catalog_product_entity")){
                MagentoProduct magentoProduct = new MagentoProduct();
                magentoProduct.id = Integer.parseInt(entityTypeRecord.get("entity_id"));
                magentoProduct.sku = entityTypeRecord.get("sku");
                Map<String, String> productAttributesAndValues = new HashMap<String, String>();

                Set<String> optionAttributeIds = new HashSet<String>(); // e.g. colour size etc

                for (Map<String, String> optionAttribute : tableMap.get("catalog_product_super_attribute")) {
                    if (optionAttribute.get("product_id").equals(entityTypeRecord.get("entity_id"))) { // an option for this product
                        optionAttributeIds.add(optionAttribute.get("attribute_id"));
                    }
                }

                Set<String> options = new HashSet<String>();


                // ok now some brain ache to set up all the appropriate attributes . . .
                for (Map<String, String> attribute : productAttributes){
                        String attributeName = attribute.get("frontend_label");
                        // now see if there's a value for this thing???
                        if (!attribute.get("backend_type").equals("static")){ // static means the value is in teh entity table
                            for (Map<String, String> possibleValueRow : tableMap.get("catalog_product_entity" + "_" + attribute.get("backend_type"))){ // should (!) have us looking in teh right place
                                if (possibleValueRow.get("attribute_id").equals(attribute.get("attribute_id")) && possibleValueRow.get("entity_id").equals(entityTypeRecord.get("entity_id"))){ // then it should (!) be the value we're looking for?
                                    productAttributesAndValues.put(attributeName, possibleValueRow.get("value"));
                                    if (optionAttributeIds.contains(possibleValueRow.get("attribute_id"))){
                                        options.add(attributeName);
                                    }
                                }
                            }
                        }
                }

                magentoProduct.options = options;
                magentoProduct.attributes = productAttributesAndValues;
                System.out.println("Adding product : " + magentoProduct.attributes.get("Name") + " atts : " + magentoProduct.attributes);
                products.put(magentoProduct.id, magentoProduct);
            }
        }

        Map<String, String> categoryEntityTypeRecord = null;
        for (Map<String, String> entityTypeRecord : tableMap.get("eav_entity_type")){
            if (entityTypeRecord.get("entity_type_code").equals("catalog_category")){
                categoryEntityTypeRecord = entityTypeRecord;
            }
        }

        List<Map<String, String>> categoryAttributes = new ArrayList<Map<String, String>>();
        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            if (attribute.get("entity_type_id").equals(categoryEntityTypeRecord.get("entity_type_id"))) { // ok we're going to get a list of relevant attributes for the product then . . .
                categoryAttributes.add(attribute);
            }
        }

        if (categoryEntityTypeRecord != null){
            for (Map<String, String> entityTypeRecord : tableMap.get("catalog_category_entity")){
                MagentoCategory magentoCategory = new MagentoCategory();
                magentoCategory.id = Integer.parseInt(entityTypeRecord.get("entity_id"));
                if (entityTypeRecord.get("parent_id") != null){
                    magentoCategory.parent_id = Integer.parseInt(entityTypeRecord.get("parent_id"));
                } else {
                    magentoCategory.parent_id = 0;
                }
                magentoCategory.path = entityTypeRecord.get("path");
                if (entityTypeRecord.get("position") != null){
                    magentoCategory.position = Integer.parseInt(entityTypeRecord.get("position"));
                } else {
                    magentoCategory.position = 0;
                }
                if (entityTypeRecord.get("level") != null){
                    magentoCategory.level = Integer.parseInt(entityTypeRecord.get("level"));
                } else {
                    magentoCategory.level = 0;
                }
                Map<String, String> categoryAttributesAndValues = new HashMap<String, String>();
                for (Map<String, String> attribute : categoryAttributes){
                    String attributeName = attribute.get("frontend_label");
                    // now see if there's a value for this thing???
                    if (!attribute.get("backend_type").equals("static")){ // static means the value is in teh entity table
                        for (Map<String, String> possibleValueRow : tableMap.get("catalog_category_entity" + "_" + attribute.get("backend_type"))){ // should (!) have us looking in teh right place
                            if (possibleValueRow.get("attribute_id").equals(attribute.get("attribute_id")) && possibleValueRow.get("entity_id").equals(entityTypeRecord.get("entity_id"))){ // then it should (!) be the value we're looking for?
                                categoryAttributesAndValues.put(attributeName, possibleValueRow.get("value"));
                            }
                        }
                    }
                }
                magentoCategory.attributes = categoryAttributesAndValues;
                System.out.println("Adding category : " + magentoCategory.attributes.get("Name") + " atts : " + magentoCategory.attributes);
                categories.put(magentoCategory.id, magentoCategory);
            }
        }

/*        int id;
        int parent_id;
        Date created;
        String name;
        String sku;
        double quantityOrdered;
        double basePrice;
        double rowTotalIncludingTax;*/

        //2013-04-04 01:23:18

        SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Map<String, String> orderItem : tableMap.get("sales_flat_order_item")){
            MagentoOrderLineItem magentoOrderLineItem = new MagentoOrderLineItem();
            magentoOrderLineItem.id = Integer.parseInt(orderItem.get("item_id"));
            if (orderItem.get("parent_item_id") != null){
                magentoOrderLineItem.parent_id = Integer.parseInt(orderItem.get("parent_item_id"));
            } else {
                magentoOrderLineItem.parent_id = 0;
            }
            magentoOrderLineItem.product_id = Integer.parseInt(orderItem.get("product_id"));
            try {
                magentoOrderLineItem.created = inputDateFormat.parse(orderItem.get("created_at"));
            } catch (ParseException e) {
                e.printStackTrace();
            }

            magentoOrderLineItem.name = orderItem.get("name");
            magentoOrderLineItem.sku = orderItem.get("sku");
            magentoOrderLineItem.quantityOrdered = Double.parseDouble(orderItem.get("qty_ordered"));
            magentoOrderLineItem.basePrice = Double.parseDouble(orderItem.get("base_price"));
            if (orderItem.get("row_total_incl_tax") != null){
                magentoOrderLineItem.rowTotalIncludingTax = Double.parseDouble(orderItem.get("row_total_incl_tax"));
            } else {
                magentoOrderLineItem.rowTotalIncludingTax = 0;
            }
            orderLineItems.put(magentoOrderLineItem.id, magentoOrderLineItem);
        }

        System.out.println("load complete");
        System.out.println(getMagentoStructure());

    }

    public class MagentoProduct {
        public int id;
        public String sku;
        public Map<String, String> attributes;
        public Set<String> options;
        public Set<MagentoProduct> getChildren(){
            Set<MagentoProduct> toReturn = new HashSet<MagentoProduct>();
            //for (Map<String, String> product_relation : tableMap.get("catalog_product_relation")){
            for (Map<String, String> product_relation : tableMap.get("catalog_product_super_link")){
                if (product_relation.get("parent_id").equals(id + "")){
                    toReturn.add(products.get(Integer.parseInt(product_relation.get("product_id"))));
                }
            }
            return toReturn;
        }
        public Set<MagentoOrderLineItem> getOrderLines(){
            Set<MagentoOrderLineItem> toReturn = new HashSet<MagentoOrderLineItem>();
            for (MagentoOrderLineItem orderLineItem : orderLineItems.values()){
                if (orderLineItem.product_id == id){
                    toReturn.add(orderLineItem);
                }
            }
            return toReturn;
        }
        public MagentoProduct getParent(){
            for (Map<String, String> product_relation : tableMap.get("catalog_product_super_link")){
                if (product_relation.get("product_id").equals(id + "")){
                    return products.get(Integer.parseInt(product_relation.get("parent_id")));
                }
            }
            return null;
        }
    }

    public class MagentoCategory {
        public int id;
        public int parent_id;
        public String path;
        public int position;
        public int level;
        public Map<String, String> attributes;
        public Set<MagentoCategory> getChildren(){
            Set<MagentoCategory> toReturn = new HashSet<MagentoCategory>();
            for (MagentoCategory category : categories.values()){
                if (category.parent_id == id){
                    toReturn.add(category);
                }
            }
            return toReturn;
        }
        public Set<MagentoProduct> getProducts(){
            Set<MagentoProduct> toReturn = new HashSet<MagentoProduct>();
            for (Map<String, String> category_product_link : tableMap.get("catalog_category_product")){
                if (category_product_link.get("category_id").equals(id + "")){
                    toReturn.add(products.get(Integer.parseInt(category_product_link.get("product_id"))));
                }
            }
            return toReturn;
        }
    }
    // just the info we need for the moment, ignoring most columns
    public class MagentoOrderLineItem {
        int id;
        int parent_id;
        int product_id;
        Date created;
        String name;
        String sku;
        double quantityOrdered;
        double basePrice;
        double rowTotalIncludingTax;

        @Override
        public String toString() {
            return "MagentoOrderLineItem{" +
                    "id=" + id +
                    ", parent_id=" + parent_id +
                    ", product_id=" + product_id +
                    ", created=" + created +
                    ", name='" + name + '\'' +
                    ", sku='" + sku + '\'' +
                    ", quantityOrdered=" + quantityOrdered +
                    ", basePrice=" + basePrice +
                    ", rowTotalIncludingTax=" + rowTotalIncludingTax +
                    '}';
        }
    }

    public String getMagentoStructure(){
        StringBuilder toReturn = new StringBuilder();
        for (MagentoCategory category : categories.values()){
            if (category.parent_id == 0){
                toReturn.append(getCategoryStructure(category, 0));
            }
        }
        return toReturn.toString();
    }

    public String getCategoryStructure(MagentoCategory category, int tab){
        String toReturn = new String();
        for (int i = 0; i <= tab; i++){
            toReturn += "\t";
        }
        toReturn += "Category : " + category.attributes.get("Name") + "\n";
        Set<MagentoCategory> children = category.getChildren();
        for (MagentoCategory category1 : children){
            toReturn += getCategoryStructure(category1, tab + 1);
        }
        Set<MagentoProduct> products = category.getProducts();
        for (MagentoProduct product : products){
            if (product.getParent() == null){ // the product structure will show lover level products
                toReturn += getProductStructure(product, tab + 1);
            }
        }

        return toReturn;
    }

    public String getProductStructure(MagentoProduct product, int tab){
        String toReturn = new String();
        for (int i = 0; i <= tab; i++){
            toReturn += "\t";
        }
        MagentoProduct parent = product.getParent();
        if (parent != null){
            toReturn += "Product : " + product.attributes.get("Name");
            for (String option : parent.options){
                toReturn += " " + option + " : " + optionValueLookup.get(product.attributes.get(option));
            }
            toReturn += "\n";
        } else {
            toReturn += "Product : " + product.attributes.get("Name") + (product.options.isEmpty() ? "" : ", options : " + product.options) + "\n";
        }
        Set<MagentoProduct> children = product.getChildren();
        for (MagentoProduct product1 : children){
            toReturn += getProductStructure(product1, tab + 1);
        }
        for (MagentoOrderLineItem orderLineItem : product.getOrderLines()){
            for (int i = 0; i <= tab; i++){
                toReturn += "\t";
            }
            toReturn += "\tOrder line  : " + orderLineItem.toString() + "\n";
        }


        return toReturn;
    }

}
