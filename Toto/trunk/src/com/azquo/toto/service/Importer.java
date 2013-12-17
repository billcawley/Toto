package com.azquo.toto.service;

import com.azquo.toto.controller.NameController;
import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.service.*;
import com.csvreader.CsvReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by bill on 13/12/13.
 */




public final class Importer {



    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;
    @Autowired
    private ProvenanceService provenanceService;
    @Autowired
    private ValueDAO valueDao;
    @Autowired
    private LoginService loginService;



     public String dataImport(LoggedInConnection loggedInConnection, String fileName, String separator, boolean create) throws Exception {



        //String filePath = "/home/bill/Downloads/exportcodes.csv";
        //TODO  set correct filepath
        String filePath = "/home/bill/Downloads/" + fileName;
        if (filePath.endsWith(".zip")) {
            filePath = unzip(filePath);
        }

        long track = System.currentTimeMillis();
        String strCreate = "";
        if (create) strCreate = ";create";



        CsvReader csvReader = new CsvReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"), '\t');
        csvReader.readHeaders();
        String[] headers = csvReader.getHeaders();
        // are the following few lines necessary??
        ArrayList<Name> headerNames = new ArrayList<Name>();

        for(String header : headers){
            if (header.trim().length() > 0){ // I don't know if the csv reader checks for this
                NameController n = new NameController();
                String result = n.handleRequest(loggedInConnection,header + strCreate);
                if (result.startsWith("error:")){
                    throw new Exception("unable to understand " + header + " - " + result);
                }
                String nameToFind = header;
                if (header.contains(";")) nameToFind = header.substring(0,header.indexOf(";"));
                Name name = nameService.findOrCreateName(loggedInConnection, nameToFind);
                if (name.getPeers().size() > 0){
                    name.setImportHeading(header);
                    headerNames.add(name);
                }
            }
        }

        if (headerNames.size() == 0){
            throw new Exception("unable to find any name with peers" );
        }



        Provenance provenance = provenanceService.getTestProvenance();
        int valuecount = 0;
        HashMap<String, Name> namesFound = new HashMap<String, Name>();
        while (csvReader.readRecord()){
            String value = null;
            for (Name headerName : headerNames){
                Set<Name> namesForValue = new HashSet<Name>();
                namesForValue.add(headerName);
                String newName = csvReader.get(headerName.getName());
                Iterator it = headerName.getPeers().entrySet().iterator();
                while (it.hasNext()){
                    Map.Entry peerset = (Map.Entry) it.next();
                    Name peer = (Name)peerset.getKey();
                    String peerVal = csvReader.get((String) peer.getName());
                    if (peerVal == null || peerVal.length() == 0){
                        //throw new Exception("unable to find " + peer.getName() + " for " + headerName.getName());
                    }else{
                        //storeStructuredName(peer,peerVal, loggedInConnection);
                        String nameToFind =  peerVal + "," + peer.getName();
                        Name nameFound = namesFound.get(nameToFind);
                        if (nameFound==null){
                            if (create){
                                nameFound = nameService.findOrCreateName(loggedInConnection,nameToFind);
                            }else{
                                nameFound = nameService.findByName(loggedInConnection,nameToFind);
                            }
                            if (nameFound!=null){
                                namesFound.put(nameToFind, nameFound);
                            }
                        }
                        if (nameFound != null){
                            namesForValue.add(nameFound);
                        }
                    }

                    //namesForValue.add(nameService.findOrCreateName(loggedInConnection,peerVal + "," + peer.getName())) ;

                }
                value = csvReader.get(headerName.getImportHeading());
                if (value.trim().length() > 0){ // no point storing if there's no value!
                    valuecount++;
                    valueService.storeValueWithProvenanceAndNames(loggedInConnection, value, provenance, namesForValue);
                    if (valuecount%5000 == 0){
                        System.out.println("storing value " + valuecount);
                    }
                }

            }
        }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms");

        nameService.persist(loggedInConnection);

        return "";
    }


  public String attributeImport(LoggedInConnection loggedInConnection, String fileName, String language, String separator, boolean create) throws Exception {

    //String filePath = "/home/bill/Downloads/exportcodes.csv";
    //TODO  set correct filepath
    String filePath = "/home/bill/Downloads/" + fileName;
    if (filePath.endsWith(".zip")) {
        filePath = unzip(filePath);
    }
    CsvReader csvReader = new CsvReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"), '\t');
    csvReader.readHeaders();
    String[] headers = csvReader.getHeaders();
    // are the following few lines necessary??


     while (csvReader.readRecord()){
        String value = null;
        String searchName = csvReader.get(language);
        Name name = null;

        if (searchName != null){
            if (create){
                name = nameService.findOrCreateName(loggedInConnection, searchName);
            }else{
                name = nameService.findByName(loggedInConnection,searchName);
            }
        }
        if (name != null){
            for (String header: headers){
              String newName = csvReader.get(header);
              name.setAttribute(header,newName);
            }

        }
    }

    nameService.persist(loggedInConnection);

    return "";
}













    public void storeStructuredName(Name parentName, String rawChildName, LoggedInConnection loggedInConnection)
            throws Exception{


        rawChildName = rawChildName.replace("//", "/");
        if (rawChildName.endsWith("/")){
            rawChildName = rawChildName.substring(0, rawChildName.length() -1);
        }
        if (rawChildName.startsWith("/")){
            rawChildName = rawChildName.substring(1, rawChildName.length());
        }
        if (rawChildName.contains("/")){
            Name justAdded = null;
            while (rawChildName.contains("/")){
                // lest work from the top
                String remainingTop = rawChildName.substring(0, rawChildName.indexOf("/"));
                rawChildName = rawChildName.substring(rawChildName.indexOf("/") + 1); // chop off the name we just extracted
                if (justAdded == null){ // the first of the directory string so to speak
                    justAdded = nameService.addOrCreateChild(loggedInConnection,parentName, remainingTop);
                    //System.out.println("parent : " + parentName + " child " + remainingTop);
                } else {
                    justAdded = nameService.addOrCreateChild(loggedInConnection,justAdded, remainingTop);
                    //System.out.println("parent : " + justAdded + " child " + remainingTop);
                }
                if (!rawChildName.contains("/")){ // the final one
                    nameService.addOrCreateChild(loggedInConnection,justAdded, rawChildName);
                    //System.out.println("parent : " + justAdded + " child " + rawChildName);
                }
            }
        } else {
            nameService.addOrCreateChild(loggedInConnection,parentName,rawChildName);
        }

    }

    public String unzip(String zipFile){

        byte[] buffer = new byte[1024];
        File newFile = null;
        try{

            String outputFolder = zipFile.substring(0, zipFile.lastIndexOf(File.separator));
            //get the zip file content
            ZipInputStream zis =
                    new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while(ze!=null){

                String fileName = ze.getName();
                newFile = new File(outputFolder + File.separator + fileName);

                System.out.println("file unzip : "+ newFile.getAbsoluteFile());

                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();

                FileOutputStream fos = new FileOutputStream(newFile);

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

            System.out.println(zipFile + " Unzipped to " + newFile.getName());

        }catch(IOException ex){
            ex.printStackTrace();
        }
        return newFile.getPath();
    }



}
