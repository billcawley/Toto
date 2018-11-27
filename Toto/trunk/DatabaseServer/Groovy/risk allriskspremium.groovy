/*


Need to check for where there's more than one contract line and assign a section column at the end

*/
import com.azquo.dataimport.ValuesImportConfig
import com.azquo.StringLiterals

import java.text.SimpleDateFormat

def fileProcess(Object[] args) {
    // now do it again
    SimpleDateFormat df = new SimpleDateFormat("yyyy-mm-dd");
    SimpleDateFormat usdf = new SimpleDateFormat("mm/dd/yyyy");
    println("risk all risks premium running ")
    def lineNo = 1
    int topLine = 0;
    ValuesImportConfig valuesImportConfig = (ValuesImportConfig) args[0];
    String filePath = valuesImportConfig.getUploadedFile().getPath();
    //AzquoMemoryDBConnection azquoMemoryDBConnection = valuesImportConfig.getAzquoMemoryDBConnection();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    String line
    int contractNumCol = -1;
    int contractPremCol = -1;
    int agreementCol = -1;
    int expDateCol = -1;
    List<String> linesToSort = new ArrayList<>();
    fileWriter = writeFile.newWriter();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            if (line.trim().contains("\t") && topLine == 0){ // for the moment we'll assume the headings are here
                println("read line " + line)
                StringTokenizer st = new StringTokenizer(line, "\t");
                int colNum = 0;
                while (st.hasMoreTokens()){
                    String col = st.nextToken().toLowerCase();
                    if (col.equals("agmt_num")){
                       topLine = lineNo;
                       agreementCol = colNum

                    }
                    if (col.equals("contract_num")){
                        contractNumCol = colNum
                    }
                    if (col.equals("written_prem")){
                        contractPremCol = colNum
                    }
                    if (col.equals("exp_date")){
                        expDateCol = colNum;
                    }
                    colNum++;
                }
                if (expDateCol> 0) {
                    fileWriter.write(line + "\tInception_date\tLine");
                    fileWriter.write("\r\n");
                }
            } else if (agreementCol >= 0) { // ok we're into data
                if (lineNo == topLine + 1) {
                    if (agreementCol >= 0 && line.size() > agreementCol && line[agreementCol] != 'B') {
                        valuesImportConfig.getFileNameParameters().put("import template", "risk allriskspremium1");
                        List<String> languages = new ArrayList<>();
                        languages.add("allriskspremium1");
                        languages.add(StringLiterals.DEFAULT_DISPLAY_NAME);
                        valuesImportConfig.setLanguages(languages);

                    }
                }
                if (!line.trim().isEmpty()){
                    //work out inception date....
                    def split1 = line.split("\t")
                    String exp_date = split1[expDateCol];
                    Calendar c = Calendar.getInstance();
                    try{
                        c.setTime(df.parse(exp_date));
                    }catch(Exception e1) {
                        try {
                            //if the original cell was not formatted as a date, the date will be in US format
                            c.setTime(usdf.parse(exp_date))
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new Exception(e);
                        }
                    }
                    c.add(Calendar.YEAR,-1);
                    c.add(Calendar.DAY_OF_MONTH,1);
                    line += "\t" + df.format(c.getTime());
                    linesToSort.add(line);
                }
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