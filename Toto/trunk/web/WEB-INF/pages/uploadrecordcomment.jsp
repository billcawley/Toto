<%@ page contentType="text/html;charset=UTF-8" language="java" %><%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
    <script src="//cdnjs.cloudflare.com/ajax/libs/jquery/1.11.1/jquery.min.js"></script>
    <!--    <script src="//cdnjs.cloudflare.com/ajax/libs/jstree/3.0.4/jstree.min.js"></script> -->
    <script src="/jstree/jstree.js"></script>
    <link rel="stylesheet" href="/css/themes/proton/style.min.css" />
    <!-- <link rel="stylesheet" href="//cdnjs.cloudflare.com/ajax/libs/jstree/3.0.4/themes/default/style.min.css" />-->
    <link rel="stylesheet" href="/jstree/themes/default/style.css" />
    <link href="/css/style.css" rel="stylesheet" type="text/css">
    <!-- <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css"> -->
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <script>
        ${script}
        function submitClose(){
            document.getElementById("upload").submit();
            /*$.post("/api/UploadRecordComment", {
                urid: document.getElementById('urid').value,
                comment: document.getElementById('comment').value
            }).then(window.parent.$['inspectOverlay']().close());*/
        }
    </script>
</head>
<body class="jstree">
<form method="post" id="upload" enctype="multipart/form-data" name="upload" action="/api/UploadRecordComment">
    <div class="centeralign">
        <input type="hidden" name="urid" value="${urid}" id="urid"/>
        <textarea name="comment" cols="80" rows="10"  id="comment">${comment}</textarea>
        <br/>
        <a href="#" onclick="submitClose(); return false;" class="button" >Save</a>
    </div>
</form>
</body>
</html>