package com.azquo.spreadsheet.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.zkoss.json.JSONObject;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.jsp.JsonUpdateBridge;
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

    @RequestMapping
    public void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws Exception {
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
                    if ("PDF".equals(action)) {

                        Exporter exporter = Exporters.getExporter("pdf");

                        Book book = ss.getBook();

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
