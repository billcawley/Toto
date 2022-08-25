<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Report Wizard"/>
<%@ include file="../includes/new_header.jsp" %>


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
        var dataChosen = getSelectedValue("data");
        var functionValueChosen = getSelectedValue("functions");
        var rowValueChosen = getSelectedValue("rows");
        var colValueChosen = getSelectedValue("columns");
        var templateChosen = getSelectedValue("templates");

        let params = "op=reportwizard&datachosen=" + encodeURIComponent(dataChosen)
            + "&functionchosen=" + encodeURIComponent(functionValueChosen)
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
            var selections = '<select class="select" name="' + returnedField + '" id="' + returnedField + '" onchange=changed("' + returnedField + '",this)>';
            var count = 0;
            for (var optionValue of json[i].selValues) {
                if (count++ < 50) {
                    if (optionValue.endsWith(" selected")) {
                        selections += '<option value="' + optionValue.substring(0, optionValue.length - 9) + '" selected>' + optionValue.substring(0, optionValue.length - 9) + '</option>';
                    } else {
                        selections += '<option value="' + optionValue + '">' + optionValue + '</option>';
                    }
                }
            }
            selections += "</select>";
            document.getElementById(returnedField + "pan").innerHTML = selections;
        }
    }


    function getSelectedValue(rangeName) {
        if (rangeName == "data" || document.getElementById(rangeName + "pan").innerHTML > "") {
            var sel = document.getElementById(rangeName);
            if(sel == null){
                return document.getElementById("datavalue").value;
            }
            return sel.options[sel.selectedIndex].text;
        }
        return "";
    }


</script>


<div class="az-content">
    <div class="az-topbar">
        <button>
            <svg
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke-width="2"
                    stroke="currentColor"
                    aria-hidden="true"
            >
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h8m-8 6h16"></path>
            </svg>
        </button>
        <div class="az-searchbar">
            <form action="#">
                <div>
                    <div>
                        <svg
                                xmlns="http://www.w3.org/2000/svg"
                                fill="none"
                                viewBox="0 0 24 24"
                                stroke-width="2"
                                stroke="currentColor"
                                aria-hidden="true"
                        >
                            <path
                                    stroke-linecap="round"
                                    stroke-linejoin="round"
                                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                            ></path>
                        </svg>
                    </div>
                    <input placeholder="Search" type="text" value=""/>
                </div>
            </form>
        </div>
    </div>
    <main>
        <div class="az-import-wizard-view">
            <div class="az-import-wizard">
                <div class="az-import-wizard-main">
                    <div class="az-section-heading">
                        <h3>Create a simple report</h3>
                        <div class="az-section-controls">
                            <div class="az-section-filter">
                                <div>
                                    <svg
                                            xmlns="http://www.w3.org/2000/svg"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                            aria-hidden="true"
                                    >
                                        <path
                                                fill-rule="evenodd"
                                                d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
                                                clip-rule="evenodd"
                                        ></path>
                                    </svg>
                                </div>
                                <input type="text" placeholder="Filter"/>
                            </div>
                        </div>
                    </div>
                    <div class="az-section-body">
                        <div class="az-alert az-alert-warning">
                            <div>
                                <div></div>
                                <div></div>
                                <div>
                                    <svg
                                            xmlns="http://www.w3.org/2000/svg"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                            aria-hidden="true"
                                    >
                                        <path
                                                fill-rule="evenodd"
                                                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                                                clip-rule="evenodd"
                                        ></path>
                                    </svg>
                                </div>
                                <div></div>
                                <div>
                                    <div id="instructions">
                                        Please fill in below

                                    </div>
                                </div>
                            </div>
                        </div>
                        <form action="/api/ReportWizard" method="post">
                            <div class="az-table" id="fieldtable">
                                <table class="az-table">
                                    <tr>
                                        <td>1 Select the data you want to show
                                        </td>
                                        <td>
                                            <c:choose>
                                                <c:when  test="${datavalues.size()==1}">
                                                    <input type="hidden" name="datavalue" id="datavalue" value="${datavalues.get(0)}">
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

                                        </td style="width:400px">
                                        <td> You can choose individual data items or sets of data items (from bottom of
                                            list)
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>2 ..and how to show it
                                        </td>
                                        <td>
                                            <span id="functionspan"></span>
                                        </td>
                                        <td>
                                            Most data will be summed, but in some cases this is not appropriate
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>3 Select your row headings
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
                                            4 Select the column headings
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
                                            5 Select a template
                                        </td>
                                        <td>
                                            <span id="templatespan"></span>
                                        </td>
                                        <td>
                                            You can add your own templates if you want
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>6 Name your report
                                        </td>
                                        <td>
                                            <input name="reportName" type="text" value="Test report"/>
                                        </td>
                                    </tr>
                                </table>

                            </div>
                            <div class="az-import-wizard-pagination">
                                <div>
                                    <button class="az-wizard-button-next" type="submit" name="submit" value="submit">Next</button>

                                </div>
                            </div>
                        </form>
                    </div>
                </div>

            </div>
        </div>
    </main>
</div>



<script>
    if (${datavalues.size()==1}) {
        changed("datavalue", "${datavalues.get(0)}")
    }
</script>
<%@ include file="../includes/new_footer.jsp" %>
