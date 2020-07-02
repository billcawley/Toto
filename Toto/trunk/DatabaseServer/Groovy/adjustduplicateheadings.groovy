/*
Simply adjusts duplicate headings so they are e.g. address address1 address2 address2 rather than address address address for example
*/

import com.azquo.spreadsheet.transport.UploadedFile

def fileProcess(Object[] args) {
    // now do it again
    println("adjusting duplicate headings")
    int lineNo = 1
    UploadedFile uploadedFile = (UploadedFile) args[0];
    String filePath = uploadedFile.getPath();
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    String line
    fileWriter = writeFile.newWriter();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            if (lineNo == 1){
                StringTokenizer st = new StringTokenizer(line, "\t");
                Set<String> headings = new HashSet<>();
                int colNum = 0;
                while (st.hasMoreTokens()) {
                    if (colNum != 0){
                        fileWriter.write("\t");
                    }
                    String heading = st.nextToken().toLowerCase();
                    if (!heading.isEmpty()){
                        int num = 1;
                        if (headings.contains(heading)){
                            while (headings.contains(heading + num)){
                                num++;
                            }
                            heading = heading + num;
                        }
                        fileWriter.write(heading);
                        headings.add(heading);
                    }
                    colNum++;
                }
            } else {
                fileWriter.write(line);
            }
            fileWriter.write("\r\n");
            lineNo++
        }
        fileWriter.flush();
        fileWriter.close();
    }
    return outFile
}