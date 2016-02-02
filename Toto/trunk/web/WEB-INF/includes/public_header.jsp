<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="zssjsp" uri="http://www.zkoss.org/jsp/zss" %>
<!DOCTYPE html>
<html lang="en-GB">

<head>
	<title>${title} - Azquo</title>
	<meta charset="UTF-8">
	<meta NAME="description" CONTENT="">
	<meta NAME="keywords" CONTENT="">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
	<link href='https://fonts.googleapis.com/css?family=Open+Sans:400,300,700,600' rel='stylesheet' type='text/css'>
	
	<link rel="shortcut icon" href="/favicon.ico" type="image/x-icon">
	<script type="text/javascript" src="https://code.jquery.com/jquery-2.1.4.min.js"></script>
	<script type="text/javascript" src="https://code.jquery.com/ui/1.11.4/jquery-ui.min.js"></script>
	<script type="text/javascript" src="/js/global.js"></script>
	
	<link href="https://code.jquery.com/ui/1.11.4/themes/black-tie/jquery-ui.css" rel="stylesheet" type="text/css">
	
	<c:if test="${requirezss}">
		<zssjsp:head/>
	</c:if>
	
	<link href="/css/style.css" rel="stylesheet" type="text/css">
	<script>
		function imageSelected(){
			var path = document.getElementById("imageId").value;
			if (path > ""){
				window.open("/api/Download?image=" + encodeURI(path));
			}
		}

	</script>
</head>

<body>

<header class="public">
	<div class="headerContainer">
	<div class="logo">
		<a href="/api/Online?opcode=loadsheet&reportid=1"><img src="/images/logo_alt.png" alt="azquo"></a>
	</div>
		<c:if test="${requirezss}">
			<c:if test="${images.size() > 0}">
				<div id="imagelist" style="position:absolute;left:200px;top:20px">
					<label for="imageList">Images</label>

					<select name="imageId" id="imageId" onchange = "imageSelected()">
						<option value="" selected>select to show</option>
						<c:forEach items="${images}" var="image">
							<option value="${image.value}">${image.key}</option>
						</c:forEach>
					</select>


				</div>


			</c:if>
			<c:if test="${imagestorename.length()>0}">
				<a onclick="uploadFile()" style="position:relative;top:20px">Upload image</a>
			</c:if>

			<nav class="nav">


				<ul>
					<li id="saveDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if>><a href="#" onclick="postAjax('Save'); return false;">Save Data</a></li>
					<li id="restoreDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if>><a href="#" onclick="postAjax('RestoreSavedValues'); return false;">Restore Saved Values</a></li>
					<li id="logoff"><a href="/api/Login?logoff=true">Log Off</a></li>
					<li><a href="#"><span class="fa fa-bars"></span></a>
						<ul>
							<li><a href="#" onclick="postAjax('XLS'); return false;" title="Download as XLSX (Excel)"><span class="fa fa-file-excel-o"></span> Download as XLSX (Excel)</a></li>
							<li><a href="#" onclick="postAjax('PDF'); return false;" title="Download as PDF"><span class="fa fa-file-pdf-o"></span> Download as PDF</a></li>
							<li><a href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span> Inspect database</a></li>
							<li><a href="#" onclick="return uploadFile();" title="Upload file"><span class="fa fa-cloud-upload"></span> Upload file</a></li>
							<li><a href="#" onclick="return postAjax('FREEZE');" title="Upload file"><span class="fa fa-link"></span> Freeze</a></li>
							<li><a href="#" onclick="return postAjax('UNFREEZE');" title="Upload file"><span class="fa fa-unlink"></span> Unfreeze</a></li>
						</ul>
					</li>
				</ul>
			</nav>
		</c:if>
	</div>
</header>
