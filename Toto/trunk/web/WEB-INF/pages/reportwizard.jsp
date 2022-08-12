<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Report Wizard"/>
<%@ include file="../includes/admin_header2.jsp" %>

<script>






        /*

rowHeadingsSource: string[][],
columnHeadingsSource: string[][],
columnHeadings: string[][],
rowHeadings: string[][],
context: string[][],
data: string[][],
highlight: boolean[][],
comments: string[][]
options: string,
lockresult: string
         */



        async function azquoSend(info) {
            var host = sessionStorage.getItem("host");
            try {
                let data = await fetch('/api/Excel/', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: info
                });
                //console.log(data)
                return data;
            } catch (e) {
                console.log(e)
            }
        }

        async function changed(chosenId, selection) {
            /*
            the message to Azquo tells it what values are currently selected.  The return will be a json array of fieldname,fieldvalues[]  (e.g changing the data value will set up rows, columns and templates)

             */
            var dataChosen = "";
            if (${datavalues.size()==1}){
                dataChosen = "${datavalues.get(0)}"
            }else{
                dataChosen = getSelectedValue("data");
            }
            var rowValueChosen = getSelectedValue("rows");
            var colValueChosen = getSelectedValue("columns");
            var templateChosen = getSelectedValue("templates");

             let params = "op=reportwizard&datachosen=" +encodeURIComponent(dataChosen)
                + "&rowchosen=" + encodeURIComponent(rowValueChosen)
                + "&columnchosen=" + encodeURIComponent(colValueChosen)
                + "&templatechosen=" + encodeURIComponent(templateChosen)
                + "&sessionid=${pageContext.session.id}";
            let data = await azquoSend(params);
            let json = await data.json();
            var rowValueChosen = "";
            var columnValueChosen = "";
            for (var i = 0; i < json.length; i++) {
                var returnedField = json[i].selName
               //alert(returnedField);
                var selections = '<select class="select" name="'+returnedField+'" id="'+returnedField+ '" onchange=changed("'+returnedField+'",this)>';
                var count = 0;
                for(var optionValue of json[i].selValues){
                    if (count++ < 50){
                        if (optionValue.endsWith(" selected")){
                            selections += '<option value="' + optionValue.substring(0,optionValue.length - 9) + '" selected>' + optionValue + '</option>';
                        }else{
                            selections += '<option value="' + optionValue + '">' + optionValue + '</option>';
                        }
                    }
                }
                selections += "</select>";
                document.getElementById(returnedField+"pan").innerHTML = selections;
            }
        }


        function getSelectedValue(rangeName){
            if (rangeName=="data" || document.getElementById(rangeName + "pan").innerHTML>"") {
                var sel = document.getElementById(rangeName);
                return sel.options[sel.selectedIndex].text;
            }
            return "";
        }



</script>



<div class="box">
    <h1 class="title">Report Wizard</h1>
    <div class="has-text-danger" id="error">${error}</div>
    <form action="/api/ReportWizard" method="post">
        <input type="hidden" name="stage" id="stage" value="${stage}"/>
        <!-- no business id -->
        <table class="table">
            <tr>
                <td>1/5 Select the data you want to show
                </td>
                <td>
                    <c:choose>
                        <c:when  test="${datavalues.size()==1}">
                            <input type="hidden" name="datavalue" value="${datavalues.get(0)}">
                            ${datavalues.get(0)}
                        </c:when>
                        <c:otherwise>
                            <select class="select" name="data" id="data" onchange=changed("data",this)>
                                <option value=""></option>
                                <c:forEach items="${datavalues}" var="datavalue">
                                    <option value="${datavalue}"
                                            <c:if test="${datavalue.equals(datavalueselected)}"> selected </c:if>>${datavalue}</option>
                                </c:forEach>
                            </select>
                        </c:otherwise>
                    </c:choose>

                </td>
                <td> You can choose individual data items or sets of data items (from bottom of list)
                </td>
            </tr>
            <tr>
                <td>2/5 Select your row headings
                </td>
                <td>
                    <span id="rowspan"></span>
                </td>
                <td>
                    We will work out an appropriate way to show these if the number is large
                </td>
            </tr>
            <tr>
                <td>
                    3/5 Select the column headings
                </td>
                <td>
                    <span id="columnspan"></span>
                </td>
                <td>
                    As above
                </td>
            </tr>
            <tr>
                <td>
                    4/5 Select a template
                </td>
                <td>
                    <span id="templatespan"></span>
                </td>
                <td>
                    You can add your own templates if you want
                </td>
            </tr>
            <tr>
                <td>5/5 Name your report
                </td>
                <td>
                    <input name="reportName" type="text" value="Test report"/>
                </td>
            </tr>
        </table>
        <div class="centeralign">
                 <button type="submit" name="submit" value="submit" class="button">Create Report</button>
          </div>


    </form>

</div>

<script>
    if (${datavalues.size()==1}){
        changed("datavalue","${datavalues.get(0)}")
    }
</script>
<%@ include file="../includes/admin_footer.jsp" %>
