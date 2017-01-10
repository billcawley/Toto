<%--
  Created by IntelliJ IDEA.
  User: edward
  Date: 05/01/17
  Time: 15:50
  To change this template use File | Settings | File Templates.
  Pasted from the .vm file, may need a tweak or two
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
    <script src="//cdnjs.cloudflare.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
    <script src="//cdnjs.cloudflare.com/ajax/libs/jstree/3.0.4/jstree.min.js"></script>
    <link rel="stylesheet" href="/css/themes/proton/style.min.css" />
    <link rel="stylesheet" href="//cdnjs.cloudflare.com/ajax/libs/jstree/3.0.4/themes/default/style.min.css" />
    <link href="/css/style.css" rel="stylesheet" type="text/css">
    <script>
        function checkFile(){
            var fileName = document.getElementById("uploadfile").value;
            if (fileName.match(/[^/]+(jpg|png|gif|pdf)$/) > "")    {
                document.getElementById("imageNameDiv").style.display = "block";
                var divider = "/";
                if (fileName.indexOf("\\") > 0) divider = "\\";
                while (fileName.indexOf(divider) > 0) {
                    fileName = fileName.substring(fileName.indexOf(divider) + 1);
                }
                document.getElementById("imagename").value = fileName.substring(0, fileName.indexOf("."));
            }
        }
    </script>
</head>
<body>
<main>
    <form method="post" id="upload" enctype="multipart/form-data" name="upload">
        <h2>Upload file</h2>
        <input type="hidden" name="database" value="$database"/>
        <input type="hidden" name="imagestorename" value="$imagestorename"/>

        <div>
            <label for="uploadfile">Choose the file</label>
            <input type="file" id="uploadfile"  onchange="checkFile()" name="uploadfile" required/>
        </div>
        <div id="imageNameDiv" style="display:none;">
            Save as: <input type="text" name="imagename" id="imagename" style="width:250px;" value=""/>
        </div>
        <div class="centeralign">

            <input type="submit" name="submit" value="Upload" class="button" />
        </div>
    </form>
</main>
</div>
</body>
</html>