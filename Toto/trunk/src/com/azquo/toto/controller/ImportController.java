package com.azquo.toto.controller;

import com.azquo.toto.service.*;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;




import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Created by bill on 17/12/13.
 * will import csv files (usual record structure, with headings specifying peers if necessary)
 */

@Controller
@RequestMapping("/Import")


public class ImportController {

    @Autowired
    private ImportService importService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private NameService nameService;

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request) throws Exception {

        /*
       'filename' is the name of a file that has been FTP uploaded

       'language' is the language to which the names will be translated before importing.   After importing, the translation will be bacl to 'name'

        'fileType' can be 'data' or 'translate'

           'data' files have column headings that may include 'peers' definition.
           'translate' files sill assume that the default language will be the column headed 'name'
                e.g.  if the initial import has been done from the accounts program using accounts codes then the translate file might have headings
               'accounts', 'name' (...others might include 'french', or 'payroll')

            columns containing names may contain names separated by commas to indicate a structure, which may not be complete
                 e.g.  'London, Ontario'    or 'London, Canada'   would find the same place ('London, Ontario, Canada') if it was already in the database.

        'separator'  will be a tab unless otherwise specified (e.g. 'comma' or 'pipe')

         'create' will indicate if new names are to be created.  If 'create' is not specified, any name that is not understood will be rejected

         */
        String origLanguage = "";
        String fileName = "";
        String fileType = "";
        String language = "";
        String separator = "\t";
        String create = "";
        LoggedInConnection loggedInConnection = null;
        try{
            DiskFileItemFactory factory = new DiskFileItemFactory();

// Configure a repository (to ensure a secure temp location is used)
            ServletContext servletContext = request.getServletContext();
            File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
            factory.setRepository(repository);

// Create a new file upload handler
            ServletFileUpload upload = new ServletFileUpload(factory);

// Parse the request
            List<FileItem> items = upload.parseRequest(request);
            Iterator it = items.iterator();
            FileItem item = (FileItem) it.next();
            if (!item.getFieldName().equals("parameters")){
                return "error: expecting parameters";
            }
            String parameters = item.getString();
            StringTokenizer st = new StringTokenizer(parameters,"&");
            while (st.hasMoreTokens()){
                String parameter = st.nextToken();
                StringTokenizer st2 = new StringTokenizer(parameter,"=");
                String parameterName = st2.nextToken();
                if (parameterName.equals("connectionid")){
                    loggedInConnection = loginService.getConnection(st2.nextToken());
                }
                if (parameterName.equals("filename")){
                    fileName = st2.nextToken();
                }
                if (parameterName.equals("filetype")){
                    fileType = st2.nextToken();
                }
                if (parameterName.equals("language")){
                    language = st2.nextToken();
                }
                if (parameterName.equals("separator")){
                    separator = st2.nextToken();
                }
                if (parameterName.equals("create")){
                    create = st2.nextToken();
                }
            }

            String result;
             if (loggedInConnection == null) {
                   return "error:invalid or expired connection id";
             }
            item = (FileItem) it.next();
            InputStream uploadFile = item.getInputStream();
            origLanguage = loggedInConnection.getLanguage();
            loggedInConnection.setLanguage(language);

             result = handleRequest(loggedInConnection, fileName,  uploadFile, fileType, separator, create);
            loggedInConnection.setLanguage(origLanguage);
            //return result;
            return null;
        }catch(Exception e){
            e.printStackTrace();
            if (origLanguage.length() > 0){
                loggedInConnection.setLanguage(origLanguage);
            }
            return "error:" + e.getMessage();
        }
     }


    public String handleRequest(final LoggedInConnection loggedInConnection, final String fileName, InputStream uploadFile, String fileType, String separator, final String strCreate)
            throws Exception{

        if (separator == null || separator.length() == 0) separator = "\t";
        if (separator.equals("comma")) separator = ",";
        // separator not used??
        if (separator.equals("pipe")) separator = "|";

        boolean create = false;
        if (strCreate != null && strCreate.equals("true")){
            create = true;
        }
        if (fileName.endsWith(".zip")) {
            uploadFile =new ByteArrayInputStream(unzip((ZipInputStream) uploadFile).toString().getBytes());
        }


        // there was a translate service thing before and after but now we need to do it internally
        // ok the data import actually ignores names for the moment
        String result = "";
        if (fileType.toLowerCase().equals("values")){
            result =  importService.dataImport(loggedInConnection,  uploadFile, create);
        }
        // we will pay attention onn the attribute import and replicate
        if (fileType.toLowerCase().equals("names")){
            result = importService.attributeImport(loggedInConnection,uploadFile, create);

        }

        nameService.persist(loggedInConnection);


        return result;
    }



    public StringBuffer unzip(ZipInputStream zis) {
        //shouldn't really do this in memory, but then, these files are not VERY large.

        byte[] buffer = new byte[1024];
        File newFile = null;
        StringBuffer  fos = new StringBuffer();
        try {
            //get the zip file content
            //get the zipped file list entry - there should only be one
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.append(buffer.toString().substring(0, len));
                }
            }
            zis.closeEntry();
            zis.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return fos;
    }

}
