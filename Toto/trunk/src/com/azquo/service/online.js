



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
var menuControls = $menuitems;
var clickedItem = null;
var nameChosenNo = 0
var cutitem = null;
var copyitem = null;



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


function hideMenu(control){
    document.getElementById(control).style.display = "none";
}

function showMenu(control, e){
    clickedItem = e.target;
    if (clickedItem == null) clickedItem = e;
    if (clickedItem != null){

        if (clickedItem.tagName.toLowerCase() == "li") {
            clickedItem.className = "highlight";
            var nextItem = nextSibling(clickedItem);
            nameChosenNo = nextItem.innerHTML;
        }
    }
    var selector = document.getElementById("selector");
    var left = getStyleInt(selector,"left");
    var top = getStyleInt(selector,"top");
    if (top > 100){
        top -=80;

    }
    left = left+20;
    var popup =document.getElementById(control);
    popup.style.left = left + "px";
    popup.style.top = top + "px";
    var menucontent = "";
    for (var i=1; i < 20; i++){
        var menuItem = findMenuItem(i);
        if (menuItem!=null){
           if (!menuItem.enabled){
                menucontent += '<div class="disabled">' + menuItem.name + "</div>"
            } else{
               menucontent += '<div class="enabled"><a href="#" onclick="' + menuItem.link + '">' + menuItem.name + "</a></div>"

           }
        }else{
            menucontent += '<div class="divider"></div>'
        }

    }
    popup.innerHTML = menucontent;
    popup.style.display="block";
    return false; //disables the default context menu
}

function findMenuItem(position){
    for (var i = 0;i < menuControls.length;i++){
        if (menuControls[i].position == position){
            return menuControls[i];
        }
    }
    return null;
}


function setEnabled(menuitem, enabled){
    for (var i=0;i<menuControls.length;i++){
        if (menuControls[i].name == menuitem){
            menuControls[i].enabled = enabled;
            break;
        }
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



document.onclick = onClick;

function onClick(e){
    hideMenu("popupmenu");
    if (copyitem == null && cutitem==null && clickedItem!=null){
        //clickedItem.className = ""; //for name lists this should be active
    }
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

function saveData(){
    azquojson("Online","jsonfunction=azquojsonfeed&opcode=savedata");

}


function cancelEntry() {

    if (entryX < 0) return;
    var container = document.getElementById("cell" + entryY + "-" + entryX);
    container.setAttribute("contentEditable", false);
    if (entryVal != container.innerHTML.trim()){
        sendValue(container.innerHTML.trim())
        document.getElementById("savedata").style.display="inline";
    }

    entryX = -1;
    entryY = -1;


}

document.onkeyup =function (e) {
    controlKeyDown = false;
}


function sortCol(region, colNo){
    document.getElementById("editedName").value = "sort " + region + " by column";
    document.getElementById("editedValue").value = colNo + "";
    document.azquoform.submit();
}


function showProvenance(){
    azquojson("Online","jsonfunction=azquojsonfeed&opcode=provenance&row=" + selectionY + "&col=" + selectionX );
    hideMenu("popupmenu");
}

function edit(){
    hideMenu("popupmenu");
    clickedItem.className = "highlight";
    azquojson("Online","opcode=details&nameid=" + nameChosenNo);
}


function cut(){
     cutitem = clickedItem;
    copyitem = null;
}

function copy(){
    copyitem = clickedItem;
    cutitem = null;
}


function findParent(item){
    var parent = item.parentNode;
    var count = 0;
    for (var count = 0;count < 5;count++){
        if (parent.tagName.toLowerCase()=='li'){
            break;
        }
        parent = parent.previousElementSibling;
    }
    if (count==3) return null;
    return parent;
}


function paste(offset) {

    var anchorNode = clickedItem;
    //each LI item is followed by an unseen 'div' containing the name id
    if (cutitem == null && copyitem == null) return;
    var cutting = false;
    if (cutitem != null) {
        var source = cutitem;
    } else {
        source = copyitem;
    }
    if (offset==2) {//paste into}
           while (anchorNode.nextElementSibling != null && anchorNode.nextElementSibling.tagName.toLowerCase()!="li"){
               anchorNode = anchorNode.nextElementSibling;
           }

        anchorNode.parentNode.insertBefore(document.createElement("ul"),anchorNode.nextElementSibling);
        var ulTag = anchorNode.nextElementSibling;
        ulTag.appendChild(source.cloneNode(true));
        ulTag.appendChild(source.nextElementSibling.cloneNode(true));
        var nextEl = source.nextElementSibling.nextElementSibling;
        if (nextEl != null && nextEl.tagName.toLowerCase()=="ul"){
            ulTag.appendChild(nextEl.cloneNode(true));
        }

    }else {
        var divNode = anchorNode.nextElementSibling;
        var pos = findPos(anchorNode);
        var parent = findParent(anchorNode);
        if (parent == null) return
        if (offset==1){
            pos++;
            anchorNode = anchorNode.nextElementSibling
            while (anchorNode.nextElementSibling != null && anchorNode.tagName.toLowerCase()!="li") {
                anchorNode = anchorNode.nextElementSibling;


            }
        }
        var nextEl = source.nextElementSibling.nextElementSibling;
        if (anchorNode.nextElementSibling != null) {
            anchorNode.parentNode.insertBefore(source.cloneNode(true), anchorNode);
            anchorNode.parentNode.insertBefore(source.nextElementSibling.cloneNode(true),anchorNode);
            if (nextEl != null && nextEl.tagName.toLowerCase()=="ul"){
                anchorNode.parentNode.insertBefore(nextEl.cloneNode(true), anchorNode);
            }

        } else {
            if (nextEl != null && nextEl.tagName.toLowerCase()=="ul"){
                anchorNode.parentNode.insertBefore(nextEl.cloneNode(true), anchorNode.nextElementSibling);
            }

            anchorNode.parentNode.insertBefore(source.nextElementSibling.cloneNode(true), anchorNode.nextElementSibling);
            anchorNode.parentNode.insertBefore(source.cloneNode(true), anchorNode.nextElementSibling);

        }
    }
    //now cut if necessary
    //and tell Azquo!




     var j=1;
}

function deleteName(){
    var j=1;
}


function showHighlight() {
    document.getElementById("highlightoptions").style.display = "block";
    hideMenu("popupmenu");
}

function highlight(days){
    document.getElementById("editedName").value = "highlight";
    document.getElementById("editedValue").value = days + "";
    document.azquoform.submit();
}



document.onkeydown =function (e) {

    //var evtobj=window.event? event : e;
    //if (evtobj.altKey || evtobj.ctrlKey || evtobj.shiftKey)
    //    alert("you pressed one of the 'Alt', 'Ctrl', or 'Shift' keys");
    var unicode = e.charCode ? e.charCode : e.keyCode;
    switch (unicode) {
        case 17: controlKeyDown = true;
            break;
        case 89:
        case 81: if (controlKeyDown){
             showMenu("popupmenu", e);
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


function buttonPressed(rowNo, colNo){
    document.getElementById("row").value = rowNo;
    document.getElementById("col").value = colNo;
    document.azquoform.submit();
}

function loadsheet(sheetname){
    document.getElementById("spreadsheetname").value = sheetname;
    document.azquoform.submit();
}

function downloadSheet(){
    azquojson("Download","reportid=" + document.getElementById("reportId").value);//not currently returning a status
}


function drawChart(){
    azquojson("Online","jsonfunction=azquojsonfeed&chart=$region");
}

function findPos(item){
    //returns the position in the set of the current item
    var parent = item.parentNode;
    var list = parent.getElementsByTagName("LI");
    for (var i=0;i < list.length;i++){
        var child = list[i];
        if (child.innerHTML == item.innerHTML){
           return i + 1;
        }
    }
    return 0;
}


function nextSibling(azSet){
    var azNext = azSet.nextElementSibling;
    if (!azNext) azNext = azSet.nextElementSibling; //IE
    return azNext;
}


function az_clicklist(e){
    e = e || window.event;
    if (e.target) {
        var azSet = e.target;
    }else{
        azSet = e;
    }

    while (azSet.tagName.toLowerCase() != "ul"){
        azSet = nextSibling(azSet)
    }
    if (azSet.style.display=="none"){
        azSet.style.display="block";
    }else{
        azSet.style.display="none";
    }
}




function sendValue(value){

    document.getElementById("message").innerHTML = "";
    azquojson("Online","jsonfunction=azquojsonfeed&region=" + entryRegion + "&row=" + entryRegionY + "&col=" + entryRegionX  + "&value=" + encodeURIComponent(value));
}



function azquojson(functionName, params){
    if (functionName == "Name"){
        params = "json={" + params + ",\"connectionId\":\"" + azquoform.connectionid.value + "\",\"jsonFunction\":\"azquojsonfeed\"}";
    }else{
        params +="&connectionid=" + azquoform.connectionid.value;
    }
    //var htmlText = "http://www.bomorgan.co.uk:8080/api/" + functionName + "?" + params;
    var htmlText = "/api/" + functionName + "?" + params;
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


    if (obj==null) return;
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
        innerhtml += decodeProvenance(obj.provenance, 0);
        var prov = document.getElementById("provenancecontent");
        prov.innerHTML = innerhtml;
        document.getElementById("provenance").style.display = "block";

    }
    if (obj.message > ""){
        document.getElementById("message").innerHTML = obj.message;
    }
    if (obj.namedetails >""){
        showNameDetails(obj.namedetails);
    }
}

    function addAttribute(i, attname, attvalue){
        var htmlAtts = document.getElementById("htmlatts");
        htmlAtts.innerHTML += "<div class=\"namedetailsline\">" +
            "<div class = \"name\"><input id=\"attname" + i + "\" class=\"attname\" type=\"text\" value=\"" + attname + "\"/></div>" +
            "<div class=\"value\"><input class=\"attvalue\" id=\"attvalue" + i + "\" type=\"text\" value=\"" + attvalue + "\"/></div>" +
            "</div>"

    }

   function showNameDetails(nameDetails){
       document.getElementById("htmlatts").innerHTML = "";
       //currently ignoring the 'dataelements' 'elements'  'mydataelements'  values
       var keys = Object.keys(nameDetails.attributes);
       for (var i=0;i < keys.length;i++){
           var attname = keys[i];
           var attvalue =  decodeURIComponent((nameDetails.attributes[attname]+'').replace(/\+/g, '%20'));
           if (attname== "DEFAULT_DISPLAY_NAME"){
               document.getElementById("DEFAULT_DISPLAY_NAME").value = attvalue;
           }else{
               if (attname!="RPCALC"){
                   addAttribute(i, attname, attvalue)
               }
           }

       }
       addAttribute(i,"","");
       document.getElementById("namedetails").style.display = "block";
   }


    function decodeProvenance(provDisplays, level) {
        var style = "";
        if (level > 1) style = ' style="display:none;"';
        var output = "<ul" + style + ">";
        for (var i = 0; i < provDisplays.length; i++){
            var provDisplay = provDisplays[i];
            if (provDisplay.heading) {
                 output += '<li onclick="tree_click(this);">' + provDisplay.heading + "</li>";
                output += decodeProvenance(provDisplay.items, level + 1);
            } else {
                output += "<li>" + provDisplay.value + " " + provDisplay.name + "</li>";
            }
        }
        output += "</ul>";
        return output;
    }

function tree_click(e){
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

function hideNameList(){
    hideMenu("namelistpopup");
}

function hideDetails(){
    hideMenu("namedetails");
    //clickedItem.className = "";  //good for name lists, but not for data regions!
}

function submitNameDetails(){
    var json = "\"operation\":\"edit\",\"id\":" + nameChosenNo + ",\"attributes\":{\"DEFAULT_DISPLAY_NAME\":\"" + document.getElementById("DEFAULT_DISPLAY_NAME").value + "\"";
    for (var i= 0; i<20;i++){//max number of attributes = 20 - arbitrary
        var attname = document.getElementById("attname" + i);
        if (attname != null) {
            if (attname.value > ""){
                json += ",\"" + attname.value + "\":\"" + document.getElementById("attvalue" + i).value + "\"";
            }
        }
    }
    json +="}";
    hideDetails();
    azquojson("Name", json);
    clickedItem.innerHTML = document.getElementById("DEFAULT_DISPLAY_NAME").value;

}




if (document.addEventListener) {
    document.addEventListener('contextmenu', function(e) {
        onClick(e);
       showMenu("popupmenu", e);
        e.preventDefault();
    }, false);
} else {
    document.attachEvent('oncontextmenu', function() {
        onClick(e);
        showMenu("popupmenu",e);
        window.event.returnValue = false;
    });
}

