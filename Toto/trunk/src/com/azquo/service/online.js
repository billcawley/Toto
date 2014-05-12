



var selectionX = $leftcell;
var selectionY = $topcell;
var maxRow = $maxrow;
var maxCol = $maxcol;
var leftCol = selectionX;
var topRow = selectionY;
var regions =$regions;
var entryX = -1;
var entryY = -1;
var entryVal = null;
var entryRegion = null;
var entryRegionX = 0;
var entryRegionY = 0;
var controlKeyDown = false;



positionSelection();
convertRegions();


function getStyle(oElm, strCssRule){
    var strValue = "";
    if(document.defaultView && document.defaultView.getComputedStyle){
        strValue = document.defaultView.getComputedStyle(oElm, "").getPropertyValue(strCssRule);
    }
    else if(oElm.currentStyle){
        strCssRule = strCssRule.replace(/\-(\w)/g, function (strMatch, p1){
            return p1.toUpperCase();
        });
        strValue = oElm.currentStyle[strCssRule];
    }
    return strValue;
}

function getStyleInt(oElm, strCssRule){
    return parseInt(getStyle(oElm, strCssRule));
}

function convertRegions(){
    for (var i = 0;i < regions.length;i++){
        var region = regions[i];
        region.x = parseInt(region.left);
        region.y = parseInt(region.top);
        region.rows = parseInt(region.bottom) - region.y + 1;
        region.cols = parseInt(region.right) - region.x + 1;
    }
}

function positionSelection(){
    if (entryX >=0 && (selectionX != entryX || selectionY != entryY)){
        cancelEntry();
    }
    var cursor = document.getElementById("selector");
    var selectionCell = document.getElementById("cell" + selectionY + "-" + selectionX);
    var selborder = getStyleInt(cursor, "border-right-width");
    cursor.style.left = getStyle(selectionCell, "left");
    cursor.style.top = getStyle(selectionCell,"top");
    var width = getStyleInt(selectionCell,"width");
    var cellBorderRight = getStyleInt(selectionCell, "border-right-width");
    var cellBorderBottom = getStyleInt(selectionCell,"border-bottom-width");
    var height =  getStyleInt(selectionCell,"height");
    cursor.style.width = (width - selborder + cellBorderRight) + "px";
    cursor.style.height = (height - selborder + cellBorderBottom) + "px";

    if (locked(selectionX, selectionY) == "") {
        startEntry();
    }

}

function cellExists(x,y){
    var cell = document.getElementById("cell" + y + "-" + x);
    if (cell == null) return false;
    return true;
}



function locked(cellX,cellY){
    for (var i = 0;i < regions.length;i++){
       var region = regions[i];
       if (cellX >= region.x && cellY >= region.y && cellX < region.x + region.cols && cellY < region.y + region.rows) {
           entryRegion = region.name;
           entryRegionX = cellX - region.x;
           entryRegionY = cellY - region.y;
           var locked = region.locks[entryRegionY][entryRegionX];
           return locked;
       }
    }
    return "LOCKED";
}


document.onclick = function(e){
    var target = e.target;
    var cellId = target.id
    while (cellId != undefined && cellId.substring(0, 4) != "cell" && target.parentNode != null) {
        target = target.parentNode;
        cellId = target.id;
    }
    if (cellId != null && cellId.substr(0, 4) == "cell") {
        var dashPos = cellId.indexOf("-");
        var yStr = cellId.substr(4, dashPos);
        var xStr = cellId.substr(dashPos + 1);
        selectionX = parseInt(xStr);
        selectionY = parseInt(yStr);
    }
    positionSelection();
 }



function startEntry() {

    var container = document.getElementById("cell" + selectionY + "-" + selectionX);
    container.setAttribute("contentEditable", true);
    container.focus();
    /*
    var content = container.innerHTML;
    container.innerHTML = "";
    var input = document.createElement("input");
    input.setAttribute("type","text");
    input.setAttribute("value", content);
    input.setAttribute("name","focus");
    input.style.zIndex = 10;
    container.appendChild(input);
    input.focus();
    */
    entryX = selectionX;
    entryY = selectionY;
    entryVal = container.innerHTML.trim();

}

function cancelEntry() {

    if (entryX < 0) return;
    var container = document.getElementById("cell" + entryY + "-" + entryX);
    container.setAttribute("contentEditable", false);
    if (entryVal != container.innerHTML.trim()){
        sendValue(container.innerHTML.trim())
    }

    entryX = -1;
    entryY = -1;


}

document.onkeyup =function (e) {
    controlKeyDown = false;
}


function showProvenance(){
    azquojson("Online","jsonfunction=azquojsonfeed&opcode=provenance&row=" + selectionY + "&col=" + selectionX );
}




document.onkeydown =function (e) {

    //var evtobj=window.event? event : e;
    //if (evtobj.altKey || evtobj.ctrlKey || evtobj.shiftKey)
    //    alert("you pressed one of the 'Alt', 'Ctrl', or 'Shift' keys");
    var unicode = e.charCode ? e.charCode : e.keyCode;
    switch (unicode) {
        case 17: controlKeyDown = true;
            break;
        case 81: if (controlKeyDown){
            showProvenance();
            break;
        }

        case 37:

            if (selectionX > 0){
                var newX = selectionX - 1;
                while (newX >= 0 && !cellExists(newX, selectionY)) newX--;
                if (newX >=0) selectionX = newX
            }
            break;
        case 38:
            if (selectionY > 0){
                var newy = selectionY - 1;
                while (newy >= 0 && !cellExists(selectionX, newy)) newy--;
                if (newy >=0) selectionY = newy
            }
            break;
        case 39:
            if (selectionX < maxCol){
                var newX = selectionX + 1;
                while (newX <= maxCol && !cellExists(newX, selectionY)) newX++;
                if (newX <= maxCol) selectionX = newX
            }
            break;
        case 40:
            if (selectionY < maxRow){
                var newy = selectionY + 1;
                while (newy <= maxRow && !cellExists(selectionX, newy)) newy++;
                if (newy <= maxRow) selectionY = newy
            }
            break;
        case 13:
            cancelEntry();
            break;
    }
    positionSelection();
}



function chosen(divName){
    document.getElementById("editedName").value = divName;
    document.getElementById("editedValue").value = document.getElementById(divName).value;
    document.azquoform.submit();
}

function downloadSheet(){
    document.getElementById("opcode").value = "download";
    document.azquoform.submit();
}


function drawChart(){
    azquojson("Online","jsonfunction=azquojsonfeed&chart=$region");
}

function sendValue(value){

    azquojson("Online","jsonfunction=azquojsonfeed&region=" + entryRegion + "&row=" + entryRegionY + "&col=" + entryRegionX  + "&value=" + encodeURIComponent(value));
}



function azquojson(functionName, params){
    if (functionName == "Name"){
        params = "json={" + params + ",\"connectionid\":\"" + azquoform.connectionid.value + "\",\"jsonFunction\":\"azquojsonfeed\"}";
    }else{
        params +="&connectionid=" + azquoform.connectionid.value;
    }
    //var htmlText = "http://www.bomorgan.co.uk:8080/api/" + functionName + "?" + params;
    var htmlText = "https://data.azquo.com:8443/api/" + functionName + "?" + params;
    var script = document.createElement('script'),
        head = document.getElementsByTagName('head')[0] || document.documentElement;
    script.src = htmlText;
    head.appendChild(script);
}


function oneTerm(val1, val2){
    return "<tr><td><b>" + val1 + "</b></td><td>" + val2 + "</td></tr>";
}

function showDate(dateSent){
    return dateSent;
}

function makeList(names){
    var start = true;
    var output = "";
    for (var i = 0; i < names.length; i++){
        if (start){
            start = false;

        }else{
            output += ",";
        }
        output += names[i];

    }
    return output;
}


function azquojsonfeed(obj) {


    var chart = obj.chart;
    if (chart > "") {
        window.open(chart, "_blank", "toolbar=no, scrollbars=no, resizable=yes, width=800, height=400");
        return;
    }
    if (obj.changedvalues > "") {
        for (var i = 0; i < obj.changedvalues.length; i++) {
            var val = obj.changedvalues[i];
            var elem = document.getElementById(val.id);
            elem.innerHTML = val.value;
            elem.className = val.class;

        }
    }
    if (obj.provenance > "") {
        var innerhtml = "<h2>Provenance</h2>"
        innerhtml +=  decodeProvenance(obj.provenance);
        var prov = document.getElementById("provenancecontent");
        prov.innerHTML = innerhtml;
        document.getElementById("provenance").style.display = "block";

    }


    function decodeProvenance(provDisplays) {
        var output = "<ul>"
        for (var i = 0; i < provDisplays.length; i++){
            var provDisplay = provDisplays[i];
            if (provDisplay.heading) {
                output += "<li>" + provDisplay.heading + "</li>";
                output += decodeProvenance(provDisplay.items);
            } else {
                output += "<li>" + provDisplay.value + " " + provDisplay.name + "</li>";
            }
        }
        output += "</ul>";
        return output;
    }
}