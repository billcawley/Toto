package com.azquo.controller;

import com.azquo.spreadsheet.OnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/**
 * Created by bill on 15/10/14
 * <p/>
 * Seems to be for delivering non-chart images.
 */


@Controller
@RequestMapping("/Image")


public class ImageController {

    @Autowired
    private OnlineService onlineService;

    @RequestMapping
    public String handleRequest(HttpServletResponse response
            , @RequestParam(value = "image", required = false)  String image
            , @RequestParam(value = "supplierdb", required = false)  String db
    ) throws Exception {

        if (image != null) {
            if (image.length() > 0 && image.indexOf(".") > 0) {
                //may need some security check...
                String path = onlineService.getHomeDir() + "/databases/" + db + "/" +  image;
                File file = new File(path);
                String ext = image.substring(image.indexOf(".") + 1).toLowerCase();
                if (ext.equals("jpg")) {
                    ext = "jpeg";
                }
                response.setContentType("image/" + ext);
                try {
                    response.setContentLength((int) file.length());
                    // Open the file and output streams
                    FileInputStream in = new FileInputStream(path);
                    OutputStream os = response.getOutputStream();
                    // Copy the contents of the file to the output stream
                    byte[] buf = new byte[2048];
                    int count;
                    while ((count = in.read(buf)) >= 0) {
                        os.write(buf, 0, count);
                    }
                    in.close();
                    os.close();
                } catch (Exception e) {
                    System.out.println("Exception delivering image  : " + e.getMessage() + ", " + path);
                }
            }
        }
        return "";
    }
}







