/*

Groovy for Ed Broking - hack the claim state from a "total" style row to a column, a row example is

Totals For:																											AL	2,270.71	1,321.80	.00	3,592.51	258,450.27	15,609.96	.00	274,060.23	19,741.98	1,605.00	.00	21,346.98	14,512.69	(207.27)	278,192.25	17,214.96	295,407.21

so we have to make the last column Claim State showing this as appropriate, there will be multiple of these totals

Also chop the first 4 lines

*/
import com.azquo.spreadsheet.transport.UploadedFile


def fileProcess(Object[] args) {
    UploadedFile uploadedFile = (UploadedFile) args[0];
    String filePath = uploadedFile.getPath();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    String line
    List states = new ArrayList()
    int skipLines = 0;
    file.withReader { reader ->
        int lineNo = 0;
        while ((line = reader.readLine()) != null) {
            if (line.toLowerCase().startsWith("totals for") && !line.contains("UMR")) { // so a line we want to get the state off
                StringTokenizer st = new StringTokenizer(line, "\t");
                //println(line)
                while (st.hasMoreElements()){
                    st.nextToken()
                    state = st.nextToken()
                    if (state.length()==2){
                        //println("found a state " + state)
                        states.add(state);
                        break;
                    }
                }
            }
            if (line.startsWith("Coverholder")){
                skipLines = lineNo;
            }
            lineNo++;
        }
    }
    // now do it again
    def lineNo = 1
    fileWriter = writeFile.newWriter();
    Iterator<String> statesIt = states.iterator();
    String state = statesIt.next();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            if (line.toLowerCase().startsWith("totals for")  && !line.contains("UMR") && statesIt.hasNext()) { // go next state!
                state = statesIt.next();
            }
            if (lineNo > skipLines){
                if (lineNo == (skipLines + 1)){ // top line, add the header
//                    fileWriter.write(line.replace("\\\\n", " ") + "\tClaim State"); // don't replace - bugger's up new heading lookups
                    fileWriter.write(line + "\tClaim State");
                } else {
                    if (!line.trim().isEmpty()){
                        fileWriter.write(line + "\t" + state);
                    }
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