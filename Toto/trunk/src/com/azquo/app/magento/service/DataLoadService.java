package com.azquo.app.magento.service;

import com.azquo.memorydb.Name;
import com.azquo.service.AzquoMemoryDBConnection;
import com.azquo.service.NameService;
import com.azquo.service.ValueService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by cawley on 07/08/14.
 *
 */
public final class DataLoadService {

    //Getting the runtime reference from system
    Runtime runtime = Runtime.getRuntime();
    int mb = 1024*1024;

    private final String latestupdate = "Latest update";

    @Autowired
    private NameService nameService;

    @Autowired
    private ValueService valueService;

/*    final Map<Integer, Name> products = new HashMap<Integer, Name>();
    final Map<Integer, Name> categories = new HashMap<Integer, Name>();
    //final Map<Integer, MagentoOrderLineItem> orderLineItems = new HashMap<Integer, MagentoOrderLineItem>();
    final Map<String, String> optionValueLookup = new HashMap<String, String>();*/

    public String findLastUpdate(AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        String date = "never";
        Name order = nameService.findByName(azquoMemoryDBConnection, "order");
        if (order!=null){
            String lastUpdate = order.getAttribute(latestupdate);
            if (lastUpdate!=null){
                date = lastUpdate;
            }
        }
        return date;
    }

    public void loadData(AzquoMemoryDBConnection azquoMemoryDBConnection, InputStream data) throws Exception {
        loadData(azquoMemoryDBConnection, data != null ? new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8)) : null);
    }

    public void loadData(AzquoMemoryDBConnection azquoMemoryDBConnection, BufferedReader br) throws Exception {
        Map<String, List<Map<String, String>>> tableMap = new HashMap<String, List<Map<String, String>>>();
        if (br == null){
            br = new BufferedReader(new FileReader("/home/bill/Sear/magento/testdata_dump.txt"));
        }

        long marker = System.currentTimeMillis();

        String line;
        List<Map<String, String>> currentTableDataMap = null;
        String[] currentColumnNames = null;
        int count = 0;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("||||TABLE:")) {
                count = 0;
                currentColumnNames = null;
                String tableName = line.substring(10);
                System.out.println();
                System.out.print("Initial load of : " + tableName);
                // I'm not going to support tables being loaded in two chunks I see no point. THis would overwrite data if a table were referenced twice
                currentTableDataMap = new ArrayList<Map<String, String>>(); // and I know this repeats keys for each row, the goal here is ease of use for importing, not efficiency
                tableMap.put(tableName, currentTableDataMap);
            } else { // data, is it the first one?
                if (currentColumnNames == null) {
                    currentColumnNames = line.split("\t", -1);
                } else {
                    count++;
                    String[] lineValues = line.split("\t", -1);
                    Map<String, String> dataRowMap = new HashMap<String, String>();
                    for (int i = 0; i < lineValues.length;i++) {
                        String val = lineValues[i].intern();
                       // //zap the time element of any dates imported
                       // Date date = valueService.interpretDate(val);
                       // if (date!=null){
                       //     val = val.substring(0,10);
                       // }

                        dataRowMap.put(currentColumnNames[i].intern(),val);
                    }
                    currentTableDataMap.add(dataRowMap);
                    if (count%10000 == 0){
                        System.out.print(".");
                    }
                }
            }
        }
        System.out.println();
        System.out.println("initial load of magento data done");
        System.out.println("Trying to make some product objects");

        Name topProduct = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "product", null, false);
        Name productAttributesName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product Attributes", topProduct, false);
        Name productCategories = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product categories", topProduct, false);
        Name allSKUs = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All SKUs", topProduct, false);
        Name allProducts = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All Products", topProduct, false);
        Name allCategories = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All Categories", topProduct, false);

        Name uncategorisedProducts = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Uncategorised Products", topProduct, false);

        //find out the name attribute numbers ....
        String categoryEntityId = null;
        String productEntityId = null;
        String customerEntityId = null;
        String firstNameId = null;
        String lastNameId = null;
        for (Map<String, String> entityTypeRecord : tableMap.get("eav_entity_type")) {
            if (entityTypeRecord.get("entity_type_code")!=null) {
                if (entityTypeRecord.get("entity_type_code").equals("catalog_category")) {
                    categoryEntityId = entityTypeRecord.get("entity_type_id");
                }
                if (entityTypeRecord.get("entity_type_code").equals("catalog_product")) {
                    productEntityId = entityTypeRecord.get("entity_type_id");
                }
                if (entityTypeRecord.get("entity_type_code").equals("customer")) {
                    customerEntityId = entityTypeRecord.get("entity_type_id");
                }
            }

        }

        String categoryNameId = null;
        String productNameId = null;
        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            if (attribute.get("attribute_code").equals("name")) { // ok we're going to get a list of relevant attributes for the product then . . .
                if (attribute.get("entity_type_id").equals(categoryEntityId)) {
                    categoryNameId = attribute.get("attribute_id");
                }
                if (attribute.get("entity_type_id").equals(productEntityId)) {
                    productNameId = attribute.get("attribute_id");
                }

            }
        }

        //now start the real work - categories first
        Map<String, Name> azquoCategoriesFound = new HashMap<String, Name>();
        Map<String, Name> azquoProductsFound = new HashMap<String, Name>();
        List<String> languages = new ArrayList<String>();
        List<String> defaultLanguage = new ArrayList<String>();
        defaultLanguage.add(Name.DEFAULT_DISPLAY_NAME);

        for (Map<String, String> entityTypeRecord : tableMap.get("catalog_category_entity")) {
            //invert the path for uploading to Azquo  -  1/2/3 becomes `3`,`2`,`1`
            StringTokenizer pathBits = new StringTokenizer(entityTypeRecord.get("path"), "/");
            String path = "";
            while (pathBits.hasMoreTokens()) {
                path = "`" + pathBits.nextToken() + "`," + path;
            }
            languages.add("MagentoCategoryID");
            Name categoryName =  nameService.findOrCreateNameStructure(azquoMemoryDBConnection, path.substring(0, path.length() - 1), productCategories, true, languages);

            azquoCategoriesFound.put(entityTypeRecord.get("entity_id"),categoryName);
            allCategories.addChildWillBePersisted(categoryName);

        }
        //now name the categories
        for (Map<String, String> attributeRow : tableMap.get("catalog_category_entity_varchar")) { // should (!) have us looking in teh right place
            //only picking the name from all the category attributes

            if (attributeRow.get("attribute_id").equals(categoryNameId)) {
                // can return the name
                nameService.findByName(azquoMemoryDBConnection, attributeRow.get("entity_id"));
                if (azquoCategoriesFound.get(attributeRow.get("entity_id")) == null){
                    System.out.println("Entity id linked in catalog_category_entity_varchar that doesn't exist in catalog_category_entity : " + attributeRow.get("entity_id"));
                } else {
                    azquoCategoriesFound.get(attributeRow.get("entity_id")).setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, attributeRow.get("value"));
                }
            }
        }
        languages.clear();
        languages.add("MagentoProductID");
        List<String> productLanguages = new ArrayList<String>(languages);

        for (Map<String, String> entityRow : tableMap.get("catalog_product_entity")) {
            Name magentoName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + entityRow.get("entity_id"), allSKUs, true,languages);
            allProducts.addChildWillBePersisted(magentoName);
            if (!magentoName.findAllParents().contains(productCategories) && !magentoName.findAllParents().contains(uncategorisedProducts)) {
                uncategorisedProducts.addChildWillBePersisted(magentoName);
            }
            magentoName.setAttributeWillBePersisted("SKU", entityRow.get("sku"));
            azquoProductsFound.put(entityRow.get("entity_id"), magentoName);
        }
        //name the products...
        for (Map<String, String> attributeRow : tableMap.get("catalog_product_entity_varchar")) {
            //only picking the name from all the category attributes, and only choosing store 0 - other stores assumed to be different languages
            if (attributeRow.get("attribute_id").equals(productNameId) && attributeRow.get("store_id").equals("0")) {
                //Name magentoName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + attributeRow.get("entity_id"), topProduct, true,languages);
                if (azquoProductsFound.get(attributeRow.get("entity_id")) == null){
                    System.out.println("Entity id linked in catalog_product_entity_varchar that doesn't exist in catalog_product_entity : " + attributeRow.get("entity_id"));
                } else {
                    azquoProductsFound.get(attributeRow.get("entity_id")).setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, attributeRow.get("value"));
                }
            }
        }
        //create a product structure
        for (Map<String, String> relationRow : tableMap.get("catalog_product_super_link")) {
            Name child = azquoProductsFound.get(relationRow.get("product_id"));
            if (child == null){
                System.out.println("product id linked in catalog_product_super_link that doesn't exist in catalog_product_entity : " + relationRow.get("product_id"));
            } else {
                if (azquoProductsFound.get(relationRow.get("parent_id")) == null){
                    System.out.println("parent_id linked in catalog_product_super_link that doesn't exist in catalog_product_entity : " + relationRow.get("parent_id"));
                } else {
                    azquoProductsFound.get(relationRow.get("parent_id")).addChildWillBePersisted(child);
                    allProducts.removeFromChildrenWillBePersisted(child);
                }
            }
        }
        // and put products into categories

        for (Map<String, String> relationRow : tableMap.get("catalog_category_product")) {
            Name child = azquoProductsFound.get(relationRow.get("product_id"));
            if (child == null){
                System.out.println("product id linked in catalog_category_product that doesn't exist in catalog_product_entity : " + relationRow.get("product_id"));
            } else {
                Name category = azquoCategoriesFound.get(relationRow.get("category_id"));
                if (category == null){
                    System.out.println("category_id linked in catalog_category_product that doesn't exist in catalog_category_entity : " + relationRow.get("category_id"));
                } else {
                    if (!child.findAllParents().contains(category)){
                        category.addChildWillBePersisted(child);
                    }
                    uncategorisedProducts.removeFromChildrenWillBePersisted(child);
                }
            }
        }
        //now find the attributes that matter
        Set<String> attributes = new HashSet<String>();
        for (Map<String, String> attributeRow : tableMap.get("catalog_product_super_attribute")) {
            //only interested in the attribute_id
            attributes.add(attributeRow.get("attribute_id"));
        }
        //name the attributes that matter
        Map<String, String> attributeNames = new HashMap<String, String>();
        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            String attributeNo = attribute.get("attribute_id");
            if (attributes.contains(attributeNo)) {
                attributeNames.put(attributeNo, attribute.get("attribute_code"));
            }
        }


        //name the option values
        Map<String, String> optionValues = new HashMap<String, String>();
        for (Map<String, String> optionVal : tableMap.get("eav_attribute_option_value")) {
            String storeId = optionVal.get("store_id");
            if (storeId == null || (storeId!= null && storeId.equals("0"))) {
                optionValues.put(optionVal.get("option_id"), optionVal.get("value"));
            }
        }

        //.... and allocate them!
        for (Map<String, String> attVals : tableMap.get("catalog_product_entity_int")) {
            String attVal = attVals.get("attribute_id");
            if (attributes.contains(attVal)) {
                Name magentoName = azquoProductsFound.get(attVals.get("entity_id"));
                if (magentoName == null){
                    System.out.println("entity_id linked in catalog_product_entity_int that doesn't exist in catalog_product_entity : " + attVals.get("entity_id"));
                } else {
                    Name magentoProductCategory = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, attributeNames.get(attVal), productAttributesName, true,languages);
                    String val = attVals.get("value");
                    if (optionValues.get(val) != null) {
                        //note - this is NOT a product id, so don't use the product id to find it!
                        Name magentoOptionName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, optionValues.get(val), magentoProductCategory, true,null);
                        if (!magentoName.findAllParents().contains(magentoOptionName)) {
                            magentoOptionName.addChildWillBePersisted(magentoName);
                            //allProducts.removeFromChildrenWillBePersisted(magentoName);
                        }
                    } else {
                        System.out.println("found an option value " + val + " for " + magentoProductCategory.getDefaultDisplayName());
                    }
                }
            }
        }

        System.out.println("time to do initial non taxing bit " + (System.currentTimeMillis() - marker));
        marker = System.currentTimeMillis();

        double price = 0.0;
        double qty = 0.0;
        String configLine = null;
//        String productId = null;
        Name entities = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Entities",null,false);
        Name priceName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Price",entities, false);
           Name qtyName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Quantity",entities, false);
        Name allOrdersName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection,"All orders, order",null, false);
        Name allDates = nameService.findOrCreateNameStructure(azquoMemoryDBConnection,"All dates, date", null, false);
        Name allHours = nameService.findOrCreateNameStructure(azquoMemoryDBConnection,"All hours, date", null, false);
        final LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>(2);
        peers.put(allOrdersName, true);
        priceName.setPeersWillBePersisted(peers);
        qtyName.setPeersWillBePersisted(peers);

        Map<String, Name> azquoOrdersFound = new HashMap<String, Name>();

        System.out.println("about to go into sales flat order item " + (System.currentTimeMillis() - marker));
        marker = System.currentTimeMillis();
        long part1 = 0;
        long part2 = 0;
        long part3 = 0;
        long part4 = 0;
        long part5 = 0;
        long part51 = 0;
        long part52 = 0;
        long part53 = 0;
        long part6 = 0;
        long part7 = 0;
        long part71 = 0;
        long part72 = 0;
        long part8 = 0;
        int counter = 0;
        if (tableMap.get("sales_flat_order_item")==null){
            if (azquoMemoryDBConnection.getCurrentDatabase()!=null){
                azquoMemoryDBConnection.persist();
            }
            return;
        }
        for (Map<String, String> salesRow : tableMap.get("sales_flat_order_item")) {
            long thisCycleMarker = System.currentTimeMillis();
            if (configLine == null) {
                price = 0.0;
                qty = 0.0;
//                productId = salesRow.get("product_id");
                try {
                    price = Double.parseDouble(salesRow.get("base_row_invoiced"));
                    double qtyOrdered = Double.parseDouble(salesRow.get("qty_ordered"));
                    double qtyCancelled = Double.parseDouble(salesRow.get("qty_canceled"));
                    qty = qtyOrdered - qtyCancelled;
                } catch (Exception e) {
                    //ignore the line
                }
                if (salesRow.get("product_type").equals("configurable")) {
                    configLine = salesRow.get("item_id");
                }
            } else {
//                productId = salesRow.get("product_id");
                if (!configLine.equals(salesRow.get("parent_item_id"))) {
                    System.out.println("problem in importing sales items - config item " + configLine + " does not have a simple item associated");
                    qty = 0;
                }
                configLine = null;
            }
            part1 += (thisCycleMarker - System.currentTimeMillis());
            thisCycleMarker = System.currentTimeMillis();
            languages.clear();
            languages.add("MagentoOrderID");
            part2 += (thisCycleMarker - System.currentTimeMillis());
            thisCycleMarker = System.currentTimeMillis();
            if (configLine == null) {
                //store the values.   Qty and price have attributes order, product.  order is in all orders, and in the relevant date
                String orderNo = "Order " + salesRow.get("order_id");
                Name orderName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,orderNo, allOrdersName,true,languages);
                part3 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                azquoOrdersFound.put(orderNo, orderName);
                //adding 'Item ' to the item number so as not to confuse with order number for the developer - the system should be happy without it.
                Name orderItemName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Item " + salesRow.get("item_id"), orderName, true,languages);
                Set<Name> namesForValue = new HashSet<Name>();
                part4 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                Name productName = azquoProductsFound.get(salesRow.get("product_id"));
                if (productName == null){
                    //not on the product list!!  in the demo database there was a giftcard in the sales that was not in the product list
                    productName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Product " + salesRow.get("product_id"), allSKUs,true, productLanguages);
                    allProducts.addChildWillBePersisted(productName);
                    //productName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME,salesRow.get("product_type"));
                }

                part5 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                String orderDate = salesRow.get("created_at").substring(0,10);
                String orderTime = salesRow.get("created_at").substring(11,13);
                int orderHour;
                try {
                    orderHour = Integer.parseInt(orderTime);
                    if (orderHour < 12){
                        orderTime = orderHour + " AM";
                    }else {
                        if (orderHour == 12) {
                            orderHour = 24;
                        }
                        orderTime = (orderHour - 12) + " PM";
                    }
                }catch (Exception e){
                    //leave orderTime as is
                }

                 part51 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                Name dateName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderDate, allDates, false,defaultLanguage);
                Name hourName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderTime,allHours, true, defaultLanguage);
                part52 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                dateName.addChildWillBePersisted(orderName);
                hourName.addChildWillBePersisted(orderName);
                productName.addChildWillBePersisted(orderItemName);
                part53 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                //namesForValue.add(productName);
                namesForValue.add(orderItemName);
                namesForValue.add(priceName);
                part6 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, price + "", namesForValue);
                part7 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                part71 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                namesForValue = new HashSet<Name>();
                namesForValue.add(orderItemName);
                namesForValue.add(qtyName);
                part72 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qty + "", namesForValue);
                part8 += (thisCycleMarker - System.currentTimeMillis());
                //thisCycleMarker = System.currentTimeMillis();


            }
            counter++;
            if (counter == 10000){
                System.out.println("10000 lines sales flat order item " + (System.currentTimeMillis() - marker));
                System.out.println("Total part breakdown");
                System.out.println("part 1 " + part1);
                System.out.println("part 2 " + part2);
                System.out.println("part 3 " + part3);
                System.out.println("part 4 " + part4);
                System.out.println("part 5 " + part5);
                System.out.println("part 51 " + part51);
                System.out.println("part 52 " + part52);
                System.out.println("part 53 " + part53);
                System.out.println("part 6 " + part6);
                System.out.println("part 7 " + part7);
                System.out.println("part 71 " + part71);
                System.out.println("part 72 " + part72);
                System.out.println("part 8 " + part8);
                marker = System.currentTimeMillis();
                counter = 0;

                System.out.println("name service time track" + nameService.getTimeTrackMapForConnection(azquoMemoryDBConnection));
                System.out.println("value service time track" + valueService.getTimeTrackMapForConnection(azquoMemoryDBConnection));


                System.out.println("##### Heap utilization statistics [MB] #####");

                //Print used memory
                System.out.println("Used Memory:"
                        + (runtime.totalMemory() - runtime.freeMemory()) / mb);

                //Print free memory
                System.out.println("Free Memory:"
                        + runtime.freeMemory() / mb);

                //Print total available memory
                System.out.println("Total Memory:" + runtime.totalMemory() / mb);

                //Print Maximum available memory
                System.out.println("Max Memory:" + runtime.maxMemory() / mb);

            }

        }



        languages.clear();
        languages.add("MagentoCustomerID");
        Name allCustomersName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection,"All customers, customer",null, false,languages);

        for (Map<String, String> orderRow : tableMap.get("sales_flat_order")) {
            //only importing the IDs at present
            Name orderName = azquoOrdersFound.get("Order " + orderRow.get("entity_id"));
            if (orderName != null){
                String customer = orderRow.get("customer_id");
                if (customer == null || customer.length()== 0) customer="Unknown";
                Name customerName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Customer " + customer, allCustomersName, true,languages);
                customerName.addChildWillBePersisted(orderName);
            }



        }
        //NOW THE CUSTOMERS!
        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            if (attribute.get("entity_type_id").equals(customerEntityId)){
                String attCode = attribute.get("attribute_code");
                if (attCode.equals("firstname")){
                    firstNameId = attribute.get("attribute_id");
                }
                if (attCode.equals("lastname")){
                    lastNameId = attribute.get("attribute_id");
                }
            }
         }

        for (Map<String, String> customerRec : tableMap.get("customer_entity")) {
            String customerId = customerRec.get("entity_id");
            String email = customerRec.get("email");
            Name customer = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Customer " + customerId, allCustomersName, true, languages);
            customer.setAttributeWillBePersisted("email", email);

        }

        for (Map<String, String> attributeRow : tableMap.get("customer_entity_varchar")) {
            //only picking the name from all the category attributes, and only choosing store 0 - other stores assumed to be different languages
            String attId =attributeRow.get("attribute_id");
            if (attId.equals(firstNameId) || attId.equals(lastNameId)){
                String valFound = attributeRow.get("value");
                String attFound;
                if (attId.equals(firstNameId)){
                    attFound = "First name";
                }else{
                    attFound = "Last name";
                }
                String customerId = attributeRow.get("entity_id");
                Name customer = nameService.findByName(azquoMemoryDBConnection,"Customer " + customerId, languages);
                if (customer != null){
                    customer.setAttributeWillBePersisted(attFound, valFound);
                    String firstName = customer.getAttribute("First name");
                    String lastName = customer.getAttribute("Last name");
                    String customerName;
                    if (firstName != null){
                        if (lastName!=null){
                            customerName = firstName + " " + lastName;
                        }else{
                            customerName = firstName;
                        }
                    }else{
                        customerName = lastName;
                    }
                    customer.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, customerName);
                }
            }
        }



        languages.clear();
        languages.add(Name.DEFAULT_DISPLAY_NAME);


        Name order = nameService.findByName(azquoMemoryDBConnection,"order", languages);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        order.setAttributeWillBePersisted(latestupdate, sdf.format(new Date()));
        if (azquoMemoryDBConnection.getCurrentDatabase()!=null){
            azquoMemoryDBConnection.persist();
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

        /*

        SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (Map<String, String> orderItem : tableMap.get("sales_flat_order_item")) {
            MagentoOrderLineItem magentoOrderLineItem = new MagentoOrderLineItem();
            magentoOrderLineItem.id = Integer.parseInt(orderItem.get("item_id"));
            if (orderItem.get("parent_item_id") != null) {
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
            if (orderItem.get("row_total_incl_tax") != null) {
                magentoOrderLineItem.rowTotalIncludingTax = Double.parseDouble(orderItem.get("row_total_incl_tax"));
            } else {
                magentoOrderLineItem.rowTotalIncludingTax = 0;
            }
            orderLineItems.put(magentoOrderLineItem.id, magentoOrderLineItem);
        }

        System.out.println("load complete");

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

     }
*/
}
