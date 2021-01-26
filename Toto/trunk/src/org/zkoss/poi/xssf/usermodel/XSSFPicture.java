//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

// EFC note - I've not modified this from the decompiler and yet is solves a runtime linking problem . . .

package org.zkoss.poi.xssf.usermodel;

import java.awt.Dimension;
import java.io.IOException;
import org.openxmlformats.schemas.drawingml.x2006.main.CTBlipFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualPictureProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPoint2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPresetGeometry2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTransform2D;
import org.openxmlformats.schemas.drawingml.x2006.main.STShapeType;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTPicture;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTPictureNonVisual;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTPicture.Factory;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCol;
import org.zkoss.poi.openxml4j.opc.PackagePart;
import org.zkoss.poi.openxml4j.opc.PackageRelationship;
import org.zkoss.poi.ss.usermodel.ClientAnchor;
import org.zkoss.poi.ss.usermodel.Picture;
import org.zkoss.poi.ss.util.ImageUtils;
import org.zkoss.poi.util.Internal;
import org.zkoss.poi.util.POILogFactory;
import org.zkoss.poi.util.POILogger;

public final class XSSFPicture extends XSSFShape implements Picture {
    private static final POILogger logger = POILogFactory.getLogger(XSSFPicture.class);
    private static float DEFAULT_COLUMN_WIDTH = 9.140625F;
    private static CTPicture prototype = null;
    private CTPicture ctPicture;

    protected XSSFPicture(XSSFDrawing drawing, CTPicture ctPicture) {
        this.drawing = drawing;
        this.ctPicture = ctPicture;
    }

    protected static CTPicture prototype() {
        if (prototype == null) {
            CTPicture pic = Factory.newInstance();
            CTPictureNonVisual nvpr = pic.addNewNvPicPr();
            CTNonVisualDrawingProps nvProps = nvpr.addNewCNvPr();
            nvProps.setId(1L);
            nvProps.setName("Picture 1");
            nvProps.setDescr("Picture");
            CTNonVisualPictureProperties nvPicProps = nvpr.addNewCNvPicPr();
            nvPicProps.addNewPicLocks().setNoChangeAspect(true);
            CTBlipFillProperties blip = pic.addNewBlipFill();
            blip.addNewBlip().setEmbed("");
            blip.addNewStretch().addNewFillRect();
            CTShapeProperties sppr = pic.addNewSpPr();
            CTTransform2D t2d = sppr.addNewXfrm();
            CTPositiveSize2D ext = t2d.addNewExt();
            ext.setCx(0L);
            ext.setCy(0L);
            CTPoint2D off = t2d.addNewOff();
            off.setX(0L);
            off.setY(0L);
            CTPresetGeometry2D prstGeom = sppr.addNewPrstGeom();
            prstGeom.setPrst(STShapeType.RECT);
            prstGeom.addNewAvLst();
            prototype = pic;
        }

        return prototype;
    }

    protected void setPictureReference(PackageRelationship rel) {
        this.ctPicture.getBlipFill().getBlip().setEmbed(rel.getId());
    }

    @Internal
    public CTPicture getCTPicture() {
        return this.ctPicture;
    }

    public void resize() {
        this.resize(1.0D);
    }

    public void resize(double scale) {
        XSSFClientAnchor anchor = (XSSFClientAnchor)this.getAnchor();
        XSSFClientAnchor pref = this.getPreferredSize(scale);
        int row2 = anchor.getRow1() + (pref.getRow2() - pref.getRow1());
        int col2 = anchor.getCol1() + (pref.getCol2() - pref.getCol1());
        anchor.setCol2(col2);
        anchor.setDx1(0);
        anchor.setDx2(pref.getDx2());
        anchor.setRow2(row2);
        anchor.setDy1(0);
        anchor.setDy2(pref.getDy2());
    }

    public XSSFClientAnchor getPreferredSize() {
        return this.getPreferredSize(1.0D);
    }

    public XSSFClientAnchor getPreferredSize(double scale) {
        XSSFClientAnchor anchor = (XSSFClientAnchor)this.getAnchor();
        XSSFPictureData data = this.getPictureData();
        Dimension size = getImageDimension(data.getPackagePart(), data.getPictureType());
        double scaledWidth = size.getWidth() * scale;
        double scaledHeight = size.getHeight() * scale;
        double scaledWidth0 = scaledWidth + (double)Math.round((double)anchor.getDx1() / 9525.0D);
        float w = 0.0F;
        int col2 = anchor.getCol1();
        int dx2 = 0;

        while(true) {
            w += (float)Math.round(this.getColumnWidthInPixels(col2));
            if ((double)w > scaledWidth0) {
                double scaledHeight0;
                double h;
                if ((double)w > scaledWidth0) {
                    scaledHeight0 = (double)Math.round(this.getColumnWidthInPixels(col2));
                    h = (double)w - scaledWidth0;
                    dx2 = (int)Math.round(9525.0D * (scaledHeight0 - h));
                }

                anchor.setCol2(col2);
                anchor.setDx2(dx2);
                scaledHeight0 = scaledHeight + (double)Math.round((double)anchor.getDy1() / 9525.0D);
                h = 0.0D;
                int row2 = anchor.getRow1();
                int dy2 = 0;

                while(true) {
                    h += (double)Math.round(this.getRowHeightInPixels(row2));
                    if (h > scaledHeight0) {
                        if (h > scaledHeight0) {
                            double ch = (double)Math.round(this.getRowHeightInPixels(row2));
                            double delta = h - scaledHeight0;
                            dy2 = (int)Math.round(9525.0D * (ch - delta));
                        }

                        anchor.setRow2(row2);
                        anchor.setDy2(dy2);
                        CTPositiveSize2D size2d = this.ctPicture.getSpPr().getXfrm().getExt();
                        size2d.setCx(Math.round(scaledWidth * 9525.0D));
                        size2d.setCy(Math.round(scaledHeight * 9525.0D));
                        return anchor;
                    }

                    ++row2;
                }
            }

            ++col2;
        }
    }

    private float getColumnWidthInPixels(int columnIndex) {
        XSSFSheet sheet = (XSSFSheet)this.getDrawing().getParent();
        CTCol col = sheet.getColumnHelper().getColumn((long)columnIndex, false);
        double numChars = col != null && col.isSetWidth() ? col.getWidth() : (double)DEFAULT_COLUMN_WIDTH;
        return (float)numChars * 7.0017F;
    }

    private float getRowHeightInPixels(int rowIndex) {
        XSSFSheet sheet = (XSSFSheet)this.getDrawing().getParent();
        XSSFRow row = sheet.getRow(rowIndex);
        float height = row != null ? row.getHeightInPoints() : sheet.getDefaultRowHeightInPoints();
        return height * 96.0F / 72.0F;
    }

    protected static Dimension getImageDimension(PackagePart part, int type) {
        try {
            return ImageUtils.getImageDimension(part.getInputStream(), type);
        } catch (IOException var3) {
            logger.log(5, var3);
            return new Dimension();
        }
    }

    public XSSFPictureData getPictureData() {
        String blipId = this.ctPicture.getBlipFill().getBlip().getEmbed();
        return (XSSFPictureData)this.getDrawing().getRelationById(blipId);
    }

    protected CTShapeProperties getShapeProperties() {
        return this.ctPicture.getSpPr();
    }

    public String getName() {
        return this.ctPicture.getNvPicPr().getCNvPr().getName();
    }

    public String getAlt() {
        return this.ctPicture.getNvPicPr().getCNvPr().getDescr();
    }

    public ClientAnchor getClientAnchor() {
        return (ClientAnchor)this.getAnchor();
    }

    public String getPictureId() {
        return this.ctPicture.getBlipFill().getBlip().getEmbed() + "_" + this.ctPicture.getNvPicPr().getCNvPr().getId();
    }

    public void setClientAnchor(ClientAnchor newanchor) {
        XSSFClientAnchor anchor = (XSSFClientAnchor)this.getAnchor();
        anchor.setCol1(newanchor.getCol1());
        anchor.setCol2(newanchor.getCol2());
        anchor.setDx1(newanchor.getDx1());
        anchor.setDx2(newanchor.getDx2());
        anchor.setDy1(newanchor.getDy1());
        anchor.setDy2(newanchor.getDy2());
        anchor.setRow1(newanchor.getRow1());
        anchor.setRow2(newanchor.getRow2());
    }
}
