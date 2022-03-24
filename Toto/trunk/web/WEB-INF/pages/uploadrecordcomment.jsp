<%@ page contentType="text/html;charset=UTF-8" language="java" %><%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
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
        # a:link {color:${bannerColor}}
        a:visited {color:${bannerColor}}

    </style>
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