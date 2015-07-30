package com.azquo.spreadsheet.controller;

import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.AzquoBook;
import com.azquo.spreadsheet.view.ZKAzquoBookUtils;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.util.PDFMergerUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.zkoss.json.JSONObject;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.jsp.JsonUpdateBridge;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zul.Filedownload;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Writer;

/**
 * Created by cawley on 05/03/15
 * .
 */
@Controller
@RequestMapping("/ZKSpreadsheetCommand")
public class ZKSpreadsheetCommandController {

    @Autowired
    private SpreadsheetService spreadsheetService;

    @Autowired
    private UserChoiceDAO userChoiceDAO;

    @Autowired
    private OnlineReportDAO onlineReportDAO;

    @Autowired
    private UserRegionOptionsDAO userRegionOptionsDAO;

    public static final String CHOICES_MAP = "CHOICES_MAP";

    @RequestMapping
    public void handleRequest(final HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // really necessary? Maybe check
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        //parameter from ajax request, you have to pass it in AJAX request
        //necessary parameter to get ZK server side desktop
        final String desktopId = req.getParameter("desktopId");
        //necessary parameter to get ZK server side spreadsheet
        final String zssUuid = req.getParameter("zssUuid");
        final String action = req.getParameter("action");
        // use utility class to wrap zk in servlet request and
        // get access and response result

        // prepare a json result object, it can contain your ajax result and
        // also the necessary zk component update result
        final JSONObject result = new JSONObject();

        JsonUpdateBridge bridge = new JsonUpdateBridge(req.getServletContext(), req, resp,
                desktopId) {
            @Override
            protected void process(Desktop desktop) {
                Spreadsheet ss = (Spreadsheet) desktop.getComponentByUuidIfAny(zssUuid);
                try {
                    if ("XLS".equals(action)) {

                        Exporter exporter = Exporters.getExporter();
                        Book book = ss.getBook();
                        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(file);
                            exporter.export(book, fos);
                        } finally {
                            if (fos != null) {
                                fos.close();
                            }
                        }

                        Filedownload.save(new AMedia(book.getBookName() + ".xlsx", null, null, file, true));
                    }
                    boolean permuteTest = true;
                    if ("PDF".equals(action)) {
                        Exporter exporter = Exporters.getExporter("pdf");
                        Book book = ss.getBook();
                        if (!permuteTest){ // standard single
                            File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                            FileOutputStream fos = null;
                            try {
                                fos = new FileOutputStream(file);
                                exporter.export(book, file);
                            } finally {
                                if (fos != null) {
                                    fos.close();
                                }
                            }
                            Filedownload.save(new AMedia(book.getBookName() + ".pdf", "pdf", "application/pdf", file, true));
                        } else {
                            PDFMergerUtility merger = new PDFMergerUtility();
                            ZKAzquoBookUtils zkAzquoBookUtils = new ZKAzquoBookUtils(spreadsheetService, userChoiceDAO, userRegionOptionsDAO);
                            for (int i = 0; i < 4; i++) {
                                // similar to reloading in the ZKComposer
                                final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
                                for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
                                    newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                                }
                                zkAzquoBookUtils.populateBook(newBook);
                                File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                                FileOutputStream fos = null;
                                try {
                                    fos = new FileOutputStream(file);
                                    exporter.export(newBook, fos);
                                } finally {
                                    if (fos != null) {
                                        fos.close();
                                    }
                                }
                                merger.addSource(file);
                            }
                            File merged = File.createTempFile(Long.toString(System.currentTimeMillis()), "merged");
                            merger.setDestinationFileName(merged.getAbsolutePath());
                            merger.mergeDocuments();
                            Filedownload.save(new AMedia(book.getBookName() + "merged.pdf", "pdf", "application/pdf", merged, true));
                        }
                    }

                    if ("Save".equals(action)) {
                        LoggedInUser loggedInUser = (LoggedInUser) req.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                        OnlineReport onlineReport = onlineReportDAO.findById(loggedInUser.getReportId());
                        //spreadsheetService.setProvenance(loggedInUser.getDataAccessToken(),loggedInUser.getUser().getName(),"in spreadsheet",onlineReport.getReportName(), loggedInUser.getContext());
                        final Book book = ss.getBook();
                        for (SName name : book.getInternalBook().getNames()) {
                            if (name.getName().toLowerCase().startsWith(AzquoBook.azDataRegion)) { // I'm saving on all sheets, this should be fine with zk
                                String region = name.getName().substring(AzquoBook.azDataRegion.length());
                                    spreadsheetService.saveData(loggedInUser, region.toLowerCase(), onlineReport.getReportName());
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        /*
        * Generate ZK update result in given JSON object. An AJAX response
        * handler at client side, zssjsp, will 'eval' this result to update ZK
        * components.
        */
        bridge.process(result);
        Writer w = resp.getWriter();
        w.append(result.toJSONString());
    }

}
