//javascript for Azquo DC
var az_chosen;
az_chosen = "";
az_logon = "demo@user.com";
az_password = "password";
az_database = "export";



function azquojson(functionName, params){
    if (functionName == "Name"){
        params = "json={" + params + ",\"user\":\"" +  az_logon + "\",\"password\":\"" + az_password + "\",\"database\":\"" + az_database + "\",\"jsonFunction\":\"azquojsonfeed\"}";
    }else{
        params +="&user=" + az_logon + "&password=" +  az_password + "&database=" + az_database;
    }
    var htmlText = "https://data.azquo.com/api/" + functionName + "?" + params;
    var script = document.createElement('script'),
    head = document.getElementsByTagName('head')[0] || document.documentElement;
    script.src = htmlText;
    head.appendChild(script);
}




function azquojsontest(functionName, params){
    if (functionName == "Name"){
        params = "json={" + params + ",\"user\":\"" +  az_logon + "\",\"password\":\"" + az_password + "\",\"database\":\"" + az_database + "\",\"jsonFunction\":\"azquojsonfeed\"}";
    }else{
        params +="&user=" + az_logon + "&password=" +  az_password + "&database=" + az_database;
    }
    var htmlText = "https://data.azquo.com:8443/api/" + functionName + "?" + params;
    var script = document.createElement('script'),
        head = document.getElementsByTagName('head')[0] || document.documentElement;
    script.src = htmlText;
    head.appendChild(script);
}




function azquojsonfeed(obj) {


    var data = obj.data;
    if (data > ""){
        var htmlText = "<table>";
        for (i = 0;i < data.length;i++){
            htmlText += "<tr>";
            var datalabel = "";
            for (j = 1;j < data[i].length; j++){
                if (data[i][j] > "") {
                    datalabel += "`" +  data[i][j] + "`,";
                }
            }
            for (j = 0;j < data[i].length; j++){
                if (j > 0){
                    if (data[i][j] > ""){
                        datalabel  = "`" +  data[i][j] + "`";
                    }else{
                        datalabel = "";
                    }
                }
                htmlText += "<td onclick=\"az_provenance(this,'" + escape(datalabel) + "')\">" + data[i][j] + "</td>";
            }
            htmlText += "</tr>";
        }
        htmlText += "</table>"
        var azData = document.getElementById("az_Data");
        azData.innerHTML = htmlText;
        azData.style.display = "block";
    }
    var namesFound = obj.names;
    htmlText = "";
    if (namesFound > ""){
        htmlText = showLegend();
        htmlText += az_showSet(namesFound,"");
        var azSelect = document.getElementById("az_Select");
        azSelect.innerHTML = htmlText;
        azSelect.style.display = "block";
    }
    var provenance = obj.provenance;
    if (provenance > ""){
        az_showProvenance(provenance);
    }
}


function azquojsonreportfeed(obj) {


    var reportsFound = obj.reportlist;
    var htmlText = "";
    if (reportsFound > ""){
        htmlText = "<p>Click on the report you want to see</p>";
        htmlText += az_showReports(reportsFound,"");
        var azSelect = document.getElementById("az_Select");
        azSelect.innerHTML = htmlText;
        azSelect.style.display = "block";
    }
}



function showLegend(){
    htmlText = "<p>In the list below, you can click on any item with elements to see the elements</p>";
     return htmlText;
}


function showDetails(nameFound){
    if (nameFound.mydataitems != null){
        return "<span class='item-expandable' onclick=\"az_getdata(this)\"><i class='icon-external-link'></i>" + nameFound.name + "(" + nameFound.mydataitems  + ")</span>";
    }else{
        return "<i class='icon-circle-arrow-down'></i>" + nameFound.name + " (" + nameFound.dataitems + " in " + nameFound.elements + " subsets)";
    }

}



function  az_showSet(namesFound, style){

    var htmlText = "<ul" + style + ">";
    // All dirs
    var count = 0
    while (count < namesFound.length){
        if (namesFound[count].dataitems != "0"){
            if (namesFound[count].children != null){

                htmlText +="<li onclick=\"az_click(this)\">"+ showDetails(namesFound[count]) + "</li>";
                htmlText += az_showSet(namesFound[count].children, " style=\"display:none;\"");
            }else{
                htmlText += "<li class=\"name\">"  + showDetails(namesFound[count]) + "</li>";
            }
        }
        count++;
    }
    htmlText +="</ul>";
    return htmlText;
}



function  az_showReports(reportsFound, style){

    var htmlText = "<ul" + style + ">";
    // All dirs
    var count = 0
    while (count < reportsFound.length){
           var reportFound = reportsFound[count];
           htmlText += "<li class=\"name\"><span class='item-expandable' onclick=\"az_sendReportId(" + reportFound.id + ")\"><i class='icon-external-link'></i>"  +reportFound.database + ":" + reportFound.reportName + "</span></li>";
        count++;
    }
    htmlText +="</ul>";
    return htmlText;
}



function az_showProvenance(allprovenance){
    var provenance = "Provenance:<br/><br/>";
    var lastPerson = ""
    var lastTime = ""
    for (var i = 0;i <allprovenance.length;i++){
        var oneProv = allprovenance[i];

        if (oneProv.when != lastTime || oneProv.who != lastPerson){
           lastPerson = oneProv.who;
           lastTime = oneProv.when;
           provenance = provenance + "updated by: " + lastPerson +  " at " + lastTime + " - " + oneProv.how + " " + oneProv.where + "<br/>";
        }
        //provenance = provenance + "<br/>" +  oneProv.value + "  ";
        //var provNames = oneProv.names;
        //for (var j = 0; j< provNames.length; j++){
        //    provenance = provenance + provNames[j] + " ";
        //}
    }
    document.getElementById("az_provenanceinfo").innerHTML = provenance;
    document.getElementById("az_provenance").style.display = "block"
}

function jsonElement(elementName, elementValue){
    return "\"" + elementName + "\":\"" + elementValue + "\"";
}


function az_inputChanged(){
    document.getElementById("az_Data").style.display = "none";
    var newname = document.getElementById("az_InputName").value
    window.open("https://data.azquo.com/api/Jstree?op=new&database=export&user=demo@user.com&password=password&itemschosen=" + escape(newname), "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")


}

function az_sendlogon(){
    document.loginform.submit();
}


function az_sendReportId(reportId){
    window.open("https://" +
        "data.azquo.com:8443/api/Online?user=" + az_logon + "&password=" + az_password + "&reportid=" + reportId,"_blank");
    //document.getElementById("az_Select").style.display = "none";
    //azquojsontest("Value","reportid=" + reportId +  "&jsonfunction=azquojsonreportfeed");
}



function az_click(e){
    e = e || window.event;
    var azSet;
    if (e.target) azSet = e.target.nextElementSibling;//from Googling should be IE
    if (!azSet) azSet = e.nextElementSibling;
    if (!azSet) azSet = e.nextSibling; //IE
    if (azSet.style.display=="none"){
        azSet.style.display="block";
    }else{
        azSet.style.display="none";
    }
}

function az_getdata(e){
    e = e || window.event;
    // amended - could be cumulative...
     az_chosen =  e.textContent;
    if (az_chosen.substring(0,1) == "\"") {
        az_chosen = az_chosen.substring(1);
    }
    //remove final brackets

    var bracketPos = 0;
    while (az_chosen.indexOf("(", bracketPos) > 0){
        bracketPos = az_chosen.indexOf("(", bracketPos) + 1;

    }
    if (bracketPos > 0){
        az_chosen = az_chosen.substring(0, bracketPos - 1);
    }
    document.getElementById("az_search_set").innerHTML = az_chosen;
    document.getElementById("az_Select").style.display = "none";
    azquojson("Value","searchbynames=" + escape("`" + az_chosen + "`") + "&jsonfunction=azquojsonfeed");


}

function az_provenance(e, datalabel){
    document.getElementById("az_provenance_cell").innerHTML = datalabel.replace(new RegExp("%2C","g"),", ").replace(new RegExp("%22","g"),"").replace(new RegExp("%20","g")," ").replace(new RegExp ("%60","g"),"");
    var prov = document.getElementById("az_provenance");
    var node;
    if (e) {
        node = e;
    }else{
        node = window.event.currentTarget;
    }
    var left = node.offsetLeft;
    var top = node.offsetTop;
    while (node.offsetParent != null){
        node = node.offsetParent;
        left += node.offsetLeft;
        top += node.offsetTop;
    }
    prov.style.left = left + "px";
    prov.style.top = top + "px";

    azquojson("Provenance","searchnames=" + datalabel +  "&jsonfunction=azquojsonfeed");


}
