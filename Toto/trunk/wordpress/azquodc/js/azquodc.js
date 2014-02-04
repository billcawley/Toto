//javascript for Azquo DC
var az_connectionId;
var az_chosen;
az_connectionId = "";
az_chosen = "";


function zapParam(params, tozap){
    var paramPos = params.indexOf("&" + tozap);
    if (paramPos > 0){
        var paramEnd = params.indexOf("&",paramPos + 1);
        if (paramEnd < 0) return params.substring(0,paramPos);
        return params.substring(0,paramPos) + params.substring(paramEnd);
    }
    return params;
}


function azquojson(functionName, params){
    if (functionName == "Name"){
        params = "json={" + params + ",\"user\":\"demo@user.com\",\"password\":\"password\",\"database\":\"Demo_export\",\"jsonFunction\":\"azquojsonfeed\"}";
    }else{
        params +="&user=demo@user.com&password=password&database=Demo_export";
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
                    datalabel += "\"" +  data[i][j] + "\",";
                }
            }
            for (j = 0;j < data[i].length; j++){
                if (j > 0){
                    if (data[i][j] > ""){
                        datalabel  = "\"" +  data[i][j] + "\"";
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

function az_showProvenance(provenance){
    document.getElementById("az_who").innerHTML = provenance.who;
    document.getElementById("az_when").innerHTML = provenance.when;
    document.getElementById("az_method").innerHTML = provenance.method;
    document.getElementById("az_where").innerHTML = provenance.where;

    document.getElementById("az_provenance").style.display = "block"
}



function az_inputChanged(){
    document.getElementById("az_Data").style.display = "none";
    var newname = document.getElementById("az_InputName").value
    azquojson("Name","\"operation\":\"structure\",\"name\":\"" + escape(newname) + "\"");
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
    az_chosen = "\"" + e.textContent + "\"";
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
    azquojson("Value","searchbynames=" + escape(az_chosen) + "&jsonfunction=azquojsonfeed");


}

function az_provenance(e, datalabel){
    document.getElementById("az_provenance_cell").innerHTML = datalabel.replace(new RegExp("%2C","g"),", ").replace(new RegExp("%22","g"),"").replace(new RegExp("%20","g")," ");
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
