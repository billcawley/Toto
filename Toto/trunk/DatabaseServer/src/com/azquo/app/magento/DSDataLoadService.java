package com.azquo.app.magento;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/05/15.
 * <p/>
 * The new home of Magento loading logic. Much of this was in DataLoadService.
 * <p/>
 * Really the logic in here is defined by the data as dumped from Magento.
 * <p>
 * To elaborate a little : in principle importing is done via the importing service but for cases like Magento there's nothing wring with custom code (it may be necessary)
 * just make sure it's in the app package. Maybe move this to Groovy? Certainly I want to experiment with that for per business importing.
 * <p>
 * Note : given that this is what one might call "dynamic" or script like I'm not going to get so worked up pouring over every line or getting a green light.
 */
public class DSDataLoadService {

    private static final String[] productAtts = {"url_path", "meta_description", "country_of_manufacture", "meta_title", "price", "weight", "ship_height", "ship_width", "ship_depth", "cost", "commodity"};

    private static void logMemUseage() {
        final Runtime runtime = Runtime.getRuntime();
        final int mb = 1024 * 1024;
        NumberFormat nf = NumberFormat.getInstance();
        System.out.println("--- MEMORY USED :  " + nf.format((runtime.totalMemory() - runtime.freeMemory()) / mb) + "MB of " + nf.format(runtime.totalMemory() / mb) + "MB, max allowed " + nf.format(runtime.maxMemory() / mb));
    }

    private final static String LATEST_UPDATE = "Latest update";
    private final static String REQUIRED_TABLES = "required tables";

    // convenience local object, don't see a need for getters/setters

    private static class SaleItem {
        Name itemName;
        double price;
        double tax;
        double origPrice;
        double qty;

        SaleItem() {
            itemName = null;
            price = 0.0;
            tax = 0.0;
            origPrice = 0.0;
            qty = 0.0;
        }
    }

    private static final DecimalFormat df = new DecimalFormat("#.00");

    // A simple marker in the Azquo DB saying when the last upload happened, set at the end of loadData.

    public static String findLastUpdate(final DatabaseAccessToken databaseAccessToken, final String remoteAddress) throws Exception {
        final AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        final Name orderName = NameService.findByName(azquoMemoryDBConnection, "order");
        if (orderName == null) {
            return null;
        }
        String lastUpdate = orderName.getAttribute(LATEST_UPDATE + " " + remoteAddress);
        if (lastUpdate == null) {
            lastUpdate = orderName.getAttribute(LATEST_UPDATE);
        }
        return lastUpdate;
    }

    public static boolean magentoDBNeedsSettingUp(final DatabaseAccessToken databaseAccessToken) throws Exception {
        final AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        return NameService.findByName(azquoMemoryDBConnection, "All Months") == null;
    }

    // generally default data but after the first time it will stay as it was then.

    public static String findRequiredTables(final DatabaseAccessToken databaseAccessToken, final String remoteAddress) throws Exception {
        final AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        String requiredTables = defaultData().replace("$starttime", "");
        String date = "never";
        final String lastUpdate = findLastUpdate(databaseAccessToken, remoteAddress);
        final Name order = NameService.findByName(azquoMemoryDBConnection, "order");
        if (order != null) {
            if (lastUpdate != null) {
                date = lastUpdate;
            }
            requiredTables = order.getAttribute(REQUIRED_TABLES);
            if (requiredTables == null) {
                requiredTables = defaultData();
            }
            requiredTables = requiredTables.replace("$starttime", date);
        }
        return requiredTables;
    }

    /* might be a case for koloboke if speed becomes a concern
    this function is too big for intellij to analyse properly, if we want to make this class more correct it should be broken up.
    in terms of logic it's essentially translating Magento into Azquo.
    I don't think it could be done by standard import rules, too much logic e.g. calculating bundles.
      */
    public static void loadData(DatabaseAccessToken databaseAccessToken, String filePath, String remoteAddress, String user) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        azquoMemoryDBConnection.setProvenance(user, "imported from Magento", "", "");
        Map<String, List<Map<String, String>>> tableMap = HashObjObjMaps.newMutableMap();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF8"));//changed for Windows.....
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
                // I'm not going to support tables being loaded in two chunks I see no point. This would overwrite data if a table were referenced twice.
                currentTableDataMap = new ArrayList<>(); // and I know this repeats keys for each row, the goal here is ease of use for importing, not efficiency
                // to try to reduce garbage I'm moving these to koloboke
                tableMap.put(tableName, currentTableDataMap);
            } else { // data, is it the first one?
                if (currentColumnNames == null) {
                    currentColumnNames = line.split("\t", -1);
                } else {
                    count++;
                    String[] lineValues = line.split("\t", -1);
                    Map<String, String> dataRowMap = HashObjObjMaps.newMutableMap();
                    for (int i = 0; i < lineValues.length; i++) {
                        // I think this is a fair .intern, assuming from here the strings are used "as is"
                        String val = lineValues[i].intern();
                        // //zap the time element of any dates imported
                        // Date date = ValueService.interpretDate(val);
                        // if (date!=null){
                        //     val = val.substring(0,10);
                        // }
                        dataRowMap.put(currentColumnNames[i].intern(), val);
                    }
                    currentTableDataMap.add(dataRowMap);
                    if (count % 10000 == 0) {
                        System.out.print(".");
                    }
                }
            }
        }
        System.out.println();
        System.out.println("initial load of magento data done");
        System.out.println("Trying to make some product objects");
        // Not sure about the string literals here. On the other hand they're not referenced anywhere else in the Java, these are database references.
        // in Azquo the import defines the schema, do we assume that someone writing Magento reports would look here or just inspect? Is it necessary to control such things?
        Name topProduct = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "product", null, false);
        Name productAttributesName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product Attributes", topProduct, false);
        Name productCategories = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product categories", topProduct, false);
        Name allSKUs = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All SKUs", topProduct, false);
        Name allProducts = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All Products", topProduct, false);
        Name orphans = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "SKU Orphans", topProduct, false);
        Name allCategories = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All Categories", topProduct, false);
        Name uncategorisedProducts = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Uncategorised Products", topProduct, false);

        //find out the name attribute numbers ....
        String categoryEntityId = null;
        String productEntityId = null;
        String customerEntityId = null;
        String addressEntityId = null;
        String firstNameId = null;
        String lastNameId = null;

        List<String> defaultLanguage = new ArrayList<>();
        defaultLanguage.add(Constants.DEFAULT_DISPLAY_NAME);
        List<String> dateLanguage = Collections.singletonList("date");
        Name storeName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "store", null, false, defaultLanguage);
        Name allStoresName = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All stores", storeName, false, defaultLanguage);
        Map<String, Name> storeGroupMap = HashObjObjMaps.newMutableMap();

        for (Map<String, String> storeGroupRec : tableMap.get("core_store_group")) {
            String groupId = storeGroupRec.get("group_id");
            String name = storeGroupRec.get("name");
            if (!groupId.equals("0")) {
                Name storeGroup = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, name, allStoresName, true, defaultLanguage);
                storeGroupMap.put(groupId, storeGroup);
            }
        }

        tableMap.remove("core_store_group");
        Map<String, Name> storeMap = HashObjObjMaps.newMutableMap();

        for (Map<String, String> storeRec : tableMap.get("core_store")) {
            String storeId = storeRec.get("store_id");
            String groupId = storeRec.get("group_id");
            String name = storeRec.get("name");
            if (!storeId.equals("0")) {
                Name store = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, name, storeGroupMap.get(groupId), true, defaultLanguage);
                storeMap.put(storeId, store);
            }
        }
        tableMap.remove("core_store");

        for (Map<String, String> entityTypeRecord : tableMap.get("eav_entity_type")) {
            if (entityTypeRecord.get("entity_type_code") != null) {
                if (entityTypeRecord.get("entity_type_code").equals("catalog_category")) {
                    categoryEntityId = entityTypeRecord.get("entity_type_id");
                }
                if (entityTypeRecord.get("entity_type_code").equals("catalog_product")) {
                    productEntityId = entityTypeRecord.get("entity_type_id");
                }
                if (entityTypeRecord.get("entity_type_code").equals("customer")) {
                    customerEntityId = entityTypeRecord.get("entity_type_id");
                }
                if (entityTypeRecord.get("entity_type_code").equals("customer_address")) {
                    addressEntityId = entityTypeRecord.get("entity_type_id");
                }
            }
        }

        Map<String, String> attIds = HashObjObjMaps.newMutableMap();

        String categoryNameId = null;
        String productNameId = null;
        String countryNameId = null;
        String postcodeNameId = null;

        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            String attCode = attribute.get("attribute_code");
            String entTypeId = attribute.get("entity_type_id");
            String attId = attribute.get("attribute_id");
            if (attCode.equals("name")) { // ok we're going to get a list of relevant attributes for the product then . . .
                if (entTypeId.equals(categoryEntityId)) {
                    categoryNameId = attId;
                }
            }
            if (entTypeId.equals(productEntityId)) {
                if (attCode.equals("name")) {
                    productNameId = attId;
                }
                for (String att : productAtts) {
                    if (attCode.equals(att)) {
                        attIds.put(att, attId);
                    }
                }
            }
        }
        //now start the real work - categories first
        Map<String, Name> azquoCategoriesFound = HashObjObjMaps.newMutableMap();
        Map<String, Name> azquoProductsFound = HashObjObjMaps.newMutableMap();
        Map<String, Name> azquoCustomersFound = HashObjObjMaps.newMutableMap();
        List<String> categoryLanguage = new ArrayList<>();
        categoryLanguage.add("MAGENTOCATEGORYID");
        Map<String, String> categoryNames = HashObjObjMaps.newMutableMap();
        //name the categories
        for (Map<String, String> attributeRow : tableMap.get("catalog_category_entity_varchar")) { // should (!) have us looking in the right place
            //only picking the name from all the category attributes
            if (attributeRow.get("attribute_id").equals(categoryNameId)) {
                if (attributeRow.get("store_id").equals("0")) {
                    categoryNames.put(attributeRow.get("entity_id"), attributeRow.get("value"));
                }
            }
        }
        tableMap.remove("catalog_category_entity_varchar");
        for (Map<String, String> entityTypeRecord : tableMap.get("catalog_category_entity")) {
            //invert the path for uploading to Azquo  -  1/2/3 becomes `3`,`2`,`1`
            StringTokenizer pathBits = new StringTokenizer(entityTypeRecord.get("path"), "/");
            String path = "";
            String thisCatNo = entityTypeRecord.get("entity_id");
            while (pathBits.hasMoreTokens()) {
                String catNo = pathBits.nextToken();
                if (catNo == null) {
                    System.out.println("we have a category with no name!");
                }
                if (path.isEmpty()) {
                    path = catNo;
                } else {
                    path = path + StringLiterals.MEMBEROF + catNo;
                }
            }
            //TODO consider what might happen if importing categories from two different databases - don't do it!
            Name categoryName = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, "`" + path + "`", productCategories, true, categoryLanguage);
            categoryName.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, categoryNames.get(thisCatNo));
            azquoCategoriesFound.put(thisCatNo, categoryName);
            allCategories.addChildWillBePersisted(categoryName);
        }
        tableMap.remove("catalog_category_entity");
        List<String> skuLanguage = new ArrayList<>();
        skuLanguage.add("SKU");
        for (Map<String, String> entityRow : tableMap.get("catalog_product_entity")) {
            if (entityRow.get("sku") == null || entityRow.get("sku").isEmpty()){
                System.out.println("Sku null on " + entityRow);
            } else {
                Name magentoName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, entityRow.get("sku"), allSKUs, true, skuLanguage);
                allProducts.addChildWillBePersisted(magentoName);
                if (!magentoName.findAllParents().contains(productCategories) && !magentoName.findAllParents().contains(uncategorisedProducts)) {
                    uncategorisedProducts.addChildWillBePersisted(magentoName);
                }
                azquoProductsFound.put(entityRow.get("entity_id"), magentoName);
            }
        }
        tableMap.remove("catalog_product_entity");
        readProductAttributes("catalog_product_entity_varchar", tableMap, azquoProductsFound, attIds, productNameId);
        readProductAttributes("catalog_product_entity_decimal", tableMap, azquoProductsFound, attIds, productNameId);
        //create a product structure
        for (Map<String, String> relationRow : tableMap.get("catalog_product_super_link")) {
            Name child = azquoProductsFound.get(relationRow.get("product_id"));
            if (child == null) {
                System.out.println("product id linked in catalog_product_super_link that doesn't exist in catalog_product_entity : " + relationRow.get("product_id"));
            } else {
                if (azquoProductsFound.get(relationRow.get("parent_id")) == null) {
                    System.out.println("parent_id linked in catalog_product_super_link that doesn't exist in catalog_product_entity : " + relationRow.get("parent_id"));
                } else {
                    azquoProductsFound.get(relationRow.get("parent_id")).addChildWillBePersisted(child);
                    allProducts.removeFromChildrenWillBePersisted(child);
                }
            }
        }
        tableMap.remove("catalog_product_super_link");
        // and put products into categories
        for (Map<String, String> relationRow : tableMap.get("catalog_category_product")) {
            Name child = azquoProductsFound.get(relationRow.get("product_id"));
            if (child == null) {
                System.out.println("product id linked in catalog_category_product that doesn't exist in catalog_product_entity : " + relationRow.get("product_id"));
            } else {
                Name category = azquoCategoriesFound.get(relationRow.get("category_id"));
                if (category == null) {
                    System.out.println("category_id linked in catalog_category_product that doesn't exist in catalog_category_entity : " + relationRow.get("category_id"));
                } else {
                    if (!child.findAllParents().contains(category)) {
                        category.addChildWillBePersisted(child);
                    }
                    uncategorisedProducts.removeFromChildrenWillBePersisted(child);
                }
            }
        }
        tableMap.remove("catalog_category_product");
        //now find the attributes that matter
/*        Set<String> attributes = HashObjSets.newMutableSet(tableMap.get("catalog_product_super_attribute").size());
        for (Map<String, String> attributeRow : tableMap.get("catalog_product_super_attribute")) {
            //only interested in the attribute_id
            attributes.add(attributeRow.get("attribute_id"));
        }        //only interested in the attribute_id*/
        //name the attributes that matter
        Map<String, String> attributeNames = HashObjObjMaps.newMutableMap();
        Set<String> attributeNoLookup = HashObjSets.newMutableSet(); // list the booleans in here

        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            String attributeNo = attribute.get("attribute_id");
            //attributeNames.put(attributeNo, attribute.get("attribute_code"));
            String attName = attribute.get("frontend_label");
            if (attName == null || attName.isEmpty()){
                attName = attribute.get("attribute_code");
            }
            if (attName == null || attName.isEmpty()){
                attName = "BLANK";
            }
            attributeNames.put(attributeNo, attName);
            // not default or custom then don't try to look up
            if (!"".equals(attribute.get("source_model")) && !"eav/entity_attribute_source_table".equals(attribute.get("source_model"))){
                attributeNoLookup.add(attributeNo);
            }
        }
        tableMap.remove("catalog_product_super_attribute");
        //name the option values
        Map<String, String> optionValues = HashObjObjMaps.newMutableMap();
        for (Map<String, String> optionVal : tableMap.get("eav_attribute_option_value")) {
            String storeId = optionVal.get("store_id");
            if (storeId == null || (storeId != null && storeId.equals("0"))) {
                optionValues.put(optionVal.get("option_id"), optionVal.get("value"));
            }
        }
        tableMap.remove("eav_attribute_option_value");
        //.... and allocate them!
        for (Map<String, String> attVals : tableMap.get("catalog_product_entity_int")) {
            String attNo = attVals.get("attribute_id");
            //if (attributes.contains(attVal)) {
            if (attributeNames.keySet().contains(attNo)) {
                Name magentoName = azquoProductsFound.get(attVals.get("entity_id"));
                if (magentoName == null) {
                    System.out.println("entity_id linked in catalog_product_entity_int that doesn't exist in catalog_product_entity : " + attVals.get("entity_id"));
                } else {
                    Name magentoProductCategory = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, attributeNames.get(attNo), productAttributesName, true, skuLanguage);
                    String val = attVals.get("value");
                    if (optionValues.get(val) != null || attributeNoLookup.contains(attNo)) {
                        String resolvedValue = optionValues.get(val);
                        if (attributeNoLookup.contains(attNo)){ // hack for things like boolean
                            resolvedValue = val;
                        }
                        /* new code added by Edd according to instruction from WFC 10/11/2016. A product (magentoName) cannot be in more than one option
                        On a clean load this won't happen anyway but on update it can e.g. in stock/out of stock. If the product is not removed from options first
                        then on a subsequent upload a product could appear as both in and out of stock! So run through existing options taking the product out . . .
                         */
                        for (Name existingOption : magentoProductCategory.getChildren()){
                            existingOption.removeFromChildrenWillBePersisted(magentoName);
                        }
                        //note - this is NOT a product id, so don't use the product id to find it!
                        List<String> productAttributeLanguage = new ArrayList<>();
                        productAttributeLanguage.add("PRODUCT ATTRIBUTE");
                        Name magentoOptionName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, resolvedValue, magentoProductCategory, true, productAttributeLanguage);
                        if (!magentoName.findAllParents().contains(magentoOptionName)) {
                            magentoOptionName.addChildWillBePersisted(magentoName);
                            if (allProducts.getChildren().contains(magentoName)) {
                                orphans.addChildWillBePersisted(magentoName);
                            }
                        }
                    } else {
                        //System.out.println("found an option value " + val + " for " + magentoProductCategory.getDefaultDisplayName());
                    }
                }
            }
        }
        tableMap.remove("catalog_product_entity_int");
        String currentParent = "";
        Name bundleName = null;
        String bundlePrices = "";
        for (Map<String, String> attVals : tableMap.get("catalog_product_bundle_selection")) {
            String parentId = attVals.get("parent_product_id");
            if (!currentParent.equals(parentId)) {
                if (currentParent.length() > 0) {
                    bundleName.setAttributeWillBePersisted("BUNDLEPRICES", bundlePrices);
                }
                bundlePrices = "";
                currentParent = parentId;
                bundleName = azquoProductsFound.get(parentId);
                if (bundleName == null) {
                    //this should not happen!
                    bundleName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + parentId, allSKUs, true, skuLanguage);
                    azquoProductsFound.put(parentId, bundleName);
                    allProducts.addChildWillBePersisted(bundleName);
                }
            }
            String childId = attVals.get("product_id");
            Name childName = azquoProductsFound.get(childId);
            if (childName == null) {
                //this should not happen!
                childName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + childId, allSKUs, true, skuLanguage);
                azquoProductsFound.put(childId, childName);
                allProducts.addChildWillBePersisted(childName);
            }
            String sKU = childName.getAttribute("SKU");
            String price = attVals.get("selection_price_value");
            if (bundlePrices.length() > 0) bundlePrices += ",";
            bundlePrices += sKU + "=" + price;
        }
        if (currentParent.length() > 0) {
            bundleName.setAttributeWillBePersisted("bundleprices", bundlePrices);
        }

        tableMap.remove("catalog_product_bundle_selection");
        Name entities = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Entities", null, false);
        Name orderEntities = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Order entities", entities, false);
        Name productEntities = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product entities", entities, false);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Name date = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "date", null, false);
        Name allDates = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All dates", date, false);

        if (tableMap.get("cataloginventory_stock_item") != null) {
            Name inStockName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "In Stock", productEntities, true);
            Name today = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, dateFormat.format(new Date()), date, false);
            Name stockDates = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Stock dates", date, true);
            stockDates.addChildWillBePersisted(today);
            if (today == null) {
                throw new Exception("the dates in the system do not extend sufficiently to store the stock");
            }
            for (Map<String, String> stockVals : tableMap.get("cataloginventory_stock_item")) {
                String product = stockVals.get("product_id");
                String qty = stockVals.get("qty");
                if (product != null && qty != null) {
                    Name productName = azquoProductsFound.get(product);
                    if (productName == null) {
                        System.out.println("unable to find product_id : " + product + " used in cataloginventory_stock_item");
                    } else {
                        Set<Name> namesForValue = HashObjSets.newMutableSet();
                        namesForValue.add(today);
                        namesForValue.add(inStockName);
                        namesForValue.add(productName);
                        ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qty, namesForValue);
                    }
                }
            }
        }

        System.out.println("time to do initial non taxing bit " + (System.currentTimeMillis() - marker));
        marker = System.currentTimeMillis();
        SaleItem bundleTotal = new SaleItem();
        List<SaleItem> bundleItems = new ArrayList<>();

        double price = 0.0;
        double qty = 0.0;
        double qtyCanceled = 0.0;

        double tax = 0.0;
        double weight = 0.0;
        String configLine = null;
//        String productId = null;
        Name priceName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Price", orderEntities, false);
        Name shippingName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Shipping", orderEntities, false);
        Name shippingTaxName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Shipping tax", orderEntities, false);
        Name qtyName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Quantity", orderEntities, false);
        Name weightName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Weight", orderEntities, false);
        Name canceledName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Canceled quantity", orderEntities, false);
        Name discountName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Discount", orderEntities, false);
        Name taxName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Tax", orderEntities, false);
        Name ordersName = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, "order", null, false);
        Name allOrdersName = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All orders", ordersName, false);
        Name orderStatusName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All Order statuses", ordersName, false);
        Name allCurrenciesName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All currencies", ordersName, false);
        Name allHours = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, "date" + StringLiterals.MEMBEROF + "All hours", null, false);

        Name customersName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "customer", null, false, defaultLanguage);
        Name allCustomersName = NameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All customers", customersName, false, defaultLanguage);
        Name allCountriesName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All countries", customersName, false, defaultLanguage);
        Name allGroupsName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All customer groups", customersName, false, defaultLanguage);
        List<String> emailLanguage = new ArrayList<>();
        emailLanguage.add("email");

        System.out.println("customer groups");
        Map<String, Name> customerGroups = HashObjObjMaps.newMutableMap();
        if (tableMap.get("customer_group") != null) {
            for (Map<String, String> group : tableMap.get("customer_group")) {
                String groupId = group.get("customer_group_id");
                String groupString = group.get("customer_group_code");
                Name groupName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, groupString, allGroupsName, true, emailLanguage);
                customerGroups.put(groupId, groupName);
            }
        }

        //NOW THE CUSTOMERS!
        for (Map<String, String> attribute : tableMap.get("eav_attribute")) {
            if (attribute.get("entity_type_id").equals(customerEntityId)) {
                String attCode = attribute.get("attribute_code");
                String attId = attribute.get("attribute_id");
                if (attCode.equals("firstname")) {
                    firstNameId = attId;
                }
                if (attCode.equals("lastname")) {
                    lastNameId = attId;
                }
            }
            if (attribute.get("entity_type_id").equals(addressEntityId)) {
                String attCode = attribute.get("attribute_code");
                String attId = attribute.get("attribute_id");
                if (attCode.equals("country_id")) {
                    countryNameId = attId;

                }
                if (attCode.equals("postcode")) {
                    postcodeNameId = attId;
                }
            }
        }

        System.out.println("customer info");
        for (Map<String, String> customerRec : tableMap.get("customer_entity")) {
            String customerId = customerRec.get("entity_id");
            String email = customerRec.get("email");
            Name customer = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, email, allCustomersName, true, emailLanguage);
            azquoCustomersFound.put(customerId, customer);
            //customer.setAttributeWillBePersisted("email", email);
            String groupId = customerRec.get("group_id");
            if (groupId != null) {
                Name group = customerGroups.get(groupId);
                if (group != null && customer != null) { // customer null if blank?
                    group.addChildWillBePersisted(customer);
                }
            }

        }
        tableMap.remove("customer_entity");

        for (Map<String, String> attributeRow : tableMap.get("customer_entity_varchar")) {
            //only picking the name from all the category attributes, and only choosing store 0 - other stores assumed to be different languages
            String attId = attributeRow.get("attribute_id");
            if (attId.equals(firstNameId) || attId.equals(lastNameId)) {
                String valFound = attributeRow.get("value");
                String attFound;
                if (attId.equals(firstNameId)) {
                    attFound = "FIRST NAME";
                } else {
                    attFound = "LAST NAME";
                }
                String customerId = attributeRow.get("entity_id");
                Name customer = azquoCustomersFound.get(customerId);
                if (customer != null) {
                    customer.setAttributeWillBePersisted(attFound, valFound);
                    String firstName = customer.getAttribute("FIRST NAME");
                    String lastName = customer.getAttribute("LAST NAME");
                    String customerName;
                    if (firstName != null) {
                        if (lastName != null) {
                            customerName = firstName + " " + lastName;
                        } else {
                            customerName = firstName;
                        }
                    } else {
                        customerName = lastName;
                    }
                    customer.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, customerName);
                }
            }
        }

        if (tableMap.get("customer_address_entity") != null) {
            Map<String, Name> addressMap = HashObjObjMaps.newMutableMap();
            for (Map<String, String> addressRec : tableMap.get("customer_address_entity")) {
                Name customer = azquoCustomersFound.get(addressRec.get("parent_id"));
                if (customer == null) {
                    customer = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Customer " + addressRec.get("parent_id"), allCustomersName, true, emailLanguage);
                    azquoCustomersFound.put(addressRec.get("parent_id"), customer);
                }
                addressMap.put(addressRec.get("entity_id"), customer);
            }
            tableMap.remove("customer_address_entity");

            for (Map<String, String> attributeRow : tableMap.get("customer_address_entity_varchar")) {
                String attId = attributeRow.get("attribute_id");
                if (attId.equals(countryNameId) || attId.equals(postcodeNameId)) {
                    String value = attributeRow.get("value");
                    String addressId = attributeRow.get("entity_id");
                    Name customer = addressMap.get(addressId);
                    if (attId.equals(countryNameId) && !value.isEmpty()) { // apparently country can be blank!
                        Name country = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, value, allCountriesName, true, emailLanguage);
                        country.addChildWillBePersisted(customer);
                    } else {
                        customer.setAttributeWillBePersisted("postcode", value);
                    }

                }
            }
            tableMap.remove("customer_address_entity_varchar");
            System.out.println("customer info done");
        }

        Map<String, Name> azquoOrdersFound = HashObjObjMaps.newMutableMap();

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
        if (tableMap.get("sales_flat_order_item") == null) {
            if (azquoMemoryDBConnection.getAzquoMemoryDB() != null) {
                azquoMemoryDBConnection.persist();
            }
            return;
        }
        List<String> orderLanguage = new ArrayList<>();
        orderLanguage.add("MagentoOrderID");
        String bundleLine = "";
        for (Map<String, String> salesRow : tableMap.get("sales_flat_order_item")) {
            double discount = 0.0;
            String parentItemId = salesRow.get("parent_item_id");
            String itemId = salesRow.get("item_id");

            if (bundleLine.length() > 0 && (!parentItemId.equals(configLine)) && !parentItemId.equals(bundleLine)) {
                calcBundle(azquoMemoryDBConnection, bundleTotal, bundleItems, priceName, taxName);
                bundleItems = new ArrayList<>();
                bundleLine = "";
            }
            String productId = salesRow.get("product_id");
            Name productName = azquoProductsFound.get(productId);
            if (productName == null) {
                //not on the product list!!  in the demo database there was a giftcard in the sales that was not in the product list
                productName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + productId, allSKUs, true, skuLanguage);
                allProducts.addChildWillBePersisted(productName);
                azquoProductsFound.put(productId, productName);
                //productName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME,salesRow.get("product_type"));
            }
            long thisCycleMarker = System.currentTimeMillis();
            if (configLine == null) {
                price = 0.0;
                qty = 0.0;
                //                productId = salesRow.get("product_id");
                try {
                    price = Double.parseDouble(salesRow.get("base_row_invoiced"));//not sure which figure to take - base_row_invoiced or base_row_total
                    if (price == 0.0) {
                        price = Double.parseDouble(salesRow.get("base_row_total"));
                    }
                    double qtyOrdered = Double.parseDouble(salesRow.get("qty_ordered"));
                    qtyCanceled = Double.parseDouble(salesRow.get("qty_canceled"));
                    weight = Double.parseDouble(salesRow.get("weight"));
                    tax = Double.parseDouble(salesRow.get("base_tax_amount"));
                    qty = qtyOrdered - qtyCanceled;
                    discount = Double.parseDouble(salesRow.get("base_discount_invoiced"));
                    if (qty > 1) {
                        weight *= qty;//store total weight
                    }
                } catch (Exception e) {
                    //ignore the line
                }
                String productType = salesRow.get("product_type");
                if (productType.equals("configurable") || productType.equals("bundle")) {
                    if (productType.equals("bundle")) {
                        bundleTotal.itemName = productName;
                        bundleTotal.price = price;
                        bundleTotal.tax = tax;
                        bundleLine = itemId;
                    } else {
                        configLine = itemId;
                    }
                }
                thisCycleMarker = System.currentTimeMillis();
            } else {
//                productId = salesRow.get("product_id");
                if (!configLine.equals(parentItemId)) {
                    System.out.println("problem in importing sales items - config item " + configLine + " does not have a simple item associated");
                    qty = 0;
                }
                configLine = null;
            }
            part1 += (thisCycleMarker - System.currentTimeMillis());
            thisCycleMarker = System.currentTimeMillis();
            if (configLine == null && !itemId.equals(bundleLine)) {
                //not sure about discounts on bundles! - they may be ignored
                //store the values.   Qty and price have attributes order, product.  order is in all orders, and in the relevant date
                String orderNo = "Order " + salesRow.get("order_id");
                Name orderName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderNo, allOrdersName, true, orderLanguage);
                part3 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                azquoOrdersFound.put(orderNo, orderName);
                //adding 'Item ' to the item number so as not to confuse with order number for the developer - the system should be happy without it.
                Name orderItemName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Item " + salesRow.get("item_id"), orderName, true, orderLanguage);
                part4 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();

                part5 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                String orderDate = salesRow.get("created_at").substring(0, 10);
                String orderTime = salesRow.get("created_at").substring(11, 13);
                int orderHour;
                try {
                    orderHour = Integer.parseInt(orderTime);
                    if (orderHour < 12) {
                        orderTime = orderHour + " AM";
                    } else {
                        if (orderHour == 12) {
                            orderHour = 24;
                        }
                        orderTime = (orderHour - 12) + " PM";
                    }
                } catch (Exception e) {
                    //leave orderTime as is
                }
                part51 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                Name dateName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderDate, allDates, false, dateLanguage);
                Name hourName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderTime, allHours, true, dateLanguage);
                part52 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                dateName.addChildWillBePersisted(orderName);
                hourName.addChildWillBePersisted(orderName);
                productName.addChildWillBePersisted(orderItemName);
                part53 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                //namesForValue.add(productName);
                //NEW STORAGE METHOD  - PRICE + QUANTITY ATTRIBUTES OF THE ORDER ITEM
                //orderItemName.setAttributeWillBePersisted("price", price+"");
                //orderItemName.setAttributeWillBePersisted("quantity",qty + "");
                //....................END OF NEW STORAGE METHOD - LINE BELOW TO BE DELETED
                Set<Name> namesForValue = HashObjSets.newMutableSet();
                if (bundleLine.length() == 0) {
                    namesForValue.add(orderItemName);
                    namesForValue.add(priceName);
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, price + "", namesForValue);
                    namesForValue = HashObjSets.newMutableSet();
                    namesForValue.add(orderItemName);
                    namesForValue.add(taxName);
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, tax + "", namesForValue);
                    namesForValue = HashObjSets.newMutableSet();
                } else {
                    SaleItem saleItem = new SaleItem();
                    saleItem.itemName = orderItemName;
                    saleItem.price = price;
                    saleItem.tax = tax;
                    saleItem.qty = qty;
                    bundleItems.add(saleItem);
                }
                if (qty == 0) qty = 1;//put in by WFC when Auree quantities turned out to be 0
                namesForValue.add(orderItemName);
                namesForValue.add(qtyName);
                ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qty + "", namesForValue);
                namesForValue = HashObjSets.newMutableSet();
                namesForValue.add(orderItemName);
                namesForValue.add(weightName);
                ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, weight + "", namesForValue);
                if (discount > 0.0) {
                    namesForValue = HashObjSets.newMutableSet();
                    namesForValue.add(orderItemName);
                    namesForValue.add(discountName);
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, discount + "", namesForValue);

                }
                if (qtyCanceled > 0) {
                    namesForValue = HashObjSets.newMutableSet();
                    namesForValue.add(orderItemName);
                    namesForValue.add(canceledName);
                    ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qtyCanceled + "", namesForValue);
                }
                part8 += (thisCycleMarker - System.currentTimeMillis());
                //thisCycleMarker = System.currentTimeMillis();
            }

            counter++;
            if (counter == 100000) {
                System.out.println("100000 lines sales flat order item " + (System.currentTimeMillis() - marker));
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
                System.out.println("name spreadsheet time track" + NameService.getTimeTrackMapForConnection(azquoMemoryDBConnection));
                //System.out.println("value spreadsheet time track" + ValueService.getTimeTrackMapForConnection(azquoMemoryDBConnection));
                logMemUseage();
            }
        }
        if (bundleLine.length() > 0) {
            calcBundle(azquoMemoryDBConnection, bundleTotal, bundleItems, priceName, taxName);
        }
        // new thing here - this used to just run through sales_flat_order_address finding the delivery country. Now we need it for getting infor for non logged in customers, hence run through mapping before doing the orders
        Map<String, Map<String, String>> salesFlatOrderShippingAddressByOrderId = HashObjObjMaps.newMutableMap();
        if (tableMap.get("sales_flat_order_address") != null) {
            for (Map<String, String> orderRow : tableMap.get("sales_flat_order_address")) {
                if (orderRow.get("address_type").equals("shipping")) { // only want the shipping one - I assume there's only one sipping address per order
                    salesFlatOrderShippingAddressByOrderId.put(orderRow.get("parent_id"), orderRow); // I believe the parent_id is the order id
                    String fullName = getFullName(orderRow);
                    Name orderName = azquoOrdersFound.get("Order " + orderRow.get("parent_id"));
                    if (orderName != null) {
                        orderName.setAttributeWillBePersisted("delivery name", fullName);
                        orderName.setAttributeWillBePersisted("postcode", get(orderRow, "postcode"));
                        orderName.setAttributeWillBePersisted("street", get(orderRow, "street"));
                        orderName.setAttributeWillBePersisted("city", get(orderRow, "city"));
                    }


                }
            }
        }

        counter = 0;
        Name notLoggedIn = null;
        System.out.print("Orders and shipping ");
        List<String> currencyLanguage = new ArrayList<>();
        currencyLanguage.add("currency");
        for (Map<String, String> orderRow : tableMap.get("sales_flat_order")) {
            //only importing the IDs at present
            Name orderName = azquoOrdersFound.get("Order " + orderRow.get("entity_id"));
            if (orderName != null) {
                Map<String, String> salesFlatOrderShippingAddress = salesFlatOrderShippingAddressByOrderId.get(orderRow.get("entity_id"));
                // what was being done below
                if (salesFlatOrderShippingAddress != null) {
                    orderName.setAttributeWillBePersisted("DELIVERY COUNTRY", salesFlatOrderShippingAddress.get("country_id"));
                }
                Name store = storeMap.get(orderRow.get("store_id"));
                if (store != null) {
                    store.addChildWillBePersisted(orderName);
                }
                String customer = orderRow.get("customer_id");
                String magentoCustomer;
                Name customerName;
                if (customer == null || customer.length() == 0) {
                    // ok new logic, we need to make a customer from the extra info in sales_flat_order_address if it wasn't there by customer account
                    // note when they don't have an email address there will be no customer, I could do this from name but this might clash
                    if (salesFlatOrderShippingAddress != null) { // so we can make the customer name
                        // switch languages temporarily to email - mimic the code when there's a customer account
                        String email = salesFlatOrderShippingAddress.get("email");
                        if (email == null || email.length() == 0) {
                            email = "noemail" + salesFlatOrderShippingAddress.get("firstname") + salesFlatOrderShippingAddress.get("lastname") + salesFlatOrderShippingAddress.get("postcode"); // then make one up
                        }
                        customerName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, email, allCustomersName, true, Collections.singletonList("email"));
                        String fullName = getFullName(salesFlatOrderShippingAddress);
                        customerName.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, fullName);
                        customerName.setAttributeWillBePersisted("POSTCODE", salesFlatOrderShippingAddress.get("postcode"));
                    } else {
                        if (notLoggedIn == null) {
                            magentoCustomer = "NOT LOGGED IN";
                            //nb this next instruction seems to take a long time - hence storing 'notLoggedIn'
                            notLoggedIn = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, magentoCustomer, allGroupsName, true, emailLanguage);
                            allCountriesName.addChildWillBePersisted(notLoggedIn);
                        }
                        customerName = notLoggedIn;
                    }
                } else {
                    customerName = azquoCustomersFound.get(customer);
                    if (customerName == null) {
                        magentoCustomer = "Customer " + customer;
                        customerName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, magentoCustomer, allCustomersName, true, emailLanguage);
                    }
                }
                customerName.addChildWillBePersisted(orderName);
                String currency = orderRow.get("base_currency_code");
                Name currencyName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, currency, allCurrenciesName, true, currencyLanguage);
                currencyName.addChildWillBePersisted(orderName);
                String orderNo = orderRow.get("increment_id");
                orderName.setAttributeWillBePersisted("order no", orderNo);
                String shipping = orderRow.get("shipping_amount");
                if (shipping != null && shipping.length() > 0) {
                    double dShipping = 0.0;
                    try {
                        dShipping = Double.parseDouble(shipping);
                    } catch (Exception ignored) {
                    }
                    if (dShipping != 0) {
                        Set<Name> namesForValue = HashObjSets.newMutableSet();
                        namesForValue.add(orderName);
                        namesForValue.add(shippingName);
                        ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, shipping, namesForValue);
                    }
                }
                String shippingTax = orderRow.get("shipping_tax_amount");
                if (shippingTax != null && shippingTax.length() > 0) {
                    double dShippingTax = 0.0;
                    try {
                        dShippingTax = Double.parseDouble(shippingTax);
                    } catch (Exception ignored) {
                    }
                    if (dShippingTax != 0) {
                        Set<Name> namesForValue = HashObjSets.newMutableSet();
                        namesForValue.add(orderName);
                        namesForValue.add(shippingTaxName);
                        ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, shippingTax, namesForValue);
                    }
                }
                String orderStatus = orderRow.get("status");
                if (orderStatus != null && orderStatus.length() > 0) {
                    Name statusName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderStatus, orderStatusName, true, defaultLanguage);
                    statusName.addChildWillBePersisted(orderName);
                }

                counter++;
                if (counter % 1000 == 0) {
                    System.out.print(".");
                }
            }
        }

        if (tableMap.get("sales_flat_invoice_grid") != null) {
            List<String> orderIdLanguage = Collections.singletonList("order no");
            for (Map<String, String> invoiceRow : tableMap.get("sales_flat_invoice_grid")) {
                String orderIncrementId = invoiceRow.get("order_increment_id");
                String incrementId = invoiceRow.get("increment_id");
                if (orderIncrementId != null && incrementId != null && orderIncrementId.length() > 0 && incrementId.length() > 0) {
                    Name order = NameService.findByName(azquoMemoryDBConnection, orderIncrementId, orderIdLanguage);
                    if (order != null) {
                        order.setAttributeWillBePersisted("invoice no", incrementId);
                    }
                }
            }
        }


        System.out.println("");
        System.out.println("shipments");

        if (tableMap.get("sales_flat_shipment") != null) {
            for (Map<String, String> orderRow : tableMap.get("sales_flat_shipment")) {
                //only importing the IDs at present
                Name orderName = azquoOrdersFound.get("Order " + orderRow.get("order_id"));
                String shippingDate = orderRow.get("created_at");
                String packages = orderRow.get("total_qty");
                if (orderName != null && shippingDate != null && packages != null) {
                    shippingDate = shippingDate.substring(0, 10);
                    orderName.setAttributeWillBePersisted("Shipping date", shippingDate);
                }
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ordersName.setAttributeWillBePersisted(LATEST_UPDATE + " " + remoteAddress, sdf.format(new Date()));
        if (azquoMemoryDBConnection.getAzquoMemoryDB() != null) {
            azquoMemoryDBConnection.persistInBackground();// aim to return to them quickly, this is whre we get into multi threading . . .
        }
    }

    private static String get(Map<String, String> map, String key) {
        String toReturn = map.get(key);
        if (toReturn == null) return "none";
        return toReturn;
    }

    private static String getFullName(Map<String, String> salesFlatOrderShippingAddress) {

        String firstName = salesFlatOrderShippingAddress.get("firstname");
        String lastName = salesFlatOrderShippingAddress.get("lastname");
        String fullName;
        if (firstName != null) {
            if (lastName != null) {
                fullName = firstName + " " + lastName;
            } else {
                fullName = firstName;
            }
        } else {
            fullName = lastName;
        }
        return fullName;
    }

    private static void readProductAttributes(String tableName, Map<String, List<Map<String, String>>> tableMap, Map<String, Name> azquoProductsFound, Map<String, String> attIds, String productNameId) throws Exception {
        for (Map<String, String> attributeRow : tableMap.get(tableName)) {
            if (attributeRow.get("store_id").equals("0")) {
                String attId = attributeRow.get("attribute_id");
                String attVal = attributeRow.get("value");
                Name productName = azquoProductsFound.get(attributeRow.get("entity_id"));
                //Name magentoName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + attributeRow.get("entity_id"), topProduct, true,languages);
                if (productName == null) {
                    System.out.println("Entity id linked in catalog_product_entity_varchar that doesn't exist in catalog_product_entity : " + attributeRow.get("entity_id"));
                } else {
                    if (attId.equals(productNameId)) {
                        productName.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, attVal);
                    }
                    for (String att : productAtts) {
                        String thisAttId = attIds.get(att);
                        if (thisAttId != null && thisAttId.equals(attId)) {
                            productName.setAttributeWillBePersisted(att.replace("_", " ").toUpperCase(), attVal);
                        }
                    }
                }
            }
        }
        tableMap.remove(tableName);
    }

    private static void calcBundle(AzquoMemoryDBConnection azquoMemoryDBConnection, SaleItem bundleTotal, List<SaleItem> bundleItems, Name priceName, Name taxName) throws Exception {
        /* Magento does not put prices into bundled items (though this routine checks just in case)
        so this routine looks up the bundled price in the bundle table.
        If it fails to find a bundled price, it totals all the 'full' prices for the unaccounted lines, then apportions the rest of the bundle price and tax accordingly
          */
        double totalOrigPrice = 0.0;
        int unknownCount = 0;
        double priceRemaining = bundleTotal.price;
        double taxRemaining = bundleTotal.tax;

        Double taxAdjustment = priceRemaining / (priceRemaining + taxRemaining);

/*        List<String> productLanguage = new ArrayList<String>();
        productLanguage.add("MagentoProductId");*/
        String bundlePrices = bundleTotal.itemName.getAttribute("BUNDLEPRICES");
        if (bundlePrices == null) {
            bundlePrices = "";
        }
        String[] skuPrices = bundlePrices.split(",");

        for (SaleItem saleItem : bundleItems) {
            if (saleItem.price == 0) {
                for (String skuPrice : skuPrices) {
                    String sKU = saleItem.itemName.getAttribute("SKU");
                    if (sKU != null && skuPrice.startsWith(sKU)) {
                        String price = skuPrice.substring(sKU.length() + 1);
                        try {
                            saleItem.price = Double.parseDouble(price) * saleItem.qty * taxAdjustment;
                        } catch (Exception ignored) {
                        }
                        break;
                    }
                }
                if (saleItem.price > 0) {
                    saleItem.tax = bundleTotal.tax * saleItem.price / bundleTotal.price;
                }
            }

            if (saleItem.price > 0.0) {
                priceRemaining -= saleItem.price;
                taxRemaining -= saleItem.tax;
            } else {
                saleItem.origPrice = 1.0;
                String itemPrice = saleItem.itemName.getAttribute("PRICE");
                if (itemPrice != null) {
                    try {
                        saleItem.origPrice = Double.parseDouble(itemPrice);
                    } catch (Exception ignored) {
                    }
                }
                totalOrigPrice += saleItem.origPrice * saleItem.qty;
                unknownCount++;

            }
        }
        Double unallocatedPriceRemaining = priceRemaining;
        for (SaleItem saleItem : bundleItems) {
            if (saleItem.price == 0.0) {
                if (--unknownCount == 0) {
                    saleItem.price = priceRemaining;
                    saleItem.tax = taxRemaining;
                } else {
                    saleItem.price = saleItem.origPrice * unallocatedPriceRemaining / totalOrigPrice * saleItem.qty;
                    saleItem.tax = saleItem.price * bundleTotal.tax / bundleTotal.price;
                }
                priceRemaining -= saleItem.price;
                taxRemaining -= saleItem.tax;
            }
        }
        if (priceRemaining != 0) {
            double ratio = bundleTotal.price / (bundleTotal.price - priceRemaining);
            for (SaleItem saleItem : bundleItems) {
                saleItem.price = saleItem.price * ratio;
                saleItem.tax = saleItem.tax * ratio;
            }
        }
        for (SaleItem saleItem : bundleItems) {
            Set<Name> namesForValue = HashObjSets.newMutableSet();
            namesForValue.add(saleItem.itemName);
            namesForValue.add(priceName);
            ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, df.format(saleItem.price), namesForValue);
            namesForValue = HashObjSets.newMutableSet();
            namesForValue.add(saleItem.itemName);
            namesForValue.add(taxName);
            ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, df.format(saleItem.tax), namesForValue);
        }
    }

    private static String defaultData() {
        //version number followed by required data.  $starttime to be replaced by latest update
        return "1.0\n" +
                "'*','core_store_group','','group_id'\n" +
                "'*','core_store','','store_id'\n" +
                "'*','catalog_category_entity','','entity_id'\n" +
                "'*','catalog_category_product','', 'product_id'\n" +
                "'*','catalog_product_entity','','entity_id'\n" +
                "'*','catalog_product_bundle_selection','','selection_id'\n" +
                "'*','catalog_product_super_link','','product_id'\n" +
                "'*','catalog_product_super_attribute','','product_super_attribute_id'\n" +
                "'*','eav_attribute','', 'attribute_id'\n" +
                "'*','eav_attribute_label','','attribute_label_id'\n" +
                "'*','eav_attribute_option_value','','value_id'\n" +
                "'*','eav_entity_type','','entity_type_id'\n" +
                "'item_id,order_id,parent_item_id,created_at,product_id,weight,product_type,qty_ordered,qty_canceled,base_discount_invoiced, base_tax_amount, base_row_invoiced, base_row_total','sales_flat_order_item', '$starttime','item_id'\n" +
                "'entity_id, store_id, status, customer_id, base_currency_code, increment_id, shipping_amount,shipping_tax_amount','sales_flat_order',  '$starttime', 'entity_id'\n" +
                "'entity_id, parent_id, address_type, country_id, email, firstname, lastname, street, city, postcode','sales_flat_order_address',  '', 'entity_id'\n" + // email firstname lastname postcode extras that will be required if users are shopping in Magento without making an account
                "'entity_id, increment_id, order_increment_id','sales_flat_invoice_grid',  '$starttime', 'entity_id'\n" +
                "'entity_id, email, group_id','customer_entity',  '$starttime', 'entity_id'\n" +
                "'*','customer_group','', 'customer_group_id'\n" +
                "'entity_id, parent_id','customer_address_entity', '$starttime', 'entity_id'\n" +
                "'entity_id, order_id, created_at,total_qty','sales_flat_shipment','$starttime','entity_id'\n" +
                "'item_id,product_id,qty','cataloginventory_stock_item','','item_id'\n";
    }
}
