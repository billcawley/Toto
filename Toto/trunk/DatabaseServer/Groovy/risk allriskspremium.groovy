/*


Need to check for where there's more than one contract line and assign a section column at the end

*/
import com.azquo.dataimport.ValuesImportConfig

def fileProcess(Object[] args) {
    // now do it again
    println("risk all risks premium running ")
    def lineNo = 1
    int headingsLine = 0;
    ValuesImportConfig valuesImportConfig = (ValuesImportConfig) args[0];
    String filePath = valuesImportConfig.getFilePath();
    //AzquoMemoryDBConnection azquoMemoryDBConnection = valuesImportConfig.getAzquoMemoryDBConnection();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    String line
    int contractNumCol = 0;
    int contractPremCol = 0;
    int agreementCol = 0;
    List<String> linesToSort = new ArrayList<>();
    fileWriter = writeFile.newWriter();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            if (line.trim().contains("\t") && headingsLine == 0){ // for the moment we'll assume the headings are here
                println("read line " + line)
                headingsLine = lineNo
                StringTokenizer st = new StringTokenizer(line, "\t");
                int colNum = 0;
                while (st.hasMoreTokens()){
                    String col = st.nextToken()
                    if (col.equalsIgnoreCase("agmt_num")){
                        agreementCol = colNum
                    }
                    if (col.equalsIgnoreCase("contract_num")){
                        contractNumCol = colNum
                    }
                    if (col.equalsIgnoreCase("written_prem")){
                        contractPremCol = colNum
                    }
                    colNum++;
                }
                fileWriter.write(line + "\tLine")
                fileWriter.write("\r\n")
            } else if (headingsLine != 0){ // ok we're into data
                if (contractPremCol >=0 && line[contractPremCol].length() < 8){
                    valuesImportConfig.getFileNameParameters().put("import template","risk allriskspremium1");

                }
                if (!line.trim().isEmpty()){
                    linesToSort.add(line);
                }
            } else {
                // copying the top until data we need to sort
                fileWriter.write(line)
                fileWriter.write("\r\n")
            }
            lineNo++
        }
    }
    // now, sort!
    Collections.sort(linesToSort, new Comparator<String>() {
        @Override
        int compare(String s, String t1) {
            def split1 = s.split("\t")
            def split2 = t1.split("\t")
            if (!split1[contractNumCol].equals(split2[contractNumCol])){
                return split1[contractNumCol].compareTo(split2[contractNumCol])
            } else { // they match! numeric compare on the
                if (split1[contractPremCol].length() > 0 && split2[contractPremCol].length() > 0){
                    Double first = new Double(split1[contractPremCol]);
                    Double second = new Double(split2[contractPremCol]);
                    return second.compareTo(first); // reverse - largest no first
                }
            }
            return  0
        }
    })
    fileWriter.flush();
    String previousContract = null;
    int lineCount = 1;
    for (String sortedLine : linesToSort){
        fileWriter.write(sortedLine)
        def split1 = sortedLine.split('\t')
        contract = split1[contractNumCol];
        if (contract.equals(previousContract)){
            lineCount++;
        } else {
            lineCount = 1;
        }
        previousContract = contract;
        fileWriter.write("\t" + lineCount + "\r\n")
    }
    fileWriter.flush();
    fileWriter.close();
    return outFile
}