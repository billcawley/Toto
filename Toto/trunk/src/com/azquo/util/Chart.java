package com.azquo.util;

/**
 * Created by bill on 02/05/14.
 */

import static com.googlecode.charts4j.Color.*;
import static com.googlecode.charts4j.UrlUtil.normalize;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.googlecode.charts4j.*;
import org.junit.BeforeClass;
import org.junit.Test;


public class Chart {
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Logger.global.setLevel(Level.ALL);
    }

    private double getScaleTop(double maxVal) {

        double scaleTop = 1;
        while (scaleTop < maxVal) {
            if (2 * scaleTop >= maxVal) return 2 * scaleTop;
            if (5 * scaleTop >= maxVal) return 5 * scaleTop;
            scaleTop *= 10;
        }
        return scaleTop;

    }

    public String drawChart(String title, String[] rowHeadings, String[] colHeadings, List<List<Number>> data) {
        // EXAMPLE CODE START
        // Defining data plots.


        //Color[] colour = {BLUEVIOLET, ORANGERED, LIMEGREEN, BLUEVIOLET, ORANGERED, LIMEGREEN, BLUEVIOLET, ORANGERED, LIMEGREEN, BLUEVIOLET, ORANGERED, LIMEGREEN, BLUEVIOLET, ORANGERED, LIMEGREEN};
        int[] colorTable = {0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080, 0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF, 0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF, 0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99, 0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696, 0x003300, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366};
        Color color;


        Color[] colour = {AQUA, AQUAMARINE, AZURE, BEIGE, BISQUE, BLACK, BLANCHEDALMOND, BLUE, BLUEVIOLET, BROWN, BURLYWOOD, CADETBLUE, CHARTREUSE, CHOCOLATE, CORAL, CORNFLOWERBLUE, CORNSILK, CRIMSON, CYAN, DARKBLUE, DARKCYAN, DARKGOLDENROD, DARKGRAY, DARKGREEN, DARKKHAKI, DARKMAGENTA, DARKOLIVEGREEN, DARKORANGE, DARKORCHID, DARKRED, DARKSALMON, DARKSEAGREEN, DARKSLATEBLUE, DARKSLATEGRAY, DARKTURQUOISE, DARKVIOLET, DEEPPINK, DEEPSKYBLUE, DIMGRAY, DODGERBLUE, FIREBRICK, FLORALWHITE, FORESTGREEN, FUCHSIA, GAINSBORO, GHOSTWHITE, GOLD, GOLDENROD, GRAY, GREEN, GREENYELLOW, HONEYDEW, HOTPINK, INDIANRED, INDIGO, IVORY, KHAKI, LAVENDER, LAVENDERBLUSH, LAWNGREEN, LEMONCHIFFON, LIGHTBLUE, LIGHTCORAL, LIGHTCYAN, LIGHTGOLDENRODYELLOW, LIGHTGREEN, LIGHTGREY, LIGHTPINK, LIGHTSALMON, LIGHTSEAGREEN, LIGHTSKYBLUE, LIGHTSLATEGRAY, LIGHTSTEELBLUE, LIGHTYELLOW, LIME, LIMEGREEN, LINEN, MAGENTA, MAROON, MEDIUMAQUAMARINE, MEDIUMBLUE, MEDIUMORCHID, MEDIUMPURPLE, MEDIUMSEAGREEN, MEDIUMSLATEBLUE, MEDIUMSPRINGGREEN, MEDIUMTURQUOISE, MEDIUMVIOLETRED, MIDNIGHTBLUE, MINTCREAM, MISTYROSE, MOCCASIN, NAVAJOWHITE, NAVY, OLDLACE, OLIVE, OLIVEDRAB, ORANGE, ORANGERED, ORCHID, PALEGOLDENROD, PALEGREEN, PALETURQUOISE, PALEVIOLETRED, PAPAYAWHIP, PEACHPUFF, PERU, PINK, PLUM, POWDERBLUE, PURPLE, RED, ROSYBROWN, ROYALBLUE, SADDLEBROWN, SALMON, SANDYBROWN, SEAGREEN, SEASHELL, SIENNA, SILVER, SKYBLUE, SLATEBLUE, SLATEGRAY, SNOW, SPRINGGREEN, STEELBLUE, TAN, TEAL, THISTLE, TOMATO, TURQUOISE, VIOLET, WHEAT, WHITE, WHITESMOKE, YELLOW, YELLOWGREEN};
//Excel colours for the first dozen.
        colour[0] = Color.newColor("800000");
        colour[1] = Color.newColor("008000");
        colour[2] = Color.newColor("000080");
        colour[3] = Color.newColor("808000");
        colour[4] = Color.newColor("800080");
        colour[5] = Color.newColor("008080");
        colour[6] = Color.newColor("C0C0C0");
        colour[7] = Color.newColor("808080");
        colour[8] = Color.newColor("9999FF");
        colour[9] = Color.newColor("993366");
        colour[10] = Color.newColor("FFFFCC");
        colour[11] = Color.newColor("CCFFFF");
        colour[12] = Color.newColor("660066");
        colour[13] = Color.newColor("FF8080");


        int rows = rowHeadings.length;
        if (rows > colour.length) {
            rows = colour.length;
        }
        double maxVal = 0;
        for (int col = 0; col < colHeadings.length; col++) {
            double colTotal = 0.0;
            for (int row = 0; row < rowHeadings.length; row++) {
                colTotal += (Double) data.get(row).get(col);
            }
            if (colTotal > maxVal) maxVal = colTotal;
        }
        Double scaleTop = getScaleTop(maxVal);
        List<Data> scaleData = DataUtil.scaleDataList(data);
        List<BarChartPlot> plot = new ArrayList<BarChartPlot>();
        for (int row = 0; row < rows; row++) {
            plot.add(Plots.newBarChartPlot(DataUtil.scaleWithinRange(0, scaleTop, data.get(row)), colour[row], rowHeadings[row]));
        }
        //BarChartPlot team1 = Plots.newBarChartPlot(Data.newData(25, 43, 12, 30), BLUEVIOLET, "Team A");
        //BarChartPlot team2 = Plots.newBarChartPlot(Data.newData(8, 35, 11, 5), ORANGERED, "Team B");
        //BarChartPlot team3 = Plots.newBarChartPlot(Data.newData(10, 20, 30, 30), LIMEGREEN, "Team C");

        // Instantiating chart.
        BarChart chart = GCharts.newBarChart(plot);

        // Defining axis info and styles
        AxisStyle axisStyle = AxisStyle.newAxisStyle(BLACK, 13, AxisTextAlignment.CENTER);
        //AxisLabels score = AxisLabelsFactory.newAxisLabels("Score", 50.0);
        //score.setAxisStyle(axisStyle);
        //AxisLabels year = AxisLabelsFactory.newAxisLabels("Year", 50.0);
        //year.setAxisStyle(axisStyle);

        // Adding axis info to chart.
        chart.addXAxisLabels(AxisLabelsFactory.newAxisLabels(colHeadings));
        chart.addYAxisLabels(AxisLabelsFactory.newNumericRangeAxisLabels(0, 100));
        //chart.addYAxisLabels(score);
        //chart.addXAxisLabels(year);
        int chartHeight = 320;
        int chartWidth = 800;
        int barWidth = (chartWidth - 200) / colHeadings.length;
        int spacing = barWidth / 5;
        barWidth -= spacing;
        chart.setSize(chartWidth, chartHeight);
        chart.setBarWidth(BarChart.AUTO_RESIZE);
        chart.setSpaceWithinGroupsOfBars(spacing);
        chart.setDataStacked(true);
        chart.setTitle(title, BLACK, 16);
        chart.setLegendPosition(LegendPosition.RIGHT);
        int gridparam1 = 100;
        int gridparam2 = 10;
        int gridparam3 = 3;
        int gridparam4 = 2;
        chart.setGrid(gridparam1, gridparam2, gridparam3, gridparam4);
        chart.setBackgroundFill(Fills.newSolidFill(ALICEBLUE));
        LinearGradientFill fill = Fills.newLinearGradientFill(0, LAVENDER, 100);
        fill.addColorAndOffset(WHITE, 0);
        chart.setAreaFill(fill);
        String url = chart.toURLString();
        // EXAMPLE CODE END. Use this url string in your web or
        // Internet application.
        return url;
    }
}


