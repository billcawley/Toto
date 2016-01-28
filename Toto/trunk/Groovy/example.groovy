import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
def fileProcess(def filePath) {
    File file = new File(filePath);
    def outFile = filePath + "groovyout"
    File writeFile = new File(outFile);
    writeFile.delete() // if you don't zap the append will do as it says and append to an existing file
    def lineNo = 1
    def line
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
    return outFile
}

println fileProcess("C:\\Users\\Bill\\Downloads\\log_url_info.csv")
