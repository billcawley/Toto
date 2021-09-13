//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.zkoss.poi.xssf.usermodel.charts;

import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBoolean;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTCrosses;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumFmt;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTScaling;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTTickLblPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTUnsignedInt;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTValAx;
import org.openxmlformats.schemas.drawingml.x2006.chart.STCrossBetween;
import org.openxmlformats.schemas.drawingml.x2006.chart.STTickLblPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.STTickMark;
import org.openxmlformats.schemas.drawingml.x2006.chart.STCrossBetween.Enum;
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNoFillProperties.Factory;
import org.zkoss.poi.ss.usermodel.charts.AxisCrossBetween;
import org.zkoss.poi.ss.usermodel.charts.AxisCrosses;
import org.zkoss.poi.ss.usermodel.charts.AxisOrientation;
import org.zkoss.poi.ss.usermodel.charts.AxisPosition;
import org.zkoss.poi.ss.usermodel.charts.ChartAxis;
import org.zkoss.poi.ss.usermodel.charts.ValueAxis;
import org.zkoss.poi.xssf.usermodel.XSSFChart;

public class XSSFValueAxis extends XSSFChartAxis implements ValueAxis {
    private CTValAx ctValAx;

    public XSSFValueAxis(XSSFChart chart, long id, AxisPosition pos) {
        super(chart);
        this.createAxis(id, pos);
    }

    public XSSFValueAxis(XSSFChart chart, CTValAx ctValAx) {
        super(chart);
        this.ctValAx = ctValAx;
    }

    protected CTUnsignedInt getCtAxId() {
        return this.ctValAx.getAxId();
    }

    protected CTBoolean getCtDelete() {
        return this.ctValAx.getDelete();
    }

    // 2 efc hacks in . . .
    @Override
    public long getId() {
        return this.ctValAx.getAxId().getVal();
    }

    @Override
    public void setId(long l) {
        this.ctValAx.getAxId().setVal(l);
    }

    public boolean isDelete() {
        return this.ctValAx.getDelete().getVal();
    }

    public void setDelete(boolean delete) {
        this.ctValAx.getDelete().setVal(delete);
    }

    public void setCrossBetween(AxisCrossBetween crossBetween) {
        this.ctValAx.getCrossBetween().setVal(fromCrossBetween(crossBetween));
    }

    public AxisCrossBetween getCrossBetween() {
        return toCrossBetween(this.ctValAx.getCrossBetween().getVal());
    }

    protected CTAxPos getCTAxPos() {
        return this.ctValAx.getAxPos();
    }

    protected CTNumFmt getCTNumFmt() {
        return this.ctValAx.isSetNumFmt() ? this.ctValAx.getNumFmt() : this.ctValAx.addNewNumFmt();
    }

    protected CTScaling getCTScaling() {
        return this.ctValAx.getScaling();
    }

    protected CTCrosses getCTCrosses() {
        return this.ctValAx.getCrosses();
    }

    public void crossAxis(ChartAxis axis) {
        this.ctValAx.getCrossAx().setVal(axis.getId());
    }

    public void setMajorGridline(boolean majorGridline) {
        if (majorGridline) {
            this.fillMajorGridline(this.ctValAx.addNewMajorGridlines());
        } else {
            this.ctValAx.unsetMajorGridlines();
        }

    }

    private void createAxis(long id, AxisPosition pos) {
        this.ctValAx = this.chart.getCTChart().getPlotArea().addNewValAx();
        this.ctValAx.addNewAxId().setVal(id);
        this.ctValAx.addNewAxPos();
        this.ctValAx.addNewDelete();
        this.ctValAx.addNewScaling();
        this.ctValAx.addNewCrossBetween();
        this.ctValAx.addNewCrosses();
        this.ctValAx.addNewCrossAx();
        this.ctValAx.addNewTickLblPos().setVal(STTickLblPos.NEXT_TO);
        this.ctValAx.addNewMajorTickMark().setVal(STTickMark.OUT);
        this.ctValAx.addNewMinorTickMark().setVal(STTickMark.NONE);
        CTShapeProperties spPr = this.ctValAx.addNewSpPr();
        spPr.setNoFill(Factory.newInstance());
        CTLineProperties lnPr = org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties.Factory.newInstance();
        lnPr.setNoFill(Factory.newInstance());
        spPr.setLn(lnPr);
        this.setPosition(pos);
        this.setOrientation(AxisOrientation.MIN_MAX);
        this.setCrossBetween(AxisCrossBetween.BETWEEN);
        this.setCrosses(AxisCrosses.AUTO_ZERO);
    }

    private static Enum fromCrossBetween(AxisCrossBetween crossBetween) {
        switch(crossBetween) {
            case BETWEEN:
                return STCrossBetween.BETWEEN;
            case MIDPOINT_CATEGORY:
                return STCrossBetween.MID_CAT;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static AxisCrossBetween toCrossBetween(Enum ctCrossBetween) {
        switch(ctCrossBetween.intValue()) {
            case 1:
                return AxisCrossBetween.BETWEEN;
            case 2:
                return AxisCrossBetween.MIDPOINT_CATEGORY;
            default:
                throw new IllegalArgumentException();
        }
    }

    protected CTTickLblPos getTickLblPos() {
        return this.ctValAx.getTickLblPos();
    }
}
