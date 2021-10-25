<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Validating</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
    <kkjsp:head/>
</head>
<%
    // a tweaked copy of zsloading - now I have two copies I really must factor
    // prevent page cache in browser side
    response.setHeader("Pragma", "no-cache");
    response.setHeader("Cache-Control", "no-store, no-cache");
%>

<script type="text/javascript">
    // edd changed to call every second, no skip maker
    var something = true;

    function updateStatus() {
        if (something) {
            jq.post("/api/SpreadsheetStatus?action=importResult", function (data) {
                if (data.indexOf("true") == 0) { // the sheet should be ready, note indexof not startswith, support for the former better
                    document.getElementById("mainform").submit();
                    //location.reload();
                    something = false;
                    return;
                } else { // possible headline info
                    document.getElementById("headline").innerHTML = "<h1>" + data + "</h1>"
                }
            });
            jq.post("/api/SpreadsheetStatus?action=log", function (data) {
                var objDiv = document.getElementById("serverStatus");
                if (objDiv.innerHTML != data) { // it was updated
                    objDiv.innerHTML = data;
                    objDiv.style.backgroundColor = '#EEFFEE'; // highlight the change
                    objDiv.scrollTop = objDiv.scrollHeight;
                    // assume there could be more stuff!
                } else {
                    objDiv.style.backgroundColor = 'white';
//                alert("same data, new skip setting : " + window.skipSetting);
                }
            });
        }
    }

    setInterval(function () {
        updateStatus();
    }, 1000);

</script>
<form action="/api/PendingUpload" method="post" id="mainform">
    <input type="hidden" name="id" value="${id}"/>
    ${paramspassthrough}
</form>

<section class="section">
    <section class="hero ">
        <div class="hero-body">
            <div class="container">
                <div class="columns is-centered">
                    <div class="column is-5-tablet is-4-desktop is-3-widescreen">
                        <nav class="panel is-black">
                            <p class="panel-heading ">
                                <a href="/api/Online?reportid=1"><img src="${logo}" alt="azquo"></a>
                            </p>
                            <div class="container has-text-centered" style="padding:10px;"><h3 class="title is-4">Running validation&nbsp;<span class="fa fa-spin fa-cog"></span></h3></div>
                            <div class="container has-text-centered" id="serverStatus" style="height:200px; width:100%;font:10px monospace;overflow:auto;padding:10px"></div>
                        </nav>
                    </div>
                </div>
            </div>
        </div>
    </section>
</section>
</body>
</html>