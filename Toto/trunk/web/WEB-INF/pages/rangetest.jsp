<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Range Test" />
<%@ include file="../includes/admin_header2.jsp" %>
<script>

    function switchHeadings(columnManual, rowManual) {
        //$('#' + tableId).empty(); //not really necessary
        var rows = '';
        for (i = 0; i < 10; i++){
            var row = '<tr>';
            for (j = 0; j < 10; j++){
                if ((j == 0 && rowManual && i > 0) || (i == 0 && j > 0 && columnManual)){
                    row += '<td><input class="input is-small" name="name" id="name"/></td>';
                } else {
                    row += '<td></td>';
                }
            }
            rows += row + '<tr>';

        }
        $('#data-table').html(rows);
    }

    async function getData() {
        if ($('#rowsaql').val().length > 0 && $('#colsaql').val().length == 0){

            let data = await azquoSend("op=getdropdownlistforquery&choice=" + encodeURIComponent($('#rowsaql').val())+ "&sessionid=${pageContext.session.id}&database="  + encodeURIComponent($('#database').val()));
            var userChoices = await data.json();

            //alert(userChoices);
            var rows = '';
            // todo limit rows??
            $.each(userChoices, function(index, item) {
                var row = '<tr>';
                if (item.toString().startsWith("Error :")){
                    row += '<td><span  style="color:red">' + item + '</span></td>';
                } else {
                    row += '<td>' + item + '</td>';
                }
                for (j = 0; j < 10; j++){
                    row += '<td></td>';
                }
                rows += row + '<tr>';
            });
        }
        if ($('#rowsaql').val().length == 0 && $('#colsaql').val().length > 0){

            let data = await azquoSend("op=getdropdownlistforquery&choice=" + encodeURIComponent($('#colsaql').val())+ "&sessionid=${pageContext.session.id}&database="  + encodeURIComponent($('#database').val()));
            var userChoices = await data.json();

            //alert(userChoices);
            var rows = '';
            // todo limit cols??
                var row = '<tr>';
            $.each(userChoices, function(index, item) {
                if (item.toString().startsWith("Error :")){
                    row += '<td><span  style="color:red">' + item + '</span></td>';
                } else {
                    row += '<td>' + item + '</td>';
                }
            });
            rows += row + '<tr>';
            for (j = 0; j < 10; j++){
                row = '<tr>';
                $.each(userChoices, function(index, item) {
                    row += '<td></td>';
                });
                rows += row + '<tr>';
            }
        }

        if ($('#rowsaql').val().length > 0 && $('#colsaql').val().length > 0){




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

            let data = await azquoSend("op=loadregion&reportname=meh&json=" + encodeURIComponent(JSON.stringify({
                reportId : '',
                sheetName : "sheetname",
                region: "$region",
                optionsSource: "",
                rowHeadings: [[$('#rowsaql').val()]],
                columnHeadings: [[$('#colsaql').val()]],
                context: [[$('#context').val()]],
                query: [[]],
                userContext: "",
                data: [[]],
                comments: [[]]
            })) + "&sessionid=${pageContext.session.id}&database="  + encodeURIComponent($('#database').val()));;
            var dataJson = await data.json();


            //alert(dataJson);
            var rows = '';
            // todo limit cols??
            $.each(dataJson.columnHeadings, function(index, item) {
                var row = '<tr><td></td>';
                $.each(item, function(index, field) {
                        row += '<td>' + field + '</td>';
                });
                rows += row + '<tr>';
            });
            $.each(dataJson.data, function(index, item) {
                var row = '<tr><td>' + dataJson.rowHeadings[index] + '</td>';
                $.each(item, function(index, field) {
                    if (field.toString().startsWith("Error :")){
                        row += '<td><span  style="color:red">' + item + '</span></td>';
                    } else {
                        row += '<td>' + field + '</td>';
                    }
                });
                rows += row + '<tr>';
            });
        }
        $('#data-table').html(rows);
    }



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
</script>
<div class="box">
    Database :
    <div class="select is-small">
        <select name="database" id="database"><c:forEach items="${databases}" var="database">
            <option value="${database.name}">${database.name}</option>
        </c:forEach></select>
    </div>
    <a href="#" class="button is-small" onclick="getData()">Run Query</a>&nbsp;
    <br/>
    <br/>Rows AQL : <input class="input is-small" name="rowsaql" id="rowsaql"/> &nbsp;Cols AQL : <input class="input is-small" name="colsaql" id="colsaql"/>&nbsp;Context : <input class="input is-small" name="context" id="context"/>
    <br/>
    <br/>
    <div class="table-container">
    <table class="table is-bordered" id="data-table">
    </table>
    </div>
</div>
<%@ include file="../includes/admin_footer.jsp" %>
