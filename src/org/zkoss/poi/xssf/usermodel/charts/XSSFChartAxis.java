//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
// EFC note - as with a few others I've not modified this from the decompiler and yet is solves a runtime linking problem . . .

package org.zkoss.poi.xssf.usermodel.charts;

import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTChartLines;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTCrosses;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLogBase;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumFmt;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTOrientation;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTScaling;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTTickLblPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.STAxPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.STCrosses;
import org.openxmlformats.schemas.drawingml.x2006.chart.STOrientation;
import org.openxmlformats.schemas.drawingml.x2006.chart.STTickLblPos;
import org.openxmlformats.schemas.drawingml.x2006.chart.STOrientation.Enum;
import org.openxmlformats.schemas.drawingml.x2006.main.CTLineProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.STCompoundLine;
import org.openxmlformats.schemas.drawingml.x2006.main.STLineCap;
import org.openxmlformats.schemas.drawingml.x2006.main.STPenAlignment;
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal;
import org.zkoss.poi.ss.usermodel.charts.AxisCrosses;
import org.zkoss.poi.ss.usermodel.charts.AxisOrientation;
import org.zkoss.poi.ss.usermodel.charts.AxisPosition;
import org.zkoss.poi.ss.usermodel.charts.AxisTickLabelPosition;
import org.zkoss.poi.ss.usermodel.charts.ChartAxis;
import org.zkoss.poi.xssf.usermodel.XSSFChart;

public abstract class XSSFChartAxis implements ChartAxis {
    protected XSSFChart chart;
    private static final double MIN_LOG_BASE = 2.0D;
    private static final double MAX_LOG_BASE = 1000.0D;

    protected XSSFChartAxis(XSSFChart chart) {
        this.chart = chart;
    }

    public AxisPosition getPosition() {
        return toAxisPosition(this.getCTAxPos());
    }

    public void setPosition(AxisPosition position) {
        this.getCTAxPos().setVal(fromAxisPosition(position));
    }

    public void setNumberFormat(String format) {
        this.getCTNumFmt().setFormatCode(format);
        this.getCTNumFmt().setSourceLinked(false);
    }

    public String getNumberFormat() {
        return this.getCTNumFmt().getFormatCode();
    }

    public boolean isSetLogBase() {
        return this.getCTScaling().isSetLogBase();
    }

    public void setLogBase(double logBase) {
        if (logBase >= 2.0D && 1000.0D >= logBase) {
            CTScaling scaling = this.getCTScaling();
            if (scaling.isSetLogBase()) {
                scaling.getLogBase().setVal(logBase);
            } else {
                scaling.addNewLogBase().setVal(logBase);
            }

        } else {
            throw new IllegalArgumentException("Axis log base must be between 2 and 1000 (inclusive), got: " + logBase);
        }
    }

    public double getLogBase() {
        CTLogBase logBase = this.getCTScaling().getLogBase();
        return logBase != null ? logBase.getVal() : 0.0D;
    }

    public boolean isSetMinimum() {
        return this.getCTScaling().isSetMin();
    }

    public void setMinimum(double min) {
        CTScaling scaling = this.getCTScaling();
        if (scaling.isSetMin()) {
            scaling.getMin().setVal(min);
        } else {
            scaling.addNewMin().setVal(min);
        }

    }

    public double getMinimum() {
        CTScaling scaling = this.getCTScaling();
        return scaling.isSetMin() ? scaling.getMin().getVal() : 0.0D;
    }

    public boolean isSetMaximum() {
        return this.getCTScaling().isSetMax();
    }

    public void setMaximum(double max) {
        CTScaling scaling = this.getCTScaling();
        if (scaling.isSetMax()) {
            scaling.getMax().setVal(max);
        } else {
            scaling.addNewMax().setVal(max);
        }

    }

    public double getMaximum() {
        CTScaling scaling = this.getCTScaling();
        return scaling.isSetMax() ? scaling.getMax().getVal() : 0.0D;
    }

    public AxisOrientation getOrientation() {
        return toAxisOrientation(this.getCTScaling().getOrientation());
    }

    public void setOrientation(AxisOrientation orientation) {
        CTScaling scaling = this.getCTScaling();
        Enum stOrientation = fromAxisOrientation(orientation);
        if (scaling.isSetOrientation()) {
            scaling.getOrientation().setVal(stOrientation);
        } else {
            this.getCTScaling().addNewOrientation().setVal(stOrientation);
        }

    }

    public AxisCrosses getCrosses() {
        return toAxisCrosses(this.getCTCrosses());
    }

    public void setCrosses(AxisCrosses crosses) {
        this.getCTCrosses().setVal(fromAxisCrosses(crosses));
    }

    protected abstract CTAxPos getCTAxPos();

    protected abstract CTNumFmt getCTNumFmt();

    protected abstract CTScaling getCTScaling();

    protected abstract CTCrosses getCTCrosses();

    protected void fillMajorGridline(CTChartLines ctChartLines) {
        CTShapeProperties spPr = ctChartLines.addNewSpPr();
        CTLineProperties lnPr = spPr.addNewLn();
        lnPr.setAlgn(STPenAlignment.CTR);
        lnPr.setCap(STLineCap.FLAT);
        lnPr.setCmpd(STCompoundLine.SNG);
        lnPr.setW(9625);
        CTSolidColorFillProperties sfPr = lnPr.addNewSolidFill();
        CTSchemeColor lineColor = sfPr.addNewSchemeClr();
        lineColor.setVal(STSchemeColorVal.TX_1);
        lineColor.addNewLumMod().setVal(15000);
        lineColor.addNewLumOff().setVal(85000);
    }

    private static Enum fromAxisOrientation(AxisOrientation orientation) {
        switch(orientation) {
            case MIN_MAX:
                return STOrientation.MIN_MAX;
            case MAX_MIN:
                return STOrientation.MAX_MIN;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static AxisOrientation toAxisOrientation(CTOrientation ctOrientation) {
        switch(ctOrientation.getVal().intValue()) {
            case 1:
                return AxisOrientation.MAX_MIN;
            case 2:
                return AxisOrientation.MIN_MAX;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static org.openxmlformats.schemas.drawingml.x2006.chart.STCrosses.Enum fromAxisCrosses(AxisCrosses crosses) {
        switch(crosses) {
            case AUTO_ZERO:
                return STCrosses.AUTO_ZERO;
            case MIN:
                return STCrosses.MIN;
            case MAX:
                return STCrosses.MAX;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static AxisCrosses toAxisCrosses(CTCrosses ctCrosses) {
        switch(ctCrosses.getVal().intValue()) {
            case 1:
                return AxisCrosses.AUTO_ZERO;
            case 2:
                return AxisCrosses.MAX;
            case 3:
                return AxisCrosses.MIN;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static org.openxmlformats.schemas.drawingml.x2006.chart.STAxPos.Enum fromAxisPosition(AxisPosition position) {
        switch(position) {
            case BOTTOM:
                return STAxPos.B;
            case LEFT:
                return STAxPos.L;
            case RIGHT:
                return STAxPos.R;
            case TOP:
                return STAxPos.T;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static AxisPosition toAxisPosition(CTAxPos ctAxPos) {
        switch(ctAxPos.getVal().intValue()) {
            case 1:
                return AxisPosition.BOTTOM;
            case 2:
                return AxisPosition.LEFT;
            case 3:
                return AxisPosition.RIGHT;
            case 4:
                return AxisPosition.TOP;
            default:
                return AxisPosition.BOTTOM;
        }
    }

    protected abstract CTTickLblPos getTickLblPos();

    public AxisTickLabelPosition getTickLabelPosition() {
        return toTickLabelPosition(this.getTickLblPos().getVal());
    }

    public void setTickLabelPosition(AxisTickLabelPosition tickLblPos) {
        this.getTickLblPos().setVal(fromTickLabelPosition(tickLblPos));
    }

    private static org.openxmlformats.schemas.drawingml.x2006.chart.STTickLblPos.Enum fromTickLabelPosition(AxisTickLabelPosition pos) {
        switch(pos) {
            case HIGH:
                return STTickLblPos.HIGH;
            case LOW:
                return STTickLblPos.LOW;
            case NEXT_TO:
                return STTickLblPos.NEXT_TO;
            case NONE:
                return STTickLblPos.NONE;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static AxisTickLabelPosition toTickLabelPosition(org.openxmlformats.schemas.drawingml.x2006.chart.STTickLblPos.Enum pos) {
        switch(pos.intValue()) {
            case 1:
                return AxisTickLabelPosition.HIGH;
            case 2:
                return AxisTickLabelPosition.LOW;
            case 3:
                return AxisTickLabelPosition.NEXT_TO;
            case 4:
                return AxisTickLabelPosition.NONE;
            default:
                throw new IllegalArgumentException();
        }
    }
}
