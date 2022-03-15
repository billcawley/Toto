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
    <link rel="stylesheet" href="/sass/mystyles.css">
    <!-- <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css"> -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <!-- required for inspect - presumably zap at some point -->
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
    <link href="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/themes/black-tie/jquery-ui.css" rel="stylesheet" type="text/css">
    <script type="text/javascript" src="/js/global.js"></script>
    <style>
        .ui-dialog .ui-tabs-panel{min-height:350px; background:#ECECEC; padding:5px 5px 0px 5px; }
        .ui-dialog .ui-tabs-panel iframe{min-height:350px; background:#FFF;}

        header .nav ul li a.on {background-color:${bannerColor}}
        .ui-widget .ui-widget-header li.ui-state-active {background-color:${bannerColor}}
        a:link {color:${bannerColor}}
        a:visited {color:${bannerColor}}

    </style>
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

        function showHideDiv(div) {
            var x = document.getElementById(div);
            if (x.style.display === "none") {
                x.style.display = "block";
            } else {
                x.style.display = "none";
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
            <div id="working" class="loading" style="display:none">
                <div class="loader"><span class="fa fa-spin fa-cog"></span><h3>Working...</h3>
            </div>
            <input type="file" id="uploadfile"  onchange="checkFile()" name="uploadfile" required/>
        </div>
        <div id="imageNameDiv" style="display:none;">
            Save as: <input type="text" name="imagename" id="imagename" style="width:250px;" value=""/>
        </div>
        <div class="centeralign">
            <input type="submit" name="submit" value="Upload" class="button" onclick="showHideDiv('working')" />
        </div>
    </form>
</main>
</div>
</body>
</html>