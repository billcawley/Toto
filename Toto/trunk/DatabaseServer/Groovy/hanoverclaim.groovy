/*

new columns - 3 from column d

e.g.

B0702BB014390M Section A CAT 1954
B0702BB014390M Section A


get the contract year as with premiums - BB014390M
section, the next bit Section A
PCS code if it's there CAT 1954

section is LOB unless supplier_agmt ends with a space and letter in which case use that

*/

import com.azquo.StringLiterals
import com.azquo.spreadsheet.transport.UploadedFile

def fileProcess(Object[] args) {
    // now do it again
    println("hanover claims running ")
    int lineNo = 1
    UploadedFile uploadedFile = (UploadedFile) args[0];
    String filePath = uploadedFile.getPath();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    String line
    int sourcecol = -1;
    fileWriter = writeFile.newWriter();
    String colName = "unique market reference (umr) / policy number";
    boolean found = false;
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            fileWriter.write(line);
            if (!found && line.toLowerCase().contains(colName)) { // I guess this is the heading line then
                found = true;
                println("read line " + line)
                StringTokenizer st = new StringTokenizer(line, "\t");
                int colNum = 0;
                while (st.hasMoreTokens()) {
                    String col = st.nextToken().toLowerCase();
                    if (col.equalsIgnoreCase(colName)) {
                        sourcecol = colNum
                    }
                    colNum++;
                }
                fileWriter.write("\tcontract year\tsection\tPCS Code");
            } else if (sourcecol >= 0) {
                // this as premium but we need to get the pcs code too
                def split1 = line.split("\t");
                if (split1.length > sourcecol) {
                    String contractYear = split1[sourcecol];
                    String section = "";
                    String PCSCode = "";
                    if (contractYear.contains(" ")) {
                        section = contractYear.substring(contractYear.indexOf(" ") + 1);
                        contractYear = contractYear.substring(0, contractYear.indexOf(" "));
                    }
                    int bbindex = contractYear.toLowerCase().indexOf("bb")
                    if (bbindex > 0) {
                        contractYear = contractYear.substring(bbindex);
                    }
                    if (section.length() > 9){ // then we assume a PCS code
                        PCSCode = section.substring(9).trim();
                        section = section.substring(0, 9).trim();
                    }
                    fileWriter.write("\t" + contractYear + "\t" + section + "\t" + PCSCode);
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