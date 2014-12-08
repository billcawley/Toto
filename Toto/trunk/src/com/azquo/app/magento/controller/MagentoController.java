package com.azquo.app.magento.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.app.magento.service.DataLoadService;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Created by bill on 28/10/14.
 */

@Controller
@RequestMapping("/Magento")


public class MagentoController {

    @Autowired
    private DataLoadService dataLoadService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    OnlineService onlineService;

    @Autowired
    LoginService loginService;

    @Autowired
    DatabaseDAO databaseDAO;

    private static final Logger logger = Logger.getLogger(MagentoController.class);


    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request) throws Exception {
     LoggedInConnection loggedInConnection = null;
     try {
          DiskFileItemFactory factory = new DiskFileItemFactory();

// Configure a repository (to ensure a secure temp location is used)
        ServletContext servletContext = request.getServletContext();
        File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
        factory.setRepository(repository);
        FileItem item = null;
        FileItem data = null;
// Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);

         List<FileItem> items = upload.parseRequest(request);
         Iterator it = items.iterator();
         String op = null;
         String db = null;
         String connectionId = null;

         while (it.hasNext()){
              item = (FileItem) it.next();

             if (item.getFieldName().equals("db")) {
                 db = item.getString();
             } else if (item.getFieldName().equals("op")) {
                 op = item.getString();
             } else if (item.getFieldName().equals("connectionid")) {
                 connectionId = item.getString();
             }
             // ok this is a bit hacky but we assume the last item is the file and let the code below deal with it
             data = item;
         }




// Parse the request



         if (op==null) op = "";
         if (connectionId != null){
             loggedInConnection = loginService.getConnection(connectionId);
         }
         if (loggedInConnection == null) {
             //for testing only
             if (db == null) db = "temp";
             loggedInConnection = loginService.login(db,"tempuser","password",0,"",false);//will automatically switch the database to 'temp' if that's the only one
             if (!db.equals("temp")){
                 String result = onlineService.switchDatabase(loggedInConnection, db);
                 //todo  consider what happens if there's an error here
             }
             //loggedInConnection = loginService.login("test","magentobill","password",0,"",false);
        }
         if (op.equals("lastupdate")){
             return dataLoadService.findLastUpdate(loggedInConnection);
         }
         if (op.equals("updatedb")) {
            dataLoadService.loadData(loggedInConnection, data.getInputStream());
             return loggedInConnection.getConnectionId() + "";
            //return onlineService.readExcel(loggedInConnection, onlineReport, null, "");
        }
         if (op.equals("reports")){
             OnlineReport onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
             return onlineService.readExcel(loggedInConnection, onlineReport, null, "");

         }
         return "unknown op";
     } catch (Exception e) {
        e.printStackTrace();
        return "error:" + e.getMessage();
    }
}


}



