/* Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
first proper use of the groovy functionality. Spec emailed to me was :


if Column E holds a value starting 'P'  this is a purchase invoice - fill the first spare column with the contents of column G
if Column E holds a value starting 'S' this is a sales invoice         - fill the second spare column with the contents of Column G
else leave the two columns blank.

*/

import com.azquo.memorydb.service.*;
def fileProcess(Object[] args) {
    // loose typing seems to be what's required here
    String filePath = args[0];
//    AzquoMemoryDBConnection azquoMemoryDBConnection = (AzquoMemoryDBConnection)args[1];
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    def lineNo = 1
    def line
//    Name test = nameService.findByName(azquoMemoryDBConnection, "Client");
    println("found a name " + test)
    fileWriter = writeFile.newWriter();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            String[] split = line.split(",")
            def col = 1;
            String endBit = "";
            for (String cellvalue : split) { // copied from example groovy this should be fine.
                if (col > 1){
                    fileWriter.write("\t")
                }
                if (col == 5){
                    if (cellvalue.startsWith("P")){
                        endBit = "\t" + split[7];
                    }
                    if (cellvalue.startsWith("S")){
                        endBit = "\t\t" + split[7];
                    }
                } else {
                }
                fileWriter.write(cellvalue);
                col++
            }
            fileWriter.write(endBit);
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