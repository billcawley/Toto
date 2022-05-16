<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Loading</title>
    <link rel="stylesheet" href="/sass/mystyles.css">
    <!-- <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css"> -->
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
    <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
    <kkjsp:head/>
</head>
<script type="text/javascript">

    var skipSetting = 0;
    var skipMarker = 5;
    // how to stop this hammering? I reckon add a second every time between checks if the data hasn't changed.
    function updateStatus(){
        jq.post("/api/SpreadsheetStatus?action=sheetReady&reportid=${reportid}", function(data){
            var objDiv = document.getElementById("serverStatus");
            if ("true" == data){ // the sheet should be ready
                location.reload();
                return;
            }
        });


        if (window.skipMarker <= 0){
            jq.post("/api/SpreadsheetStatus?action=log", function(data){
                var objDiv = document.getElementById("serverStatus");
                if (objDiv.innerHTML != data){ // it was updated
                    objDiv.innerHTML = data;
                    objDiv.style.backgroundColor = '#EEFFEE'; // highlight the change
                    objDiv.scrollTop = objDiv.scrollHeight;
                    // assume there could be more stuff!
                    window.skipSetting = 0;
                    window.skipMarker = 0;
                    document.getElementById("chosen").innerHTML = extractChoices(data);
                } else {
                    objDiv.style.backgroundColor = 'white';
                    if (window.skipSetting == 0){
                        window.skipSetting = 1;
                    } else {
                        window.skipSetting *= 2;
                    }
//                alert("same data, new skip setting : " + window.skipSetting);
                    window.skipMarker = window.skipSetting;
                }
            });
        } else {
            window.skipMarker--;
        }
    }

    function extractChoices(data){
        var choices = new Map();
        const dataLines = data.split("<br>");
        var firstline = true;
        for (var dataLine of dataLines) {
            if (firstline) {
                firstline = false;//first line may be chopped
            } else {
                var choice = dataLine.split(" : ");
                if (choice.length > 1 && choice[0].indexOf(" finishing") < 0) {
                    choices.set(choice[0].trim(), choice[1].trim());
                }
            }
        }
        var toReturn = "";
        for (let ch of choices.keys()){
            toReturn+= ch + " = " + choices.get(ch) + "<br/>";
        }
        return toReturn;
    }

    setInterval(function(){ updateStatus(); }, 1000);

</script>

<body>
<section class="section">
    <section class="hero ">
        <div class="hero-body">
            <div class="container">
                <div class="columns is-centered">
                    <div class="column is-5-tablet is-4-desktop is-3-widescreen">
                        <nav class="panel is-black">
                            <p class="panel-heading" style="background-color:${bannerColor}">
                                <a href="/api/Online?reportid=1"><img src="${logo}" alt="azquo"></a>
                            </p>
                            <div class="container has-text-centered" style="padding:10px;"><h3 class="title is-3">Loading&nbsp;<span class="fa fa-spin fa-cog"></span></h3></div>
                            <div id="chosen" style="height:45px; width:100%;font:10px monospace;overflow:auto;"></div>
                            <div id="serverStatus" style="height:145px; width:100%;font:10px monospace;overflow:auto;"></div>
                            <div class="container has-text-centered" style="padding:10px;"><a href="javascript:void(0)" id="abort" onclick='jq.post("/api/SpreadsheetStatus?action=stop", null)' class="button alt small"><span class="fa fa-times-circle"></span>&nbsp;Abort Load</a></div>
                        </nav>
                    </div>
                </div>
            </div>
        </div>
    </section>
</section>


</body>
</html>