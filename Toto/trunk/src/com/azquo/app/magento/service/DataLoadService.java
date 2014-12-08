package com.azquo.app.magento.service;

import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.Name;
import com.azquo.service.AzquoMemoryDBConnection;
import com.azquo.service.NameService;
import com.azquo.service.ValueService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by cawley on 07/08/14.
 *
 */
public final class DataLoadService {

    private final String latestupdate = "Latest update";

    @Autowired
    private NameService nameService;

    @Autowired
    private ValueService valueService;



    private final Map<String, List<Map<String, String>>> tableMap = new HashMap<String, List<Map<String, String>>>();
    final Map<Integer, Name> products = new HashMap<Integer, Name>();
    final Map<Integer, Name> categories = new HashMap<Integer, Name>();
    //final Map<Integer, MagentoOrderLineItem> orderLineItems = new HashMap<Integer, MagentoOrderLineItem>();
    final Map<String, String> optionValueLookup = new HashMap<String, String>();



    public String findLastUpdate(AzquoMemoryDBConnection azquoMemoryDBConnection){
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


    public void loadData(AzquoMemoryDBConnection azquoMemoryDBConnection, String data) throws Exception {
        loadData(azquoMemoryDBConnection, data != null ? new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))) : null);
    }

    public void loadData(AzquoMemoryDBConnection azquoMemoryDBConnection, InputStream data) throws Exception {
        loadData(azquoMemoryDBConnection, data != null ? new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8)) : null);
    }

    public void loadData(AzquoMemoryDBConnection azquoMemoryDBConnection, BufferedReader br) throws Exception {
        if (br == null){
            br = new BufferedReader(new FileReader("/home/bill/Sear/magento/testdata_dump.txt"));
        }

        String line;
        List<Map<String, String>> currentTableDataMap = null;
        String[] currentColumnNames = null;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("||||TABLE:")) {
                currentColumnNames = null;
                String tableName = line.substring(10);
                System.out.println("Initial load of : " + tableName);
                // I'm not going to support tables being loaded in two chunks I see no point. THis would overwrite data if a table were referenced twice
                currentTableDataMap = new ArrayList<Map<String, String>>(); // and I know this repeats keys for each row, the goal here is ease of use for importing, not efficiency
                tableMap.put(tableName, currentTableDataMap);
            } else { // data, is it the first one?
                if (currentColumnNames == null) {
                    currentColumnNames = line.split("\t", -1);
                } else {
                    int index = 0;
                    String[] lineValues = line.split("\t", -1);
                    Map<String, String> dataRowMap = new HashMap<String, String>();
                    for (int i = 0; i < lineValues.length;i++) {
                         dataRowMap.put(currentColumnNames[i],lineValues[i]);
                    }
                    currentTableDataMap.add(dataRowMap);
                }
            }
        }
        System.out.println("initial load of magento data done");
        System.out.println("Trying to make some product objects");

        Name topProduct = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "product", null, false);
        Name productAttributesName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Product Attributes", topProduct, false);
        Name allCategoriesName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All product categories", topProduct, false);
        Name allProducts = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "All products", topProduct, false);
        Name uncategorisedProducts = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Uncategorised Products", topProduct, false);

        //find out the name attribute numbers ....
        String categoryEntityId = null;
        String productEntityId = null;
        Map<String, String> categoryEntityTypeRecord = null;
        for (Map<String, String> entityTypeRecord : tableMap.get("eav_entity_type")) {
            if (entityTypeRecord.get("entity_type_code").equals("catalog_category")) {
                categoryEntityId = entityTypeRecord.get("entity_type_id");
            }
            if (entityTypeRecord.get("entity_type_code").equals("catalog_product")) {
                productEntityId = entityTypeRecord.get("entity_type_id");
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

        for (Map<String, String> entityTypeRecord : tableMap.get("catalog_category_entity")) {
            //invert the path for uploading to Azquo  -  1/2/3 becomes `3`,`2`,`1`
            StringTokenizer pathBits = new StringTokenizer(entityTypeRecord.get("path"), "/");
            String path = "";
            while (pathBits.hasMoreTokens()) {
                path = "`" + pathBits.nextToken() + "`," + path;
            }
            languages.add("MagentoCategoryID");
            azquoCategoriesFound.put(entityTypeRecord.get("entity_id"), nameService.findOrCreateNameStructure(azquoMemoryDBConnection, path.substring(0, path.length() - 1), allCategoriesName, true, languages));

        }
        //now name the categories
        for (Map<String, String> attributeRow : tableMap.get("catalog_category_entity_varchar")) { // should (!) have us looking in teh right place
            //only picking the name from all the category attributes

            if (attributeRow.get("attribute_id").equals(categoryNameId)) {
                Name magentoName = nameService.findByName(azquoMemoryDBConnection, attributeRow.get("entity_id"));
                azquoCategoriesFound.get(attributeRow.get("entity_id")).setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, attributeRow.get("value"));
            }
        }
        languages.clear();
        languages.add("MagentoProductID");

        for (Map<String, String> entityRow : tableMap.get("catalog_product_entity")) {
            Name magentoName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, entityRow.get("entity_id"), allProducts, true,languages);
            uncategorisedProducts.addChildWillBePersisted(magentoName);
            magentoName.setAttributeWillBePersisted("SKU", entityRow.get("sku"));
            azquoProductsFound.put(entityRow.get("entity_id"), magentoName);
        }
        //name the products...
        for (Map<String, String> attributeRow : tableMap.get("catalog_product_entity_varchar")) {
            //only picking the name from all the category attributes
            if (attributeRow.get("attribute_id").equals(productNameId)) {
                Name magentoName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, attributeRow.get("entity_id"), topProduct, true,languages);
                azquoProductsFound.get(attributeRow.get("entity_id")).setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME, attributeRow.get("value"));
            }
        }
        //create a product structure
        for (Map<String, String> relationRow : tableMap.get("catalog_product_relation")) {
            Name child = azquoProductsFound.get(relationRow.get("child_id"));
            azquoProductsFound.get(relationRow.get("parent_id")).addChildWillBePersisted(child);
            allProducts.removeFromChildrenWillBePersisted(child);
        }
        // and put products into categories

        for (Map<String, String> relationRow : tableMap.get("catalog_category_product")) {
            Name child = azquoProductsFound.get(relationRow.get("product_id"));
            azquoCategoriesFound.get(relationRow.get("category_id")).addChildWillBePersisted(child);
            uncategorisedProducts.removeFromChildrenWillBePersisted(child);
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
                Name magentoProductCategory = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, attributeNames.get(attVal), productAttributesName, true,languages);
                String val = attVals.get("value");
                if (optionValues.get(val) != null) {
                    Name magentoOptionName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, optionValues.get(val), magentoProductCategory, true,languages);
                    magentoOptionName.addChildWillBePersisted(magentoName);
                } else {
                    System.out.println("found an option value " + val + " for " + magentoProductCategory.getDefaultDisplayName());
                }
            }
        }

        double price = 0.0;
        double qty = 0.0;
        String configLine = null;
        String productId = null;
        Name entities = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "Entities",null,false);
        Name priceName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Price",entities, false);
           Name qtyName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,"Quantity",entities, false);
        Name allOrdersName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection,"All orders, order",null, false);
        Name allDates = nameService.findOrCreateNameStructure(azquoMemoryDBConnection,"All dates, date", null, false);
        final LinkedHashMap<Name, Boolean> peers = new LinkedHashMap<Name, Boolean>(2);
        peers.put(allOrdersName, true);
        priceName.setPeersWillBePersisted(peers);
        qtyName.setPeersWillBePersisted(peers);

        Map<String, Name> azquoOrdersFound = new HashMap<String, Name>();

        for (Map<String, String> salesRow : tableMap.get("sales_flat_order_item")) {

            if (configLine == null) {
                price = 0.0;
                qty = 0.0;
                productId = salesRow.get("product_id");
                try {
                    price = Double.parseDouble(salesRow.get("price"));
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
                productId = salesRow.get("product_id");
                if (!configLine.equals(salesRow.get("parent_item_id"))) {
                    System.out.println("problem in importing sales items - config item " + configLine + " does not have a simple item associated");
                    qty = 0;
                }
                configLine = null;
            }
            languages.clear();
            languages.add("MagentoOrderID");
            if (configLine == null) {
                //store the values.   Qty and price have attributes order, product.  order is in all orders, and in the relevant date
                String orderNo = salesRow.get("order_id");
                Name orderName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,orderNo, allOrdersName,true,languages);
                azquoOrdersFound.put(orderNo, orderName);
                //adding 'I' to the item number so as not to confuse with order number for the developer - the system should be happy without it.
                Name orderItemName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, "I" + salesRow.get("item_id"), orderName, true,languages);
                Set<Name> namesForValue = new HashSet<Name>();
                Name productName = azquoProductsFound.get(salesRow.get("product_id"));
                if (productName == null){
                    //not on the product list!!  in the demo database there was a giftcard in the sales that was not in the product list
                    productName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,salesRow.get("product_id"), allProducts,true);
                    productName.setAttributeWillBePersisted(Name.DEFAULT_DISPLAY_NAME,salesRow.get("product_type"));
                }

                String orderDate = salesRow.get("created_at").substring(0,10);
                Name dateName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection, orderDate, allDates, true,languages);
                dateName.addChildWillBePersisted(orderName);
                productName.addChildWillBePersisted(orderItemName);
                //namesForValue.add(productName);
                namesForValue.add(orderItemName);
                namesForValue.add(priceName);
                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, price + "", namesForValue);
                namesForValue.remove(priceName);
                namesForValue.add(qtyName);
                valueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, qty + "", namesForValue);


            }

        }



        languages.clear();
        languages.add("MagentoCustomerID");
        Name allCustomersName = nameService.findOrCreateNameStructure(azquoMemoryDBConnection,"All customers, customer",null, false,languages);

        for (Map<String, String> orderRow : tableMap.get("sales_flat_order")) {
            //only importing the IDs at present
            Name orderName = azquoOrdersFound.get(orderRow.get("entity_id"));
            if (orderName != null){
                String customer = orderRow.get("customer_id");
                if (customer == null || customer.length()== 0) customer="Unknown";
                Name customerName = nameService.findOrCreateNameInParent(azquoMemoryDBConnection,customer, allCustomersName, true,languages);
                customerName.addChildWillBePersisted(orderName);
            }



        }
        Name order = nameService.findByName(azquoMemoryDBConnection,"order");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        order.setAttributeWillBePersisted(latestupdate, sdf.format(new Date()));
        if (!azquoMemoryDBConnection.getCurrentDBName().equals("temp")){
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
