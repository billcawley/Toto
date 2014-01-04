package com.azquo.toto.controller;

import com.azquo.toto.service.Importer;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
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


    private Importer importer = new Importer();

    @Autowired
    private LoginService loginService = new LoginService();

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "connectionid", required = false) final String connectionId, @RequestParam(value = "filename", required = false) String fileName,
                                @RequestParam(value = "language", required = false) String language, @RequestParam(value = "filetype", required = false) String fileType,
                                @RequestParam(value = "separator", required = false) String separator,@RequestParam(value = "create", required = false) String create ) throws Exception {

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
        try {

            if (connectionId == null) {
                return "error:no connection id";
            }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }
            result = handleRequest(loggedInConnection, fileName, language, fileType, separator, create);
        }catch(Exception e){
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
        return result;
    }


    public String handleRequest(LoggedInConnection loggedInConnection, String fileName, String language, String fileType, String separator, String strCreate)
        throws Exception{

        NameService nameService = new NameService();

        if (separator.length() == 0) separator = "\t";
        if (separator.equals("comma")) separator = ",";
        if (separator.equals("pipe")) separator = "|";

        boolean create = false;
        if (strCreate.equals("true")){
            create = true;
        }
        nameService.translateNames(loggedInConnection,language);
        String result = "";
        if (fileType.toLowerCase().equals("data")){
            result =  importer.dataImport(loggedInConnection, fileName, separator, create);
        }
        if (fileType.toLowerCase().equals("attributes")){
            result = importer.attributeImport(loggedInConnection,fileName,language,separator,create);

        }

         nameService.restoreNames(loggedInConnection);


        return result;
    }


}
