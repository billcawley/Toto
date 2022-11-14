//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.zkoss.poi.xssf.usermodel.charts;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxDataSource;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDPt;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDoughnutChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumDataSource;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPieSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTSerTx;
import org.zkoss.poi.ss.usermodel.Chart;
import org.zkoss.poi.ss.usermodel.charts.AbstractCategoryDataSerie;
import org.zkoss.poi.ss.usermodel.charts.CategoryData;
import org.zkoss.poi.ss.usermodel.charts.CategoryDataSerie;
import org.zkoss.poi.ss.usermodel.charts.ChartAxis;
import org.zkoss.poi.ss.usermodel.charts.ChartDataSource;
import org.zkoss.poi.ss.usermodel.charts.ChartTextSource;
import org.zkoss.poi.xssf.usermodel.XSSFChart;

public class XSSFDoughnutChartData implements CategoryData {
    private CTDoughnutChart ctDoughnutChart;
    private List<CategoryDataSerie> series;

    public XSSFDoughnutChartData() {
        this.series = new ArrayList();
    }

    public XSSFDoughnutChartData(XSSFChart chart) {
        this();
        CTPlotArea plotArea = chart.getCTChart().getPlotArea();
        CTDoughnutChart[] plotCharts = plotArea.getDoughnutChartArray();
        if (plotCharts != null && plotCharts.length > 0) {
            this.ctDoughnutChart = plotCharts[0];
        }

        if (this.ctDoughnutChart != null) {
            CTPieSer[] bsers = this.ctDoughnutChart.getSerArray();

            for(int j = 0; j < bsers.length; ++j) {
                CTPieSer ser = bsers[j];
                CTSerTx serTx = ser.getTx();
                ChartTextSource title = serTx == null ? null : new XSSFChartTextSource(serTx);
                ChartDataSource<String> cats = new XSSFChartAxDataSource(ser.getCat());
                ChartDataSource<Double> vals = new XSSFChartNumDataSource(ser.getVal());
                this.addSerie(j, title, cats, vals);
            }

            this.ctDoughnutChart.addNewFirstSliceAng().setVal(0);
            this.ctDoughnutChart.addNewHoleSize().setVal(50);
        }

    }

    public CategoryDataSerie addSerie(int order, ChartTextSource title, ChartDataSource<?> cats, ChartDataSource<? extends Number> vals) {
        if (!vals.isNumeric()) {
            throw new IllegalArgumentException("Doughnut data source must be numeric.");
        } else {
            XSSFDoughnutChartData.Serie newSerie = new XSSFDoughnutChartData.Serie(order, order, title, cats, vals);
            this.series.add(newSerie);
            return newSerie;
        }
    }

    public void fillChart(Chart chart, ChartAxis... axis) {
        if (!(chart instanceof XSSFChart)) {
            throw new IllegalArgumentException("Chart must be instance of XSSFChart");
        } else {
            if (this.ctDoughnutChart == null) {
                XSSFChart xssfChart = (XSSFChart)chart;
                CTPlotArea plotArea = xssfChart.getCTChart().getPlotArea();
                this.ctDoughnutChart = plotArea.addNewDoughnutChart();
                this.ctDoughnutChart.addNewVaryColors().setVal(true);
                Iterator var5 = this.series.iterator();

                while(var5.hasNext()) {
                    CategoryDataSerie s = (CategoryDataSerie)var5.next();
                    ((XSSFDoughnutChartData.Serie)s).addToChart(this.ctDoughnutChart);
                }

                this.ctDoughnutChart.addNewFirstSliceAng().setVal(0);
                this.ctDoughnutChart.addNewHoleSize().setVal(50);
            }

        }
    }

    public List<? extends CategoryDataSerie> getSeries() {
        return this.series;
    }

    static class Serie extends AbstractCategoryDataSerie {
        protected Serie(int id, int order, ChartTextSource title, ChartDataSource<?> cats, ChartDataSource<? extends Number> vals) {
            super(id, order, title, cats, vals);
        }

        protected void addToChart(CTDoughnutChart ctDoughnutChart) {
            CTPieSer pieSer = ctDoughnutChart.addNewSer();
            pieSer.addNewIdx().setVal((long)this.id);
            pieSer.addNewOrder().setVal((long)this.order);
            if (this.title != null) {
                CTSerTx tx = pieSer.addNewTx();
                XSSFChartUtil.buildSerTx(tx, this.title);
            }

            if (this.color != null) {
                XSSFChartUtil.buildFillColor(pieSer.addNewSpPr(), this.color);
            }

            if (this.dataPointColors != null) {
                this.dataPointColors.forEach((k, v) -> {
                    CTDPt ctDpt = pieSer.addNewDPt();
                    ctDpt.addNewIdx().setVal((long)k);
                    XSSFChartUtil.buildFillColor(ctDpt.addNewSpPr(), v);
                });
            }

            if (this.categories != null && this.categories.getPointCount() > 0) {
                CTAxDataSource cats = pieSer.addNewCat();
                XSSFChartUtil.buildAxDataSource(cats, this.categories);
            }

            CTNumDataSource vals = pieSer.addNewVal();
            XSSFChartUtil.buildNumDataSource(vals, this.values);
        }
    }
}
