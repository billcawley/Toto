//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.zkoss.poi.xssf.usermodel.charts;

import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBoolean;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTCatAx;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTCrosses;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLblAlgn;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumFmt;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTScaling;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTTickLblPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTUnsignedInt;
import org.openxmlformats.schemas.drawingml.x2006.chart.STLblAlgn;
import org.openxmlformats.schemas.drawingml.x2006.chart.STTickLblPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.STTickMark;
import org.openxmlformats.schemas.drawingml.x2006.chart.STLblAlgn.Enum;
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNoFillProperties.Factory;
import org.zkoss.poi.ss.usermodel.charts.AxisCrosses;
import org.zkoss.poi.ss.usermodel.charts.AxisLabelAlign;
import org.zkoss.poi.ss.usermodel.charts.AxisOrientation;
import org.zkoss.poi.ss.usermodel.charts.AxisPosition;
import org.zkoss.poi.ss.usermodel.charts.CategoryAxis;
import org.zkoss.poi.ss.usermodel.charts.ChartAxis;
import org.zkoss.poi.xssf.usermodel.XSSFChart;

public class XSSFCategoryAxis extends XSSFChartAxis implements CategoryAxis {
    private CTCatAx ctCatAx;

    public XSSFCategoryAxis(XSSFChart chart, long id, AxisPosition pos) {
        super(chart);
        this.createAxis(id, pos);
    }

    public XSSFCategoryAxis(XSSFChart chart, CTCatAx ctCatAx) {
        super(chart);
        this.ctCatAx = ctCatAx;
    }

    protected CTUnsignedInt getCtAxId() {
        return this.ctCatAx.getAxId();
    }

    protected CTBoolean getCtDelete() {
        return this.ctCatAx.isSetDelete() ? this.ctCatAx.getDelete() : this.ctCatAx.addNewDelete();
    }

    protected CTAxPos getCTAxPos() {
        return this.ctCatAx.getAxPos();
    }

    protected CTNumFmt getCTNumFmt() {
        return this.ctCatAx.isSetNumFmt() ? this.ctCatAx.getNumFmt() : this.ctCatAx.addNewNumFmt();
    }

    protected CTScaling getCTScaling() {
        return this.ctCatAx.getScaling();
    }

    protected CTCrosses getCTCrosses() {
        return this.ctCatAx.getCrosses();
    }

    // EFC hacking in these 4, hope ok!
    @Override
    public long getId() {
        return this.ctCatAx.getAxId().getVal();
    }

    @Override
    public void setId(long l) {
        this.ctCatAx.getAxId().setVal(l);
    }

    @Override
    public boolean isDelete() {
        return this.ctCatAx.isSetDelete() ? this.ctCatAx.getDelete().getVal() : this.ctCatAx.addNewDelete().getVal();
    }

    @Override
    public void setDelete(boolean b) {
        if (this.ctCatAx.isSetDelete()){
            this.ctCatAx.getDelete().setVal(b);
        } else {
            this.ctCatAx.addNewDelete().setVal(b);
        }
    }

    public void crossAxis(ChartAxis axis) {
        this.ctCatAx.getCrossAx().setVal(axis.getId());
    }

    public void setMajorGridline(boolean majorGridline) {
        if (majorGridline) {
            this.fillMajorGridline(this.ctCatAx.addNewMajorGridlines());
        } else {
            this.ctCatAx.unsetMajorGridlines();
        }

    }

    private void createAxis(long id, AxisPosition pos) {
        this.ctCatAx = this.chart.getCTChart().getPlotArea().addNewCatAx();
        this.ctCatAx.addNewAxId().setVal(id);
        this.ctCatAx.addNewAxPos();
        this.ctCatAx.addNewDelete();
        this.ctCatAx.addNewScaling();
        this.ctCatAx.addNewCrosses();
        this.ctCatAx.addNewCrossAx();
        this.ctCatAx.addNewTickLblPos().setVal(STTickLblPos.NEXT_TO);
        this.ctCatAx.addNewMajorTickMark().setVal(STTickMark.OUT);
        this.ctCatAx.addNewMinorTickMark().setVal(STTickMark.NONE);
        CTShapeProperties spPr = this.ctCatAx.addNewSpPr();
        spPr.setNoFill(Factory.newInstance());
        CTLineProperties lnPr = org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties.Factory.newInstance();
        lnPr.setNoFill(Factory.newInstance());
        spPr.setLn(lnPr);
        this.setPosition(pos);
        this.setOrientation(AxisOrientation.MIN_MAX);
        this.setCrosses(AxisCrosses.AUTO_ZERO);
    }

    protected CTTickLblPos getTickLblPos() {
        return this.ctCatAx.getTickLblPos();
    }

    public AxisLabelAlign getLabelAlign() {
        return toLabelAlign(this.ctCatAx.getLblAlgn());
    }

    public void setLabelAlign(AxisLabelAlign labelAlign) {
        CTLblAlgn ctLblAlgn;
        if (!this.ctCatAx.isSetLblAlgn()) {
            ctLblAlgn = this.ctCatAx.addNewLblAlgn();
        } else {
            ctLblAlgn = this.ctCatAx.getLblAlgn();
        }

        ctLblAlgn.setVal(fromLabelAlign(labelAlign));
    }

    public int getLabelOffset() {
        return 0;
    }

    public void setLabelOffset(int offset) {
    }

    private static Enum fromLabelAlign(AxisLabelAlign lblAlign) {
        switch(lblAlign) {
            case LEFT:
                return STLblAlgn.L;
            case CENTER:
                return STLblAlgn.CTR;
            case RIGHT:
                return STLblAlgn.R;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static AxisLabelAlign toLabelAlign(CTLblAlgn ctLblAlgn) {
        switch(ctLblAlgn.getVal().intValue()) {
            case 1:
                return AxisLabelAlign.CENTER;
            case 2:
                return AxisLabelAlign.LEFT;
            case 3:
                return AxisLabelAlign.RIGHT;
            default:
                throw new IllegalArgumentException();
        }
    }
}
