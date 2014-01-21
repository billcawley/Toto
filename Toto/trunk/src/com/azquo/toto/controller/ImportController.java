package com.azquo.toto.controller;

import com.azquo.toto.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;


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
    public String handleRequest(@RequestParam(value = "connectionid", required = false) final String connectionId, @RequestParam(value = "filename", required = false) final String fileName,
                                @RequestParam(value = "language", required = false) final String language, @RequestParam(value = "filetype", required = false) final String fileType,
                                @RequestParam(value = "separator", required = false) final String separator,@RequestParam(value = "create", required = false) final String create ) throws Exception {

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


        String result;
        String origLanguage = "";
        if (connectionId == null) {
            return "error:no connection id";
        }

        final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

        if (loggedInConnection == null) {
            return "error:invalid or expired connection id";
        }
        origLanguage = loggedInConnection.getLanguage();
        loggedInConnection.setLanguage(language);
        try {

             result = handleRequest(loggedInConnection, fileName, fileType, separator, create);
            loggedInConnection.setLanguage(origLanguage);
        }catch(Exception e){
            e.printStackTrace();
            if (origLanguage.length() > 0){
                loggedInConnection.setLanguage(origLanguage);
            }
            return "error:" + e.getMessage();
        }
        return result;
    }


    public String handleRequest(final LoggedInConnection loggedInConnection, final String fileName, String fileType, String separator, final String strCreate)
            throws Exception{

        if (separator == null || separator.length() == 0) separator = "\t";
        if (separator.equals("comma")) separator = ",";
        // separator not used??
        if (separator.equals("pipe")) separator = "|";

        boolean create = false;
        if (strCreate != null && strCreate.equals("true")){
            create = true;
        }

        // there was a translate service thing before and after but now we need to do it internally
        // ok the data import actually ignores names for the moment
        String result = "";
        if (fileType.toLowerCase().equals("values")){
            result =  importService.dataImport(loggedInConnection, fileName, create);
        }
        // we will pay attention onn the attribute import and replicate
        if (fileType.toLowerCase().equals("names")){
            result = importService.attributeImport(loggedInConnection,fileName,create);

        }

        nameService.persist(loggedInConnection);


        return result;
    }


}
