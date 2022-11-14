
def fileProcess(Object[] args) {
    String filePath = args[0];
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // to avoid confusion
    def lineNo = 1
    def line
    fileWriter = writeFile.newWriter();
    file.withReader { reader ->
        while ((line = reader.readLine()) != null) {
            String[] split = line.split("\t")
            def col = 0;
            String endBit = "";
            for (String cellvalue : split) { // copied from example groovy this should be fine.
                if (col > 0){
                    fileWriter.write("\t")
                }
                if (lineNo > 1 && col == 5){
                    if (cellvalue.startsWith("P")){
                        endBit = "\t" + split[7];
                    }
                    if (cellvalue.startsWith("S")){
                        endBit = "\t\t" + split[7];
                    }
                } else {

                }
                col++;
                fileWriter.write(cellvalue);
            }
            while (col < 24){
                fileWriter.write("\t");
                col++;
            }
            fileWriter.write(endBit);
            if (lineNo%1000 == 0){
                println(lineNo)
            }
            if (lineNo != 1){
                fileWriter.write("\t\t\t");
            }
            fileWriter.write("\r\n")
            lineNo++
        }
    }
    fileWriter.close();
    return outFile
}
