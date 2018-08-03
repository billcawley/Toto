<%--
  Created by IntelliJ IDEA.
  User: edward
  Date: 05/01/17
  Time: 16:03
  Pasted then modified from the .vm, no need to be usingvelocity I don't think.
--%>
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
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
</head>
<body class="jstree">
<div id="js-wrapper">
    <form method="post" id="names" name="names">
        <div id="js-container" class="js-col2"></div>

        <div class="js-col2">
            <div id="attributeList">
                <label for="attributeChosen"> Attribute (language)</label>
                <select class="simpleselect" id="attributeChosen" onchange="changeLanguage()">

                    <c:forEach items="${attributes}" var="attribute">
                        <option value="${attribute}"<c:if test="${attribute == attributeChosen}"> selected</c:if>>${attribute}</option>
                    </c:forEach>


<!--                    #foreach($attribute in $attributes)
                    <option value="$attribute"
                            #if ($attribute == $attributeChosen)
                            selected
                            #end
                    >
                        $attribute
                    </option>
                    #end-->
                </select>
            </div>

            <div>
                <label for="itemschosen">Select items:</label>
                <input class="itemselect" type="text" value="" id="itemschosen" name="itemschosen"/>
            </div>
            <c:choose>
            <c:when test="${mode == 'choosename'}">
                <div class="choicesubmit" style="margin-top:10px;"><p><input type="submit" onclick="submitName(); return false;" class="button" value="Choose name"></p></div>

            </c:when>

            <c:otherwise>
            <div class="choicesubmit" style="margin-top:10px;"><p><input type="submit" onclick="showData(); return false;" class="button" value="Show data"></p></div>
            </c:otherwise>
            </c:choose>
        </div>
    </form>
    <div class="namedetails" id="namedetails" style="display:none">
        <div class="namedetailsinner">
            <div class="closebutton">
                <a href="#" onclick="hideDetails();"><span class="fa fa-times-circle"></span></a>
            </div>
            <div class="propertyname">
                <h3>Property Name</h3>
                <div class="value">
                    <label for="DEFAULT_DISPLAY_NAME">Name</label>
                    <input name="DEFAULT_DISPLAY_NAME" id="DEFAULT_DISPLAY_NAME" type="text" value="" class="attvalue">
                </div>
            </div>
            <div class="attributes">
                <h3>Attributes:</h3>
                <div id="htmlatts"></div>
            </div>
            <div class="propertystats">
                <div class="datacount">
                    Direct data count <b><span id="mydatacount"></span></b> Total Data Count <b><span id="totaldatacount"></span></b>
                </div>
                <h3>Provenance:</h3>
                <div id="provenance"></div>
            </div>
            <div class="namedetailssubmit">
                <p><a href="#" onclick="submitNameDetails()" class="button">Submit</a></p>
            </div>
            <div class="clear"></div>
        </div>
    </div>

    <script type="text/javascript">
        var hundredsMoreLookup = {};
        var parents = ${parents};
        var searchNames = "${searchnames}";
        var seeParents = "See parents";
        if (parents==true){
            seeParents="See children";
        }
        if (searchNames==null){
            searchNames = "";
        }
        var nameChosenNo = 0;

        $.jstree.defaults.dnd.check_while_dragging = false;
        $.jstree.defaults.dnd.inside_pos = "last";
        $(function() {
            $('#js-container').jstree({
                'core' : {
                    'data' : {
                        "state" : {"opened" : true },
                        "url" : "/api/Jstree?op=children&topnode=${rootid}&parents=" + parents + "&itemschosen=" + searchNames + "&attribute=" + decodeURI(document.getElementById("attributeChosen").value),
                        "dataType": "JSON",
                        "data" : function (node) {
                            return { "id" : node.id,
                                "hundredsmore" : hundredsMoreLookup[node.parent]
                            };
                        }
                    },
                    "check_callback" : true
                    /*"check_callback" : function (operation, node, parent, position, more) {
                     azquojson("Jstree","op=" + operation + "&id=" + node.id + "&position=" + position + "&parent=" + parent.id);
                     //carry on, but let the json feed flag the errors
                     return true; // allow everything else
                     },
                     "themes": {
                     "name": "proton",
                     "responsive": true
                     }
                     */
                },
                "plugins" : ["dnd","contextmenu"],
                "contextmenu":{
                    "items": function($node) {
                        var tree = $("#js-container").jstree(true)
                        var menu =  {
                            createItem : {
                                "label" : "New Name",
                                "action" : function(obj) {
                                    newNode = tree.create_node($node);
                                    tree.open_node($node);
                                },
                                "_class" : "class"
                            },
                            deleteItem : {
                                "label" : "Remove Name",
                                "action" : function(obj) { tree.delete_node($node)}
                            },
                            editAttributes:{
                                "label" : "Edit attributes",
                                "action" :function(obj){editAttributes($node)}
                            },
                            seeParents:{
                                "label":  seeParents,
                                "action" :function(obj) {
                                    var url = "/api/Jstree?op=new&id=" + $node.id;
                                    if (!parents) {
                                        url += "&parents=true";
                                    }
                                    if (window != window.top){
                                        window.parent.$['inspectOverlay']().tab(url, 'Parent');
                                    }else{
                                        //window.open(url, "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
                                        window.open(url,"_blank","")
                                    }
                                }

                            }
                        };
                        return menu;
                    }
                }

            }).on('create_node.jstree', function (e, data) {
                $.get('/api/Jstree?op=create_node', { 'id' : data.node.parent, 'position' : data.position, 'text' : data.node.text })
                    .done(function (d) {
                        data.instance.set_id(data.node, d);
                        editAttributes(data.node); // here when the id has been sorted??
                    })
                    .fail(function () {
                        data.instance.refresh_node(data.node);
                    });
            })/*.on('rename_node.jstree', function (e, data) {
             $.get('response.php?operation=rename_node', { 'id' : data.node.id, 'text' : data.text })
             .fail(function () {
             data.instance.refresh();
             });
             })*/.on('delete_node.jstree', function (e, data) {
                $.get('/api/Jstree?op=delete_node', { 'id' : data.node.id });
               // data.instance.refresh_node(data.instance.get_node(data.instance).parent);
            }).on('select_node.jstree', function (e, data) {
                if (data.node.text.indexOf("more....") != -1){ // hacky that the more.... just has to match but anyway
                    hundredsMoreLookup[data.node.parent] = (hundredsMoreLookup[data.node.parent] || 0) + 1;
//                    alert("node info : " + data.node.id + " text " + data.node.text + " parent : " + data.node.parent + " parent : " + data.instance.get_node(data.node.parent).text + " hundreds more" + hundredsMoreLookup[data.node.id]);
/*                    for (i in hundredsMoreLookup) {
                        alert("Name: " + i + " Value: " + hundredsMoreLookup[i]);
                    }*/
//                $('#js-container').jstree(true).refresh_node('1');
                    //data.instance.refresh_node(data.node)
//                data.instance.refresh_node('1');
                    data.instance.refresh_node(data.instance.get_node(data.node.parent));
                }
            });

        });


        $('#js-container').on("create_node.jstree", function (e, data) {
            console.log(data.instance.get_selected(true)[0].text);
        });


        function changeLanguage(){
            if (window!=window.top){
                window.parent.$['inspectOverlay']().tab("/api/Jstree?op=new&attribute=" + document.getElementById("attributeChosen").value + "&id=0", document.getElementById("attributeChosen").value);
            }else{
                //window.open("/api/Jstree?op=new&attribute=" + document.getElementById("attributeChosen").value + "&id=0", "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600")
                window.open("/api/Jstree?op=new&attribute=" + document.getElementById("attributeChosen").value + "&id=0", "_blank", "")
            }
        }

        function submitName(){
            var selected = $('#js-container').jstree("get_selected", true);
            localStorage.setItem("itemsChosen", selected[0].original.nameId + ":" + selected[0].text);//for ad-hoc reports
            window.opener = self;
            window.close();

        }

        function showData() {
            var itemsChosen = "";
            var dataFlag = false;
            if (document.getElementById("itemschosen").value == "") {
                var selected = $('#js-container').jstree("get_selected");
                itemsChosen = "jstreeids:";
                for (item in selected) {
                 itemsChosen += selected[item].id + ",";
               }
                // edd changing the above 3 lines to this which seems to be more compatible with IE
                itemsChosen = selected;
                dataFlag = true;
            } else {
                itemsChosen = encodeURIComponent(document.getElementById("itemschosen").value);
            }
            if (dataFlag) {
                if (window != window.top) {
                    window.parent.$['inspectOverlay']().tab("/api/Showdata?chosen=" + itemsChosen, 'Show Data');
                } else {
                    window.open("/api/Showdata?chosen=" + itemsChosen, "_blank", "toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600");
                }
            }else {
                window.parent.$['inspectOverlay']().tab(window.location + "&itemschosen=" + itemsChosen, 'Select Items');

            }
        }

        function hideDetails(){
            document.getElementById("namedetails").style.display='none';
        }

        function addAttribute(i, attname, attvalue){
            var htmlAtts = document.getElementById("htmlatts");
            htmlAtts.innerHTML += "<div class=\"namedetailsline\">" +
                "<div class = \"name\"><input id=\"attname" + i + "\" class=\"attname\" type=\"text\" value=\"" + attname + "\" placeholder=\"Attribute Name\"/></div>" +
                "<div class=\"value\"><textarea class=\"attvalue\" id=\"attvalue" + i + "\" cols=\"40\">" + attvalue + "</textarea></div>" +
                "</div>"

        }

        function editAttributes($node){
            azquojson("Jstree","op=details&id="+$node.id);
            nameChosenNo = $node.id;
        }

        function showNameDetails(nameDetails){
            document.getElementById("htmlatts").innerHTML = "";
            //currently ignoring the 'dataelements' 'elements'  'mydataelements'  values
            var keys = Object.keys(nameDetails.attributes);
            document.getElementById("mydatacount").innerHTML = nameDetails.mydataitems;
            document.getElementById("totaldatacount").innerHTML = nameDetails.dataitems;
            document.getElementById("provenance").innerHTML = nameDetails.provenance;
            for (var i=0;i < keys.length;i++){
                var attname = keys[i];
                //var attvalue =  decodeURIComponent((nameDetails.attributes[attname]+'').replace("%","%25").replace(/\+/g, '%20'));
                var attvalue =  decodeURIComponent((nameDetails.attributes[attname]+'').split("%").join("%25").split(" ").join("%20")); // split join replaces mroe than one
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


        function submitNameDetails(){
            var json = "\"operation\":\"edit\",\"id\":" + nameChosenNo + ",\"attributes\":{\"DEFAULT_DISPLAY_NAME\":" + encodeURIComponent(JSON.stringify(document.getElementById("DEFAULT_DISPLAY_NAME").value));
            for (var i= 0; i<20;i++){//max number of attributes = 20 - arbitrary
                var attname = document.getElementById("attname" + i);
                if (attname != null) {
                    if (attname.value > ""){
                        json += ",\"" + attname.value + "\":" + encodeURIComponent(JSON.stringify(document.getElementById("attvalue" + i).value)) + "";
                    }
                }
            }
            json +="}";
            hideDetails();
            azquojson("Jstree", "json={" + json + "}");
//            var node = $('#js-container').jstree(true).get_node(nameChosenNo);
            $('#js-container').jstree('rename_node', nameChosenNo, document.getElementById("DEFAULT_DISPLAY_NAME").value);
        }


        function azquojson(functionName, params){
            var htmlText = "/api/" + functionName + "?" + params + "&attribute=" + encodeURI(document.getElementById("attributeChosen").value);
            var script = document.createElement('script'),
                head = document.getElementsByTagName('head')[0] || document.documentElement;
            script.src = htmlText;
            head.appendChild(script);
        }


        function azquojsonfeed(obj) {


            if (obj==null) return;
            if (obj.response > ""){
                if (obj.response!=true){
                    alert("Server " + obj.response)
                }
            }
            if (obj.namedetails >""){
                showNameDetails(obj.namedetails);
            }
        }

    </script>
    ${message}
</div>
</body>
</html>