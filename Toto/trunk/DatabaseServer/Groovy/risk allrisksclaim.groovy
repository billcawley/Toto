/*

Groovy for Ed Broking - hack the claim state from a "total" style row to a column, a row example is

Totals For:																											AL	2,270.71	1,321.80	.00	3,592.51	258,450.27	15,609.96	.00	274,060.23	19,741.98	1,605.00	.00	21,346.98	14,512.69	(207.27)	278,192.25	17,214.96	295,407.21

so we have to make the last column Claim State showing this as appropriate, there will be multiple of these totals

Also chop the first 4 lines

*/
import com.azquo.dataimport.ValuesImportConfig


def fileProcess(Object[] args) {
    ValuesImportConfig valuesImportConfig = (ValuesImportConfig) args[0];
    String filePath = valuesImportConfig.getUploadedFile().getPath();
    //AzquoMemoryDBConnection azquoMemoryDBConnection = valuesImportConfig.getAzquoMemoryDBConnection();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    String line
    List states = new ArrayList()
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("Totals For:")) { // so a line we want to get the state off
                StringTokenizer st = new StringTokenizer(line, "\t");
                st.nextToken()
                state = st.nextToken()
                println("found a state " + state)
                states.add(state);
            }
        }
    }
    // now do it again
    def lineNo = 1
    int skipLines = 4;
    fileWriter = writeFile.newWriter();
    Iterator<String> statesIt = states.iterator();
    String state = statesIt.next();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("Totals For:") && statesIt.hasNext()) { // go next state!
                state = statesIt.next();
            }
            if (lineNo > skipLines){
                if (lineNo == (skipLines + 1)){ // top line, add the header
                    fileWriter.write(line.replace("\\\\n", " ") + "\tClaim State");
                } else {
                    fileWriter.write(line + "\t" + state);
                }
                fileWriter.write("\r\n")
            }
            lineNo++
        }
    }
    fileWriter.flush();
    fileWriter.close();
    return outFile
}