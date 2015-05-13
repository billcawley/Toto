package com.azquo.app.magento.service;

import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.memorydb.service.ValueService;
import com.azquo.spreadsheet.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by cawley on 07/08/14.
 * <p/>
 * This class is used to parse files delivered by the Magento Plugin. If it ever needed to be really heavy duty one could use Spring Batch I suppose but I think
 * that would be overkill.
 */
public final class DataLoadService {

    private static final String[] productAtts = {"url_path","meta_description","country_of_manufacture", "meta_title","price","weight", "ship_height","ship_width", "ship_depth", "cost"};

    public static void logMemUseage() {
        Runtime runtime = Runtime.getRuntime();
        int mb = 1024 * 1024;
        System.out.println("##### Heap utilization statistics [MB] #####");
        System.out.println("Used Memory:" + (runtime.totalMemory() - runtime.freeMemory()) / mb);
        System.out.println("Free Memory:" + runtime.freeMemory() / mb);
        System.out.println("Total Memory:" + runtime.totalMemory() / mb);
        System.out.println("Max Memory:" + runtime.maxMemory() / mb);
    }

    @Autowired
    SpreadsheetService spreadsheetService;

    @Autowired
    ImportService importService;

    private final static String LATEST_UPDATE = "Latest update";
    private final static String REQUIRED_TABLES = "required tables";

    @Autowired
    private NameService nameService;

    @Autowired
    private ValueService valueService;

    private static class SaleItem {
        public Name itemName;
        public double price;
        public double tax;
        public double origPrice;
        public double qty;

        public SaleItem() {
            itemName = null;
            price = 0.0;
            tax = 0.0;
            origPrice = 0.0;
            qty = 0.0;

        }
    }

    DecimalFormat df = new DecimalFormat("#.00");
/*    final Map<Integer, Name> products = new HashMap<Integer, Name>();
    final Map<Integer, Name> categories = new HashMap<Integer, Name>();
    //final Map<Integer, MagentoOrderLineItem> orderLineItems = new HashMap<Integer, MagentoOrderLineItem>();
    final Map<String, String> optionValueLookup = new HashMap<String, String>();*/

    public String findLastUpdate(AzquoMemoryDBConnection azquoMemoryDBConnection, String remoteAddress) throws Exception {
        Name orderName = nameService.findByName(azquoMemoryDBConnection, "order");
        if (orderName == null) {
            return null;
        }
        String lastUpdate =  orderName.getAttribute(LATEST_UPDATE + " " + remoteAddress);
        if (lastUpdate==null){
            lastUpdate = orderName.getAttribute(LATEST_UPDATE);
        }
        return lastUpdate;
    }


    public String findRequiredTables(AzquoMemoryDBConnection azquoMemoryDBConnection, String remoteAddress) throws Exception {
        String requiredTables = defaultData().replace("$starttime", "");
        if (nameService.findByName(azquoMemoryDBConnection, "all years") == null) {
            String magentoSetupFile = spreadsheetService.getHomeDir() + "/databases/Magen/setup/magentosetup.xlsx";
            InputStream uploadFile = new FileInputStream(magentoSetupFile);
            String fileName = "magentosetup.xlsx";
            importService.importTheFile(azquoMemoryDBConnection, fileName, uploadFile);
        }

        String date = "never";
        String lastUpdate = findLastUpdate(azquoMemoryDBConnection, remoteAddress);
        Name order = nameService.findByName(azquoMemoryDBConnection, "order");
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

    public void loadData(AzquoMemoryDBConnection azquoMemoryDBConnection, InputStream data, String remoteAddress) throws Exception {
        loadData(azquoMemoryDBConnection, data != null ? new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8)) : null, remoteAddress);
    }

    // todo : since this is a single thread maybe use koloboke maps? I guess if it's going slowly. Also NIO options? We are on Java 8 now . . .

    public void loadData(AzquoMemoryDBConnection azquoMemoryDBConnection, BufferedReader br, String remoteAddress) throws Exception {
        Map<String, List<Map<String, String>>> tableMap = new HashMap<String, List<Map<String, String>>>();
        if (br == null) {
            br = new BufferedReader(new FileReader("/home/bill/Sear/magento/testdata_dump.txt"));
        }
        long marker = System.currentTimeMillis();
        String line;
        List<Map<String, String>> currentTableDataMap = null;
        String[] currentColumnNames = null;
        int count = 0;
        System.gc();
        while ((line = br.readLine()) != null) {
            if (line.startsWith("||||TABLE:")) {
                count = 0;
                currentColumnNames = null;
                String tableName = line.substring(10);
                System.out.println();
                System.out.print("Initial load of : " + tableName);
                // I'm not going to support tables being loaded in two chunks I see no point. THis would overwrite data if a table were referenced twice
                currentTableDataMap = new ArrayList<Map<String, String>>(); // and I know this repeats keys for each row, the goal here is ease of use for importing, not efficiency
                /* a further comment on efficiency :
                this kind of loading means over 10x the size in memory vs the file size but cutting this down is a bit pointless as it will take about this much
                in the memorydb. If we're really tight getting rid of the maps per line and leaving it as an arraylist might help but the existing removal of tables as they're used
                probably goes a long way to stopping memory spikes.*/
                tableMap.put(tableName, currentTableDataMap);
            } else { // data, is it the first one?
                if (currentColumnNames == null) {
                    currentColumnNames = line.split("\t", -1);
                } else {
                    count++;
                    String[] lineValues = line.split("\t", -1);
                    Map<String, String> dataRowMap = new HashMap<String, String>();
                    for (int i = 0; i < lineValues.length; i++) {
                        String val = lineValues[i].intern();
                        // //zap the time element of any dates imported
                        // Date date = valueService.interpretDate(val);
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
        // todo - address string literals here - not a good idea I don't think.
        Name topProduct = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "product", null, false);
        Name productAttributesName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product Attributes", topProduct, false);
        Name productCategories = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product categories", topProduct, false);
        Name allSKUs = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All SKUs", topProduct, false);
        Name allProducts = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All Products", topProduct, false);
        Name orphans = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "SKU Orphans", topProduct, false);
        Name allCategories = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All Categories", topProduct, false);
        Name uncategorisedProducts = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Uncategorised Products", topProduct, false);

        //find out the name attribute numbers ....
        String categoryEntityId = null;
        String productEntityId = null;
        String customerEntityId = null;
        String addressEntityId = null;
        String firstNameId = null;
        String lastNameId = null;

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

        Map<String, String> attIds = new HashMap<String, String>();

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
                for (String att:productAtts){
                    if (attCode.equals(att)){
                        attIds.put(att,attId);
                    }
                }

            }
        }
        //now start the real work - categories first
        Map<String, Name> azquoCategoriesFound = new HashMap<String, Name>();
        Map<String, Name> azquoProductsFound = new HashMap<String, Name>();
        Map<String, Name> azquoCustomersFound = new HashMap<String, Name>();
         List<String> defaultLanguage = new ArrayList<String>();
        defaultLanguage.add(Name.DEFAULT_DISPLAY_NAME);
        Map<String,String> categoryNames = new HashMap<String, String>();
        //name the categories
         for (Map<String, String> attributeRow : tableMap.get("catalog_category_entity_varchar")) { // should (!) have us looking in teh right place
            //only picking the name from all the category attributes
            if (attributeRow.get("attribute_id").equals(categoryNameId)) {
                categoryNames.put(attributeRow.get("entity_id"), attributeRow.get("value"));
            }
        }
        tableMap.remove("catalog_category_entity_varchar");
        for (Map<String, String> entityTypeRecord : tableMap.get("catalog_category_entity")) {
            //invert the path for uploading to Azquo  -  1/2/3 becomes `3`,`2`,`1` becomes '`bottom`,`higher`,`top`
            StringTokenizer pathBits = new StringTokenizer(entityTypeRecord.get("path"), "/");
            String path = "";
            while (pathBits.hasMoreTokens()) {
                String catNo = pathBits.nextToken();
                String catName = categoryNames.get(catNo);
                if (catName != null){
                    path = "`" + catName + "`," + path;
                }else{
                    System.out.println("we have a category with no name!");
                    path = "`" + catNo + "`," + path;
                }
            }
             //TODO consider what might happen if importing categories from two different databases - don't do it!
            Name categoryName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, path.substring(0, path.length() - 1), productCategories, true, defaultLanguage);

            azquoCategoriesFound.put(entityTypeRecord.get("entity_id"), categoryName);
            allCategories.addChildWillBePersisted(categoryName);
        }
        tableMap.remove("catalog_category_entity");
        List<String> languages = new ArrayList<String>();

        languages.add("SKU");
        List<String> productLanguages = new ArrayList<String>(languages);
        for (Map<String, String> entityRow : tableMap.get("catalog_product_entity")) {
            Name magentoName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, entityRow.get("sku"), allSKUs, true, languages);
            if (magentoName != null){
                allProducts.addChildWillBePersisted(magentoName);
                if (!magentoName.findAllParents().contains(productCategories) && !magentoName.findAllParents().contains(uncategorisedProducts)) {
                    uncategorisedProducts.addChildWillBePersisted(magentoName);
                }
                azquoProductsFound.put(entityRow.get("entity_id"), magentoName);
            } else {
                System.out.println("magentoName null on " + entityRow);
            }
        }
        tableMap.remove("catalog_product_entity");
        readProductAttributes("catalog_product_entity_varchar", tableMap, azquoProductsFound,attIds,productNameId);
        readProductAttributes("catalog_product_entity_decimal", tableMap, azquoProductsFound,attIds,productNameId);
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
        tableMap.remove("catalog_product_super_attribute");
        //name the option values
        Map<String, String> optionValues = new HashMap<String, String>();
        for (Map<String, String> optionVal : tableMap.get("eav_attribute_option_value")) {
            String storeId = optionVal.get("store_id");
            if (storeId == null || (storeId != null && storeId.equals("0"))) {
                optionValues.put(optionVal.get("option_id"), optionVal.get("value"));
            }
        }
        tableMap.remove("eav_attribute_option_value");
        //.... and allocate them!
        for (Map<String, String> attVals : tableMap.get("catalog_product_entity_int")) {
            String attVal = attVals.get("attribute_id");
            if (attributes.contains(attVal)) {
                Name magentoName = azquoProductsFound.get(attVals.get("entity_id"));
                if (magentoName == null) {
                    System.out.println("entity_id linked in catalog_product_entity_int that doesn't exist in catalog_product_entity : " + attVals.get("entity_id"));
                } else {
                    Name magentoProductCategory = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, attributeNames.get(attVal), productAttributesName, true, languages);
                    String val = attVals.get("value");
                    if (optionValues.get(val) != null) {
                        //note - this is NOT a product id, so don't use the product id to find it!
                        Name magentoOptionName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, optionValues.get(val), magentoProductCategory, true, null);
                        if (!magentoName.findAllParents().contains(magentoOptionName)) {
                            magentoOptionName.addChildWillBePersisted(magentoName);
                            if (allProducts.getChildren().contains(magentoName)) {
                                orphans.addChildWillBePersisted(magentoName);
                            }
                        }
                    } else {
                        System.out.println("found an option value " + val + " for " + magentoProductCategory.getDefaultDisplayName());
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
                    bundleName.setAttributeWillBePersisted("bundleprices", bundlePrices);
                }
                bundlePrices = "";
                currentParent = parentId;
                bundleName = azquoProductsFound.get(parentId);
                if (bundleName==null){
                    //this should not happen!
                    bundleName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + parentId, allSKUs, true, languages);
                    azquoProductsFound.put(parentId,bundleName);
                    allProducts.addChildWillBePersisted(bundleName);


                }

            }
            String childId = attVals.get("product_id");
            Name childName = azquoProductsFound.get(childId);
            if (childName == null){
                //this should not happen!
                childName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + childId, allSKUs, true, languages);
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
        Name entities = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Entities", null, false);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Name date = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "date", null, false);
        Name allDates = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All dates", date, false);

        if (tableMap.get("cataloginventory_stock_item") != null) {
            Name inStockName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "In Stock", entities, true);
            Name today = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, dateFormat.format(new Date()), date, false);
            Name stockDates = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Stock dates", date, true);
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
                        Set<Name> namesForValue = new HashSet<Name>();
                        namesForValue.add(today);
                        namesForValue.add(inStockName);
                        namesForValue.add(productName);
                        valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qty, namesForValue);
                    }
                }
            }
        }

        System.out.println("time to do initial non taxing bit " + (System.currentTimeMillis() - marker));
        marker = System.currentTimeMillis();
        SaleItem bundleTotal = new SaleItem();
        List<SaleItem> bundleItems = new ArrayList<SaleItem>();

        double price = 0.0;
        double qty = 0.0;
        double qtyCanceled = 0.0;

        double tax = 0.0;
        double weight = 0.0;
        String configLine = null;
//        String productId = null;
        Name priceName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Price", entities, false);
        Name shippingName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Shipping", entities, false);
        Name qtyName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Quantity", entities, false);
        Name weightName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Weight", entities, false);
        Name canceledName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Canceled quantity", entities, false);
        Name taxName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Tax", entities, false);
        Name ordersName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, "order", null, false);
        Name allOrdersName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All orders", ordersName, false);
        Name allCurrenciesName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All currencies", ordersName, false);
        Name allHours = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All hours, date", null, false);
        final LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>(2);
        peers.put(allOrdersName, true);
        priceName.setPeersWillBePersisted(peers);
        qtyName.setPeersWillBePersisted(peers);
        shippingName.setPeersWillBePersisted(peers);
        languages.clear();
        languages.add(Name.DEFAULT_DISPLAY_NAME);

        Name customersName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "customer", null, false, languages);
        Name allCustomersName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection, "All customers", customersName, false, languages);

        Name allCountriesName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All countries", customersName, false, languages);
        Name allGroupsName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All customer groups", customersName, false, languages);
        languages.clear();
        languages.add("email");

        System.out.println("customer groups");
        Map<String, Name> customerGroups = new HashMap<String, Name>();
        if (tableMap.get("customer_group") != null) {
            for (Map<String, String> group : tableMap.get("customer_group")) {
                String groupId = group.get("customer_group_id");
                String groupString = group.get("customer_group_code");
                Name groupName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, groupString, allGroupsName, true, languages);
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
            Name customer = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, email, allCustomersName, true, languages);
            azquoCustomersFound.put(customerId, customer);
            //customer.setAttributeWillBePersisted("email", email);
            String groupId = customerRec.get("group_id");
            if (groupId != null) {
                Name group = customerGroups.get(groupId);
                if (group != null) {
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
                    attFound = "First name";
                } else {
                    attFound = "Last name";
                }
                String customerId = attributeRow.get("entity_id");
                Name customer = azquoCustomersFound.get(customerId);
                if (customer != null) {
                    customer.setAttributeWillBePersisted(attFound, valFound);
                    String firstName = customer.getAttribute("First name");
                    String lastName = customer.getAttribute("Last name");
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
                    customer.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, customerName);
                }
            }
        }

        if (tableMap.get("customer_address_entity") != null) {
            Map<String, Name> addressMap = new HashMap<String, Name>();
            for (Map<String, String> addressRec : tableMap.get("customer_address_entity")) {
                Name customer = azquoCustomersFound.get(addressRec.get("parent_id"));
                if (customer==null){
                    customer = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Customer " + addressRec.get("parent_id"), allCustomersName, true, languages);
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
                    if (attId.equals(countryNameId)) {
                        Name country = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, value, allCountriesName, true, languages);
                        country.addChildWillBePersisted(customer);
                    } else {
                        customer.setAttributeWillBePersisted("postcode", value);
                    }

                }
            }
            tableMap.remove("customer_address_entity_varchar");
            System.out.println("customer info done");
        }




        Map<String, Name> azquoOrdersFound = new HashMap<String, Name>();

        System.out.println("about to go into sales flat order item " + (System.currentTimeMillis() - marker));
        marker = System.currentTimeMillis();
        long part0a = 0;
        long part0b = 0;
        long part0c = 0;
        long part0d = 0;
        long partoe = 0;
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
            if (azquoMemoryDBConnection.getCurrentDatabase() != null) {
                azquoMemoryDBConnection.persist();
            }
            return;
        }
        languages.clear();
        languages.add("MagentoOrderID");
        String bundleLine = "";
        for (Map<String, String> salesRow : tableMap.get("sales_flat_order_item")) {
            String parentItemId = salesRow.get("parent_item_id");
            String itemId = salesRow.get("item_id");
            long thisCycleMarker = System.currentTimeMillis();
            if (bundleLine.length() > 0 && (!parentItemId.equals(configLine)) && !parentItemId.equals(bundleLine)) {
                calcBundle(azquoMemoryDBConnection, bundleTotal, bundleItems, priceName, taxName);
                bundleItems = new ArrayList<SaleItem>();
                bundleLine = "";

            }
            part0a += (thisCycleMarker - System.currentTimeMillis());
            thisCycleMarker = System.currentTimeMillis();
            String productId = salesRow.get("product_id");
            Name productName = azquoProductsFound.get(productId);
            if (productName == null) {
                //not on the product list!!  in the demo database there was a giftcard in the sales that was not in the product list
                productName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + productId, allSKUs, true, productLanguages);
                allProducts.addChildWillBePersisted(productName);
                azquoProductsFound.put(productId, productName);
                //productName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME,salesRow.get("product_type"));
            }
            part0b += (thisCycleMarker - System.currentTimeMillis());
            thisCycleMarker = System.currentTimeMillis();
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
                    if (qty > 1) {
                        weight *= qty;//store total weight
                    }


                } catch (Exception e) {
                    //ignore the line
                }
                part0c += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
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
                part0d += (thisCycleMarker - System.currentTimeMillis());
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
                //store the values.   Qty and price have attributes order, product.  order is in all orders, and in the relevant date
                String orderNo = "Order " + salesRow.get("order_id");
                Name orderName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderNo, allOrdersName, true, languages);
                part3 += (thisCycleMarker - System.currentTimeMillis());
                thisCycleMarker = System.currentTimeMillis();
                azquoOrdersFound.put(orderNo, orderName);
                //adding 'Item ' to the item number so as not to confuse with order number for the developer - the system should be happy without it.
                Name orderItemName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Item " + salesRow.get("item_id"), orderName, true, languages);
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
                Name dateName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderDate, allDates, false, defaultLanguage);
                Name hourName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderTime, allHours, true, defaultLanguage);
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
                Set<Name> namesForValue = new HashSet<Name>();
                if (bundleLine.length() == 0) {
                    namesForValue.add(orderItemName);
                    namesForValue.add(priceName);
                    valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, price + "", namesForValue);
                    namesForValue = new HashSet<Name>();
                    namesForValue.add(orderItemName);
                    namesForValue.add(taxName);
                    valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, tax + "", namesForValue);
                    namesForValue = new HashSet<Name>();
                } else {
                    SaleItem saleItem = new SaleItem();
                    saleItem.itemName = orderItemName;
                    saleItem.price = price;
                    saleItem.tax = tax;
                    saleItem.qty = qty;
                    bundleItems.add(saleItem);
                }
                namesForValue.add(orderItemName);
                namesForValue.add(qtyName);
                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qty + "", namesForValue);
                namesForValue = new HashSet<Name>();
                namesForValue.add(orderItemName);
                namesForValue.add(weightName);
                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, weight + "", namesForValue);
                if (qtyCanceled > 0){
                    namesForValue = new HashSet<Name>();
                    namesForValue.add(orderItemName);
                    namesForValue.add(canceledName);
                    valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qtyCanceled + "", namesForValue);
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

                System.out.println("name spreadsheet time track" + nameService.getTimeTrackMapForConnection(azquoMemoryDBConnection));
                System.out.println("value spreadsheet time track" + valueService.getTimeTrackMapForConnection(azquoMemoryDBConnection));

                logMemUseage();
            }

        }


        if (bundleLine.length() > 0) {
            calcBundle(azquoMemoryDBConnection, bundleTotal, bundleItems, priceName, taxName);
        }
         counter = 0;
        Name notLoggedIn = null;
        System.out.print("Orders and shipping ");
        for (Map<String, String> orderRow : tableMap.get("sales_flat_order")) {
            //only importing the IDs at present
            Name orderName = azquoOrdersFound.get("Order " + orderRow.get("entity_id"));
            if (orderName != null) {
                String customer = orderRow.get("customer_id");
                String magentoCustomer;
                Name customerName;
                if (customer == null || customer.length() == 0) {
                    if (notLoggedIn == null) {
                        magentoCustomer = "NOT LOGGED IN";
                        //nb this next instruction seems to take a long time - hence storing 'notLoggedIn'
                        notLoggedIn = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, magentoCustomer, allGroupsName, true, languages);
                        allCountriesName.addChildWillBePersisted(notLoggedIn);
                    }
                    customerName = notLoggedIn;

                } else {
                    customerName = azquoCustomersFound.get(customer);
                    if (customerName == null) {
                        magentoCustomer = "Customer " + customer;
                        customerName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, magentoCustomer, allCustomersName, true, languages);
                    }
                }
                customerName.addChildWillBePersisted(orderName);
                String currency = orderRow.get("base_currency_code");
                Name currencyName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, currency, allCurrenciesName, true, languages);
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
                    if (dShipping > 0) {
                        Set<Name> namesForValue = new HashSet<Name>();
                        namesForValue.add(orderName);
                        namesForValue.add(shippingName);
                        valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, shipping, namesForValue);
                    }
                }
                counter++;
                if (counter % 1000 == 0) {
                    System.out.print(".");
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



        languages.clear();
        languages.add(Name.DEFAULT_DISPLAY_NAME);


        Name order = nameService.findByName(azquoMemoryDBConnection, "order", languages);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        order.setAttributeWillBePersisted(LATEST_UPDATE + " " + remoteAddress, sdf.format(new Date()));
        if (azquoMemoryDBConnection.getCurrentDatabase() != null) {
            azquoMemoryDBConnection.persistInBackground();// aim to return to them quickly, this is whre we get into multi threading . . .
        }

    }


    private void readProductAttributes(String tableName,  Map<String, List<Map<String, String>>> tableMap, Map<String,Name>azquoProductsFound, Map<String,String>attIds, String productNameId)throws Exception{
        for (Map<String,String>attributeRow:tableMap.get(tableName)) {
            if (attributeRow.get("store_id").equals("0")) {
                String attId = attributeRow.get("attribute_id");
                String attVal = attributeRow.get("value");
                Name productName = azquoProductsFound.get(attributeRow.get("entity_id"));
                //Name magentoName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product " + attributeRow.get("entity_id"), topProduct, true,languages);
                if (productName == null) {
                    System.out.println("Entity id linked in catalog_product_entity_varchar that doesn't exist in catalog_product_entity : " + attributeRow.get("entity_id"));
                } else {
                    if (attId.equals(productNameId)) {
                        productName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, attVal);
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

    private void calcBundle(AzquoMemoryDBConnection azquoMemoryDBConnection, SaleItem bundleTotal, List<SaleItem> bundleItems, Name priceName, Name taxName) throws Exception {

        /* Magento does not put prices into bundled items (though this routime checks just in case)
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
        String bundlePrices = bundleTotal.itemName.getAttribute("bundleprices");
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

        for (SaleItem saleItem : bundleItems) {
            Set<Name> namesForValue = new HashSet<Name>();
            namesForValue.add(saleItem.itemName);
            namesForValue.add(priceName);
            valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, df.format(saleItem.price), namesForValue);
            namesForValue = new HashSet<Name>();
            namesForValue.add(saleItem.itemName);
            namesForValue.add(taxName);
            valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, df.format(saleItem.tax), namesForValue);

        }


    }


    private String defaultData() {
        //version number followed by required data.  $starttime to be replaced by latest update
        return "1.0\n" +
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
                "'entity_id, customer_id, base_currency_code, increment_id, shipping_amount','sales_flat_order',  '$starttime', 'entity_id'\n" +
                "'entity_id, email, group_id','customer_entity',  '$starttime', 'entity_id'\n" +
                "'*','customer_group','', 'customer_group_id'\n" +
                "'entity_id, parent_id','customer_address_entity', '$starttime', 'entity_id'\n" +
                "'entity_id, order_id, created_at,total_qty','sales_flat_shipment','$starttime','entity_id'\n" +
                "'item_id,product_id,qty','cataloginventory_stock_item','','item_id'\n";
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
