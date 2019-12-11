/*

new columns -
contract year extract from supplier_agmt

section is LOB unless supplier_agmt ends with a space and letter in which case use that

*/

import com.azquo.StringLiterals
import com.azquo.spreadsheet.transport.UploadedFile

def fileProcess(Object[] args) {
    // now do it again
    println("hanover premium running ")
    int lineNo = 1
    UploadedFile uploadedFile = (UploadedFile) args[0];
    String filePath = uploadedFile.getPath();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    String line
    int LOBNumCol = -1;
    int supplier_agmtCol = -1;
    fileWriter = writeFile.newWriter();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            fileWriter.write(line);
            if (line.trim().contains("\t") && lineNo == 1) { // for the moment we'll assume the headings are here
                println("read line " + line)
                StringTokenizer st = new StringTokenizer(line, "\t");
                int colNum = 0;
                while (st.hasMoreTokens()) {
                    String col = st.nextToken().toLowerCase();
                    if (col.equals("lob")) {
                        LOBNumCol = colNum
                    }
                    if (col.equals("supplier_agmt")) {
                        supplier_agmtCol = colNum
                    }
                    colNum++;
                }
                fileWriter.write("\tcontract year\tsection");
            } else if (LOBNumCol >= 0 && supplier_agmtCol >= 0) {
                // ok we're into data but only try to change stuff if we found the columns
                //work out inception date....
                def split1 = line.split("\t");
                if (split1.length > LOBNumCol && split1.length > supplier_agmtCol) {
                    String section = split1[LOBNumCol];
                    String contractYear = split1[supplier_agmtCol];
                    if (contractYear.contains(" ")) {
                        section = contractYear.substring(contractYear.indexOf(" ") + 1);
                        contractYear = contractYear.substring(0, contractYear.indexOf(" "));
                    }
                    int bbindex = contractYear.toLowerCase().indexOf("bb")
                    if (bbindex > 0) {
                        contractYear = contractYear.substring(bbindex);
                    }
                    fileWriter.write("\t" + contractYear + "\t" + section);
                }
            }
            fileWriter.write("\r\n");
            lineNo++
        }
        fileWriter.flush();
        fileWriter.close();
    }
    return outFile
}