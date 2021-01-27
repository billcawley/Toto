//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
// EFC note - as with a few others I've not modified this from the decompiler and yet is solves a runtime linking problem . . .

package org.zkoss.poi.xssf.usermodel;

import javax.xml.namespace.QName;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGraphicalObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGraphicalObjectData;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPoint2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTransform2D;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTGraphicalObjectFrame;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTGraphicalObjectFrameNonVisual;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTGraphicalObjectFrame.Factory;
import org.openxmlformats.schemas.officeDocument.x2006.relationships.STRelationshipId;
import org.zkoss.poi.util.Internal;

public final class XSSFGraphicFrame extends XSSFShape {
    private static CTGraphicalObjectFrame prototype = null;
    private CTGraphicalObjectFrame graphicFrame;
    private XSSFDrawing drawing;
    private XSSFClientAnchor anchor;

    protected XSSFGraphicFrame(XSSFDrawing drawing, CTGraphicalObjectFrame ctGraphicFrame) {
        this.drawing = drawing;
        this.graphicFrame = ctGraphicFrame;
    }

    @Internal
    public CTGraphicalObjectFrame getCTGraphicalObjectFrame() {
        return this.graphicFrame;
    }

    protected static CTGraphicalObjectFrame prototype() {
        if (prototype == null) {
            CTGraphicalObjectFrame graphicFrame = Factory.newInstance();
            CTGraphicalObjectFrameNonVisual nvGraphic = graphicFrame.addNewNvGraphicFramePr();
            CTNonVisualDrawingProps props = nvGraphic.addNewCNvPr();
            props.setId(0L);
            props.setName("Diagramm 1");
            nvGraphic.addNewCNvGraphicFramePr();
            CTTransform2D transform = graphicFrame.addNewXfrm();
            CTPositiveSize2D extPoint = transform.addNewExt();
            CTPoint2D offPoint = transform.addNewOff();
            extPoint.setCx(0L);
            extPoint.setCy(0L);
            offPoint.setX(0L);
            offPoint.setY(0L);
            CTGraphicalObject graphic = graphicFrame.addNewGraphic();
            prototype = graphicFrame;
        }

        return prototype;
    }

    public void setMacro(String macro) {
        this.graphicFrame.setMacro(macro);
    }

    public void setName(String name) {
        this.getNonVisualProperties().setName(name);
    }

    public String getName() {
        return this.getNonVisualProperties().getName();
    }

    private CTNonVisualDrawingProps getNonVisualProperties() {
        CTGraphicalObjectFrameNonVisual nvGraphic = this.graphicFrame.getNvGraphicFramePr();
        return nvGraphic.getCNvPr();
    }

    protected void setAnchor(XSSFClientAnchor anchor) {
        this.anchor = anchor;
    }

    public XSSFClientAnchor getAnchor() {
        return this.anchor;
    }

    protected void setChart(XSSFChart chart, String relId) {
        CTGraphicalObjectData data = this.graphicFrame.getGraphic().addNewGraphicData();
        this.appendChartElement(data, relId);
        chart.setGraphicFrame(this);
    }

    public long getId() {
        return this.graphicFrame.getNvGraphicFramePr().getCNvPr().getId();
    }

    protected void setId(long id) {
        this.graphicFrame.getNvGraphicFramePr().getCNvPr().setId(id);
    }

    private void appendChartElement(CTGraphicalObjectData data, String id) {
        String r_namespaceUri = STRelationshipId.type.getName().getNamespaceURI();
        String c_namespaceUri = "http://schemas.openxmlformats.org/drawingml/2006/chart";
        XmlCursor cursor = data.newCursor();
        cursor.toNextToken();
        cursor.beginElement(new QName(c_namespaceUri, "chart", "c"));
        cursor.insertAttributeWithValue(new QName(r_namespaceUri, "id", "r"), id);
        cursor.dispose();
        data.setUri(c_namespaceUri);
    }

    protected CTShapeProperties getShapeProperties() {
        return null;
    }
}
