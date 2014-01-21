package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.csvreader.CsvReader;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by bill on 13/12/13.
 * service to process files used to import data into the database
 */

public final class ImportService {

    @Autowired
    private ValueService valueService;
    @Autowired
    private NameService nameService;

    public String dataImport(final LoggedInConnection loggedInConnection, final String fileName, final boolean create) throws Exception {
        // OK I think I'm supposed to use language in here but how??? Will go to default name for the moment
        final HashMap<Name, String> nameImportHeadingMap = new HashMap<Name, String>();
        //String filePath = "/home/bill/Downloads/exportcodes.csv";
        //TODO  set correct filepath
        String filePath = "/home/bill/Downloads/" + fileName;
        if (filePath.endsWith(".zip")) {
            filePath = unzip(filePath);
        }

        long track = System.currentTimeMillis();
        String strCreate = "";
        if (create) strCreate = ";create";


        final CsvReader csvReader = new CsvReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"), '\t');
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();
        // are the following few lines necessary??
        final ArrayList<Name> headerNames = new ArrayList<Name>();

        for (String header : headers) {
            if (header.trim().length() > 0) { // I don't know if the csv reader checks for this
                final String result = nameService.handleRequest(loggedInConnection, header + strCreate);
                if (result.startsWith("error:")) {
                    throw new Exception("unable to understand " + header + " - " + result);
                }
                String nameToFind = header;
                if (header.contains(";")) nameToFind = header.substring(0, header.indexOf(";"));
                final Name name = nameService.findOrCreateName(loggedInConnection, nameToFind);
                if (name.getPeers().size() > 0) {
                    nameImportHeadingMap.put(name, header);
                    headerNames.add(name);
                }
            }
        }

        if (headerNames.size() == 0) {
            throw new Exception("unable to find any name with peers");
        }


        final Provenance provenance = loggedInConnection.getProvenance();
        int valuecount = 0;
        final HashMap<String, Name> namesFound = new HashMap<String, Name>();
        while (csvReader.readRecord()) {
            String value;
            for (Name headerName : headerNames) {
                final Set<Name> namesForValue = new HashSet<Name>();
                namesForValue.add(headerName);
                for (Name peer : headerName.getPeers().keySet()) {
                    final String peerVal = csvReader.get(peer.getDefaultDisplayName());
                    if (peerVal == null || peerVal.length() == 0) {
                        //throw new Exception("unable to find " + peer.getName() + " for " + headerName.getName());
                    } else {
                        //storeStructuredName(peer,peerVal, loggedInConnection);
                        final String nameToFind = peerVal + "," + peer.getDefaultDisplayName();
                        Name nameFound = namesFound.get(nameToFind);
                        if (nameFound == null) {
                            if (create) {
                                nameFound = nameService.findOrCreateName(loggedInConnection, nameToFind);
                            } else {
                                nameFound = nameService.findByName(loggedInConnection, nameToFind);
                            }
                            if (nameFound != null) {
                                namesFound.put(nameToFind, nameFound);
                            }
                        }
                        if (nameFound != null) {
                            namesForValue.add(nameFound);
                        }
                    }

                    //namesForValue.add(nameService.findOrCreateName(loggedInConnection,peerVal + "," + peer.getName())) ;

                }
                value = csvReader.get(nameImportHeadingMap.get(headerName));
                if (value.trim().length() > 0) { // no point storing if there's no value!
                    valuecount++;
                    valueService.storeValueWithProvenanceAndNames(loggedInConnection, value, provenance, namesForValue);
                    if (valuecount % 5000 == 0) {
                        System.out.println("storing value " + valuecount);
                    }
                }

            }
        }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms");

        nameService.persist(loggedInConnection);

        return "";
    }


    public String attributeImport(final LoggedInConnection loggedInConnection, final String fileName, final boolean create) throws Exception {

        //String filePath = "/home/bill/Downloads/exportcodes.csv";
        //TODO  set correct filepath
        String filePath = "/home/bill/Downloads/" + fileName;
        if (filePath.endsWith(".zip")) {
            filePath = unzip(filePath);
        }
        final CsvReader csvReader = new CsvReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"), '\t');
        csvReader.readHeaders();
        final String[] headers = csvReader.getHeaders();


        while (csvReader.readRecord()) {
            final String searchName = csvReader.get(loggedInConnection.getLanguage());
            Name name = null;

            // ok this is going to search for things in the language column with the default name . . doesn't really make sense!

            if (searchName != null) {
                if (create) {
                    name = nameService.findOrCreateName(loggedInConnection, searchName);
                } else {
                    name = nameService.findByName(loggedInConnection, searchName);
                }
            }
            if (name != null) {
                for (String header : headers) {
                    if (header.length() > 0) {
                        String attName = header;
                        if (header.toLowerCase().equals("name")){
                            attName = name.DEFAULT_DISPLAY_NAME;
                        }
                        final String newName = getFirstName(csvReader.get(header));
                        final String oldName = name.getAttribute(attName);
                        if ((oldName == null && newName.length() > 0) || (oldName != null && !newName.equals(oldName))){
                            if (newName.length()==0){
                                name.removeAttributeWillBePersisted(attName);
                            }else{
                                name.setAttributeWillBePersisted(attName, getFirstName(newName));
                            }
                        }
                    }
                }

            }
        }
        return "";
    }

    // I'm guessing from the internet?

    public String unzip(final String zipFile) {

        byte[] buffer = new byte[1024];
        File newFile = null;
        try {
            final String outputFolder = zipFile.substring(0, zipFile.lastIndexOf(File.separator));
            //get the zip file content
            final ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            //get the zipped file list entry
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                final String fileName = ze.getName();
                newFile = new File(outputFolder + File.separator + fileName);
                System.out.println("file unzip : " + newFile.getAbsoluteFile());
                //create all non exists folders
                //else you will hit FileNotFoundException for compressed folder
                new File(newFile.getParent()).mkdirs();
                final FileOutputStream fos = new FileOutputStream(newFile);
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
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return newFile.getPath();
    }


    private String getFirstName(final String nameGiven) {
        if (nameGiven == null) return null;
        int commaPos = nameGiven.indexOf(",");
        if (commaPos < 0) return nameGiven;
        int quotePos = nameGiven.indexOf("`");
        if (quotePos < commaPos && quotePos >= 0) {
            int endQuote = nameGiven.indexOf("`", quotePos + 1);
            return nameGiven.substring(quotePos + 1, endQuote);
        }
        return nameGiven.substring(0, commaPos);
    }


}
