<!DOCTYPE HTML>
<html>
<head>
    <script>
        async function updateChart(){
            var cellsAndHeadingsForExcel = await azquoSend("/api/Excel/", "op=loadregion&reportname=" + encodeURIComponent("<%= request.getParameter("report") %>") + "&json=" + encodeURIComponent(<%= request.getParameter("json") %>) + "&sessionid=<%= session.getId() %>");
//    console.log(JSON.stringify(cellsAndHeadingsForExcel));
            var newChartData = [];
            var newChartData2 = [];
            var grandTotal = 0;
            var rowTotals = [];
            var i;

            var visitorsData = {};
            // atlanta sydney etc needs to be the col headings
            for (i = 0; i < cellsAndHeadingsForExcel.rowHeadings.length; i++) {
                var rowTotal = 0;
                dPoints = [];
                var j;
                for (j = 0; j < cellsAndHeadingsForExcel.columnHeadings[0].length; j++){
                    dPoints.push(
                        { label: "" + cellsAndHeadingsForExcel.columnHeadings[0][j] , y: parseFloat(cellsAndHeadingsForExcel.data[i][j]) }
                    );
                    rowTotal += parseFloat(cellsAndHeadingsForExcel.data[i][j]);
                }
                rowTotals.push(
                    { y: rowTotal, name: cellsAndHeadingsForExcel.rowHeadings[i] + ""}
                )
                grandTotal += rowTotal;
                newChartData.push({
                    type: "spline",
                    name: cellsAndHeadingsForExcel.rowHeadings[i] + "",
                    showInLegend: true,
                    dataPoints: dPoints
                });
                newChartData2.push({
                    type: "stackedColumn",
                    name: cellsAndHeadingsForExcel.rowHeadings[i] + "",
                    showInLegend: true,
                    dataPoints: dPoints
                });

                visitorsData[cellsAndHeadingsForExcel.rowHeadings[i] + ""] = [{
                    name: cellsAndHeadingsForExcel.rowHeadings[i] + "",
                    type: "column",
                    dataPoints: dPoints
                }]
            }


            visitorsData["<%= request.getParameter("report") %>"] = [{
                click: visitorsChartDrilldownHandler,
                cursor: "pointer",
                explodeOnClick: false,
                innerRadius: "75%",
                legendMarkerType: "square",
                name: "<%= request.getParameter("report") %>",
                radius: "100%",
                showInLegend: true,
                startAngle: 90,
                type: "doughnut",
                dataPoints: rowTotals
            }];

//    console.log(JSON.stringify(newChartData));

            chart = new CanvasJS.Chart("chartContainer", {
                animationEnabled: true,
                exportEnabled: true,
                title:{
                    text: "<%= request.getParameter("report") %>"
                },
                toolTip: {
                    shared: true
                },
                legend:{
                    cursor:"pointer",
//            itemclick: toggleDataSeries
                },
                data: newChartData
            });
            chart.render();

            chart2 = new CanvasJS.Chart("chartContainer2", {
                animationEnabled: true,
                exportEnabled: true,
                title:{
                    text: "<%= request.getParameter("report") %>"
                },
                toolTip: {
                    shared: true
                },
                legend:{
                    cursor:"pointer",
//            itemclick: toggleDataSeries
                },
                data: newChartData2
            });
            //chart2.render();



            var newVSReturningVisitorsOptions = {
                animationEnabled: true,
                theme: "light2",
                title: {
                    text: "<%= request.getParameter("report") %>"
                },
                subtitles: [{
                    text: "Click on Any Segment to Drilldown",
                    backgroundColor: "#2eacd1",
                    fontSize: 16,
                    fontColor: "white",
                    padding: 5
                }],
                legend: {
                    fontFamily: "calibri",
                    fontSize: 14,
                    itemTextFormatter: function (e) {
                        return e.dataPoint.name + ": " + Math.round(e.dataPoint.y / grandTotal * 100) + "%";
                    }
                },
                data: []
            };

            var visitorsDrilldownedChartOptions = {
                animationEnabled: true,
                theme: "light2",
                axisX: {
                    labelFontColor: "#717171",
                    lineColor: "#a2a2a2",
                    tickColor: "#a2a2a2"
                },
                axisY: {
                    gridThickness: 0,
                    includeZero: false,
                    labelFontColor: "#717171",
                    lineColor: "#a2a2a2",
                    tickColor: "#a2a2a2",
                    lineThickness: 1
                },
                data: []
            };

            chart3 = new CanvasJS.Chart("chartContainer3", newVSReturningVisitorsOptions);
            chart3.options.data = visitorsData["<%= request.getParameter("report") %>"];
            chart3.render();

            function visitorsChartDrilldownHandler(e) {
                chart3 = new CanvasJS.Chart("chartContainer3", visitorsDrilldownedChartOptions);
                chart3.options.data = visitorsData[e.dataPoint.name];
                chart3.options.title = { text: e.dataPoint.name }
                chart3.render();
                $("#backButton").toggleClass("invisible");
            }

            $("#backButton").click(function() {
                $(this).toggleClass("invisible");
                chart3 = new CanvasJS.Chart("chartContainer3", newVSReturningVisitorsOptions);
                chart3.options.data = visitorsData["<%= request.getParameter("report") %>"];
                chart3.render();
            });


        }

        async function azquoSend(url, info) {
            try {
                let data = await fetch(url, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: info
                });
                //console.log(data.text())
                var newVar = await data.json();
                return newVar;
            } catch (e) {
                console.log(e)
            }
        }
    </script>
    <style>
        #backButton {
            border-radius: 4px;
            padding: 8px;
            border: none;
            font-size: 16px;
            background-color: #2eacd1;
            color: white;
            position: absolute;
            top: 10px;
            right: 10px;
            cursor: pointer;
        }
        .invisible {
            display: none;
        }
    </style>
</head>
<body onload="updateChart()">
<script src="https://canvasjs.com/assets/script/jquery-1.11.1.min.js"></script>
<script src="https://canvasjs.com/assets/script/canvasjs.min.js"></script>
<div id="chartContainer3" style="height: 370px; width: 100%;"></div>
<button class="btn invisible" id="backButton">< Back</button>
<!--<button onclick="updateChart(); return false;">Update Chart</button>-->
<div id="chartContainer" style="height: 370px; width: 100%;"></div>
<div id="chartContainer2" style="height: 370px; width: 100%;"></div>
</body>
</html>