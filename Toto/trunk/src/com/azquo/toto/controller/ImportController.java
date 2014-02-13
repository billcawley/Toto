package com.azquo.toto.controller;

import com.azquo.toto.adminentities.UploadRecord;
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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Created by bill on 17/12/13.
 * will import csv files (usual record structure, with headings specifying peers if necessary)
 * Now prepares the  multi part form data for the import service
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
        try {
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
            boolean macMode = false;
            if (!item.getFieldName().equals("parameters")) { // no parameters file passed, used to be a plain error but mac may have other ideas - parameters sent via curl
                while (it.hasNext()){
                    if (item.getFieldName().equals("connectionid")) {
                        macMode = true;
                        loggedInConnection = loginService.getConnection(item.getString());
                    } else if (item.getFieldName().equals("filename")) {
                        fileName = item.getString();
                    } else if (item.getFieldName().equals("filetype")) {
                        fileType = item.getString();
                    } else if (item.getFieldName().equals("language")) {
                        language = item.getString();
                    } else if (item.getFieldName().equals("separator")) {
                        separator = item.getString();
                    } else if (item.getFieldName().equals("create")) {
                        create = item.getString();
                    }
                    // ok this is a bit hacky but we assume the last item is the file and let the code below deal with it
                    item = (FileItem) it.next();
                }

                if (!macMode){ // either mac or windows not sending what we want
                    return "error: expecting parameters";
                }
            } else { // parameters file built on windows
                String parameters = item.getString();
                StringTokenizer st = new StringTokenizer(parameters, "&");
                while (st.hasMoreTokens()) {
                    String parameter = st.nextToken();
                    if (!parameter.endsWith("=")){
                        StringTokenizer st2 = new StringTokenizer(parameter, "=");
                        String parameterName = st2.nextToken();
                        if (parameterName.equals("connectionid")) {
                            loggedInConnection = loginService.getConnection(st2.nextToken());
                        }
                        if (parameterName.equals("filename")) {
                            fileName = st2.nextToken();
                        }
                        if (parameterName.equals("filetype")) {
                            fileType = st2.nextToken();
                        }
                        if (parameterName.equals("language")) {
                            language = st2.nextToken();
                        }
                        if (parameterName.equals("separator")) {
                            separator = st2.nextToken();
                        }
                        if (parameterName.equals("create")) {
                            create = st2.nextToken();
                        }
                    }
                }

                if (loggedInConnection == null) {
                    return "error:invalid or expired connection id";
                }
                item = (FileItem) it.next();
            }
            String result;
            System.out.println("upload file : " + item.getName());
            System.out.println("upload file size : " + item.getSize());
            InputStream uploadFile = item.getInputStream();
            origLanguage = loggedInConnection.getLanguage();
            if (language.toLowerCase().endsWith("loose")){
                loggedInConnection.setLoose(true);
                language = language.substring(0, language.length()-5).trim();
            }
            if (language==null || language.length()==0 || language.equalsIgnoreCase("name")){
                language="DEFAULT_DISPLAY_NAME";
            }

            loggedInConnection.setLanguage(language);

            result = importService.importTheFile(loggedInConnection, fileName, uploadFile, fileType, separator, create, macMode);
            loggedInConnection.setLanguage(origLanguage);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            if (origLanguage.length() > 0) {
                loggedInConnection.setLanguage(origLanguage);
            }
            return "error:" + e.getMessage();
        }
    }


}
