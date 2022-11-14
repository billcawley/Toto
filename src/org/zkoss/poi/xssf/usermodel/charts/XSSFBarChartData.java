//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
// EFC note - as with a few others I've not modified this from the decompiler and yet is solves a runtime linking problem . . .

package org.zkoss.poi.xssf.usermodel.charts;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxDataSource;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarChart;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarDir;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarGrouping;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBarSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDPt;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumDataSource;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTOverlap;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTSerTx;
import org.zkoss.poi.ss.usermodel.Chart;
import org.zkoss.poi.ss.usermodel.charts.AbstractCategoryDataSerie;
import org.zkoss.poi.ss.usermodel.charts.CategoryData;
import org.zkoss.poi.ss.usermodel.charts.CategoryDataSerie;
import org.zkoss.poi.ss.usermodel.charts.ChartAxis;
import org.zkoss.poi.ss.usermodel.charts.ChartDataSource;
import org.zkoss.poi.ss.usermodel.charts.ChartDirection;
import org.zkoss.poi.ss.usermodel.charts.ChartGrouping;
import org.zkoss.poi.ss.usermodel.charts.ChartTextSource;
import org.zkoss.poi.xssf.usermodel.XSSFChart;

public class XSSFBarChartData implements CategoryData {
    private ChartGrouping _chartGrouping;
    private ChartDirection _chartDirection;
    private CTBarChart ctBarChart;
    private int _overlap;
    private List<CategoryDataSerie> series;

    public XSSFBarChartData() {
        this.series = new ArrayList();
        this.setBarDirection(ChartDirection.HORIZONTAL);
    }

    public XSSFBarChartData(XSSFChart chart) {
        this();
        CTPlotArea plotArea = chart.getCTChart().getPlotArea();
        CTBarChart[] plotCharts = plotArea.getBarChartArray();
        if (plotCharts != null && plotCharts.length > 0) {
            this.ctBarChart = plotCharts[0];
        }

        if (this.ctBarChart != null) {
            CTBarSer[] bsers = this.ctBarChart.getSerArray();

            for(int j = 0; j < bsers.length; ++j) {
                CTBarSer ser = bsers[j];
                CTSerTx serTx = ser.getTx();
                ChartTextSource title = serTx == null ? null : new XSSFChartTextSource(serTx);
                ChartDataSource<String> cats = new XSSFChartAxDataSource(ser.getCat());
                ChartDataSource<Double> vals = new XSSFChartNumDataSource(ser.getVal());
                this.addSerie(j, title, cats, vals);
            }
        }

    }

    public ChartGrouping getGrouping() {
        if (this.ctBarChart != null) {
            this._chartGrouping = XSSFChartUtil.toChartGroupingForBar(this.ctBarChart.getGrouping());
        }

        return this._chartGrouping;
    }

    public void setGrouping(ChartGrouping grouping) {
        this._chartGrouping = grouping;
        if (this.ctBarChart != null) {
            CTBarGrouping ctgr = this.ctBarChart.getGrouping();
            if (ctgr == null) {
                ctgr = this.ctBarChart.addNewGrouping();
            }

            ctgr.setVal(XSSFChartUtil.fromChartGroupingForBar(grouping));
        }

    }

    public ChartDirection getBarDirection() {
        if (this.ctBarChart != null) {
            this._chartDirection = XSSFChartUtil.toBarDirection(this.ctBarChart.getBarDir());
        }

        return this._chartDirection;
    }

    public void setBarDirection(ChartDirection barDir) {
        this._chartDirection = barDir;
        if (this.ctBarChart != null) {
            CTBarDir dir = this.ctBarChart.getBarDir();
            if (dir == null) {
                dir = this.ctBarChart.addNewBarDir();
            }

            dir.setVal(XSSFChartUtil.fromBarDirection(barDir));
        }

    }

    public CategoryDataSerie addSerie(int order, ChartTextSource title, ChartDataSource<?> cats, ChartDataSource<? extends Number> vals) {
        if (!vals.isNumeric()) {
            throw new IllegalArgumentException("Bar data source must be numeric.");
        } else {
            XSSFBarChartData.Serie newSerie = new XSSFBarChartData.Serie(order, order, title, cats, vals);
            this.series.add(newSerie);
            return newSerie;
        }
    }

    public void fillChart(Chart chart, ChartAxis... axis) {
        if (!(chart instanceof XSSFChart)) {
            throw new IllegalArgumentException("Chart must be instance of XSSFChart");
        } else {
            if (this.ctBarChart == null) {
                XSSFChart xssfChart = (XSSFChart)chart;
                CTPlotArea plotArea = xssfChart.getCTChart().getPlotArea();
                this.ctBarChart = plotArea.addNewBarChart();
                this.ctBarChart.addNewVaryColors().setVal(true);
                this.setBarDirection(this._chartDirection);
                this.setGrouping(this._chartGrouping);
                this.setBarOverlap(this._overlap);
                Iterator var5 = this.series.iterator();

                while(var5.hasNext()) {
                    CategoryDataSerie s = (CategoryDataSerie)var5.next();
                    ((XSSFBarChartData.Serie)s).addToChart(this.ctBarChart);
                }
            }

            ChartAxis[] var7 = axis;
            int var8 = axis.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                ChartAxis a = var7[var9];
                this.ctBarChart.addNewAxId().setVal(a.getId());
            }

        }
    }

    public List<? extends CategoryDataSerie> getSeries() {
        return this.series;
    }

    public int getBarOverlap() {
        return this._overlap;
    }

    public void setBarOverlap(int overlap) {
        this._overlap = overlap;
        if (this.ctBarChart != null) {
            CTOverlap ov = this.ctBarChart.getOverlap();
            if (ov == null) {
                ov = this.ctBarChart.addNewOverlap();
            }

            ov.setVal((byte)overlap);
        }

    }

    static class Serie extends AbstractCategoryDataSerie {
        protected Serie(int id, int order, ChartTextSource title, ChartDataSource<?> cats, ChartDataSource<? extends Number> vals) {
            super(id, order, title, cats, vals);
        }

        protected void addToChart(CTBarChart ctBarChart) {
            CTBarSer barSer = ctBarChart.addNewSer();
            barSer.addNewIdx().setVal((long)this.id);
            barSer.addNewOrder().setVal((long)this.order);
            if (this.title != null) {
                CTSerTx tx = barSer.addNewTx();
                XSSFChartUtil.buildSerTx(tx, this.title);
            }

            if (this.color != null) {
                XSSFChartUtil.buildFillColor(barSer.addNewSpPr(), this.color);
            }

            if (this.dataPointColors != null) {
                this.dataPointColors.forEach((k, v) -> {
                    CTDPt ctDpt = barSer.addNewDPt();
                    ctDpt.addNewIdx().setVal((long)k);
                    XSSFChartUtil.buildFillColor(ctDpt.addNewSpPr(), v);
                });
            }

            if (this.categories != null && this.categories.getPointCount() > 0) {
                CTAxDataSource cats = barSer.addNewCat();
                XSSFChartUtil.buildAxDataSource(cats, this.categories);
            }

            CTNumDataSource vals = barSer.addNewVal();
            XSSFChartUtil.buildNumDataSource(vals, this.values);
        }
    }
}
