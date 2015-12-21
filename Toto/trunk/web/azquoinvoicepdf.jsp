<%@ page contentType="application/pdf" %>
<%@ page
        import="java.util.*,java.text.*,java.io.*,  org.apache.fop.apps.*, javax.xml.transform.*, javax.xml.transform.stream.*, javax.xml.transform.sax.*" %>
<%@ page import="org.apache.commons.lang.*" %>
<%

    double quantity = 1;
    int dayrate = 2000;
    String terms = "";
    String poNumber = "";
    Date invoiceDate = new Date();
    String invoiceid = "2014/03";
    String currency = "GBP";
    String month = "February";
//    String invoiceAddress = "Anonymous Ginger Ltd.\n177B New Kings Road\nParsons Green\nLondon\nSW6 4SN";
    String invoiceAddress = "";
//    String invoiceDesc = "Development of RaceAdvisor App";
    String invoiceDesc = "";

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String sageRef = "AG1";

    try{
        poNumber = request.getParameter("ponumber");
        invoiceDate = sdf.parse(request.getParameter("invoicedate"));

        invoiceid = request.getParameter("invoiceid");
        currency = request.getParameter("currency");
        month = request.getParameter("month");
        invoiceAddress = request.getParameter("invoiceaddress");
        invoiceDesc = request.getParameter("description");
        sageRef = request.getParameter("sageref");
        terms = request.getParameter("terms");
        quantity = Double.parseDouble(request.getParameter("quantity"));
        dayrate = Integer.parseInt(request.getParameter("dayrate"));

    } catch (Exception e){
        e.printStackTrace();
    }



    double currencyValue = dayrate * quantity;
    double vat = currencyValue * 0.2;
    if (request.getParameter("novat") != null){
        vat = 0;
    }
    double total = currencyValue + vat;


// -           SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy");

    // check here that we won't get a keyword error on month!!!!!

/*    String invoicePeriod;
    String ponumber = "";
    invoicePeriod = month;*/


    FopFactory fopFactory = FopFactory.newInstance();
    TransformerFactory tFactory = TransformerFactory.newInstance();
    System.out.println("creating Invoice PDF " + request.getRemoteAddr());

    response.setContentType("application/pdf");
    String address1 = invoiceAddress.contains("\n") ? invoiceAddress.substring(0, invoiceAddress.indexOf("\n")) : invoiceAddress;
    address1 = address1.contains("\r") ? address1.substring(0, address1.indexOf("\r")) : address1;
    response.addHeader("Content-Disposition", "attachment; filename=AzquoInvoice" + address1 + month + ".pdf");
    Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, response.getOutputStream());
    Transformer transformer = tFactory.newTransformer();
    StringBuffer template = new StringBuffer();
    String inputLine = null;
    try {
        FileInputStream fstream = new FileInputStream("/usr/share/apache-tomcat-8.0.24/webapps/ROOT/azquoinvoice.fop");
        //FileInputStream fstream = new FileInputStream("/usr/share/apache-tomcat-8.0.21/webapps/ROOT/azquoinvoice.fop");
        DataInputStream in = new DataInputStream(fstream);
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        while ((inputLine = br.readLine()) != null) {
            // Print the content on the console
            template.append(inputLine);
        }
        //Close the input stream
        in.close();
    } catch (Exception e) {//Catch exception if any
        System.out.println("Error reading azquoinvoice.fop: " + e.getMessage());
    }
    String invoiceFOP = template.toString();
    NumberFormat nf = NumberFormat.getInstance();
    nf.setMinimumFractionDigits(2);
    int termsInt = 30;
    if (terms != null && terms.length() > 0) {
        String days =  terms.contains(" ") ? terms.substring(0, terms.indexOf(" ")) : terms;
        try {
            termsInt = Integer.parseInt(days);
        } catch (Exception e) {
        }
    }
    java.util.Date dateDue = new java.util.Date(invoiceDate.getYear(), invoiceDate.getMonth(), invoiceDate.getDate() + termsInt);
    SimpleDateFormat monthDisplay = new SimpleDateFormat("MMMMM");
    //invoiceFOP = invoiceFOP.replace("INVOICEMONTH",monthDisplay.format(i.getDate()), true);
    String invoiceTitle = "Monthly fee for developmennt of RaceAdvisor App";
    invoiceFOP = invoiceFOP.replace("INVOICETITLE", StringEscapeUtils.escapeXml(invoiceTitle));
    SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
    String dateDueI = StringEscapeUtils.escapeXml(df.format(dateDue));
    String paidWithThanks = "";
    //if (i.getDatePaid() !=null) paidWithThanks = "Paid with thanks " + Database.dateFormat2.format(i.getDatePaid());
    invoiceFOP = invoiceFOP.replace("INVOICENAME", "INVOICE");
    invoiceFOP = invoiceFOP.replace("PAIDWITHTHANKS", paidWithThanks);
    invoiceFOP = invoiceFOP.replace("DATEDUE", dateDueI);
    invoiceFOP = invoiceFOP.replace("PONUMBER", StringEscapeUtils.escapeXml(poNumber));
    int lineNo = 2;
    invoiceFOP = invoiceFOP.replace("INVOICEL1", "");
    invoiceFOP = invoiceFOP.replace("INVOICEPER1", month);
    invoiceFOP = invoiceFOP.replace("INVOICEDESC1", invoiceDesc);
    invoiceFOP = invoiceFOP.replace("INVOICEQUANT1", quantity + "");
    invoiceFOP = invoiceFOP.replace("INVOICEMON1", dayrate + "");
    invoiceFOP = invoiceFOP.replace("INVOICEN1", nf.format(currencyValue));

    lineNo = 2;

    // hack for 2 lines . . .
/*    currencyValue += 1100;
    vat = currencyValue * 0.2;
    total = currencyValue + vat;

    invoiceFOP = invoiceFOP.replace("INVOICEL2", "");
    invoiceFOP = invoiceFOP.replace("INVOICEPER2", month);
    invoiceFOP = invoiceFOP.replace("INVOICEDESC2", "Days modifying daily_racing_stats.php and add_betfair_ids.php");
    invoiceFOP = invoiceFOP.replace("INVOICEQUANT2", "2.0");
    invoiceFOP = invoiceFOP.replace("INVOICEMON2", "550");
    invoiceFOP = invoiceFOP.replace("INVOICEN2", nf.format(1100.0));

    // hack for 2 lines . . .
    currencyValue += 1833;
    vat = currencyValue * 0.2;
    total = currencyValue + vat;
    lineNo = 4;
    invoiceFOP = invoiceFOP.replace("INVOICEL3", "");
    invoiceFOP = invoiceFOP.replace("INVOICEPER3", month);
    invoiceFOP = invoiceFOP.replace("INVOICEDESC3", "InfusionSoft App Payment");
    invoiceFOP = invoiceFOP.replace("INVOICEQUANT3", "1");
    invoiceFOP = invoiceFOP.replace("INVOICEMON3", "1833");
    invoiceFOP = invoiceFOP.replace("INVOICEN3", nf.format(1833.0));*/

    while (lineNo < 10) {
        invoiceFOP = invoiceFOP.replace("INVOICEL" + lineNo, "");
        invoiceFOP = invoiceFOP.replace("INVOICEPER" + lineNo, "");
        invoiceFOP = invoiceFOP.replace("INVOICEDESC" + lineNo, "");
        invoiceFOP = invoiceFOP.replace("INVOICEQUANT" + lineNo, "");
        invoiceFOP = invoiceFOP.replace("INVOICEMON" + lineNo, "");
        invoiceFOP = invoiceFOP.replace("INVOICEN" + lineNo, "");
        lineNo++;

    }

    invoiceFOP = invoiceFOP.replace("INVOICECURRENCY", StringEscapeUtils.escapeXml("GBP"));
    invoiceFOP = invoiceFOP.replace("INVOICEDATE", StringEscapeUtils.escapeXml(df.format(invoiceDate)));
    String iNo = invoiceid;
    invoiceFOP = invoiceFOP.replace("INVOICENUMBER", StringEscapeUtils.escapeXml(iNo));
    invoiceFOP = invoiceFOP.replace("INVOICEVAT", StringEscapeUtils.escapeXml(nf.format(vat)));
    invoiceFOP = invoiceFOP.replace("INVOICEAMOUNT", StringEscapeUtils.escapeXml(nf.format(currencyValue)));
    invoiceFOP = invoiceFOP.replace("INVOICETOTAL", StringEscapeUtils.escapeXml(nf.format(total)));
    if (sageRef == null) sageRef = "";
    invoiceFOP = invoiceFOP.replace("INVOICESAGEREF", StringEscapeUtils.escapeXml(sageRef));


    String text = invoiceFOP;
    //invoiceAddress = invoiceAddress.replace("&","&#38;");
    String address = invoiceAddress.trim();
    int line = 1;
    StringTokenizer st = new StringTokenizer(address, "\n");
    while (st.hasMoreTokens()) {
        text = text.replace("ADDRESS" + line, StringEscapeUtils.escapeXml(st.nextToken()));
        line++;
    }
    text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
    line++;
    text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
    line++;
    text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
    line++;
    text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
    line++;

    text = text.replace("PAYMENTTERMS", termsInt + " Days");

    invoiceFOP = text;


    Source src = new StreamSource(new StringReader(invoiceFOP));
    Result res = new SAXResult(fop.getDefaultHandler());

    transformer.transform(src, res);

%>