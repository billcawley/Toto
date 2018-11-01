// Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
// just a test file to prove the point that we can preprocess using groovy

import com.azquo.dataimport.ValuesImport
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import com.azquo.memorydb.core.*;
import com.azquo.memorydb.service.*;
import com.azquo.memorydb.*;
def fileProcess(Object[] args) {
    // loose typing seems to be what's required here
    ValuesImportConfig valuesImportConfig = (ValuesImportConfig) args[0];
    String filePath = valuesImportConfig.getFilePath();
    AzquoMemoryDBConnection azquoMemoryDBConnection = valuesImportConfig.getAzquoMemoryDBConnection();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // if you don't zap the append will do as it says and append to an existing file
    def lineNo = 1
    def line
    Name test = NameService.findByName(azquoMemoryDBConnection, "Client");
    println("found a name " + test)
    fileWriter = writeFile.newWriter();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            def split = line.split(",")
            def col = 1;
            for (String cellvalue : split) {
                if (col > 1){
                    fileWriter.write("\t")
                }
                if (col == 2){ // then process the url
                    cellvalue = cellvalue.replace("\"", "").trim();
                    def found = false;
                    try {
                        List<NameValuePair> result = URLEncodedUtils.parse(new URI(cellvalue), "UTF-8")
                        for (NameValuePair nvp : result) {
                            if (nvp.getName().equals("color")){
                                found = true;
                                fileWriter.write(nvp.getValue());
                                break
                            }
                        }
                    } catch (java.net.URISyntaxException e){
                        println(e.message);
                    }
                    if (!found) fileWriter.write("-")
                } else {
                    fileWriter.write(cellvalue);
                }
                col++
            }
            if (lineNo%1000 == 0){
                println(lineNo)
            }
            fileWriter.write("\r\n")
            lineNo++
        }
    }
    fileWriter.flush();
    fileWriter.close();

    return outFile
}