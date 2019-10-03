package com.azquo.admin.controller;

import com.azquo.dataimport.UploadRecord;
import com.azquo.dataimport.UploadRecordDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by edward on 25/10/18.
 * <p>
 * For pending uploads
 */
@Controller
@RequestMapping("/UploadRecordComment")
public class UploadRecordCommentController {

    @RequestMapping
    public String handleRequest(ModelMap modelMap, HttpServletRequest request
            , @RequestParam(value = "urid", required = false) String urid
            , @RequestParam(value = "comment", required = false) String comment
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null) {
            if (urid != null && urid.length() > 0) {
                UploadRecord ur = UploadRecordDAO.findById(Integer.parseInt(urid));
                // not currently accommodating developers
                if ((loggedInUser.getDatabase() != null && loggedInUser.getDatabase().getId() == ur.getDatabaseId() && ur.getFileType() != null && ur.getFileType().length() > 0) || (loggedInUser.getUser().isAdministrator() && ur.getBusinessId() == loggedInUser.getUser().getBusinessId())) { // ok we're allowed to see it
                    if (comment != null){ // saving
                        //System.out.println("comment" + comment);
                        ur.setUserComment(comment);
                        UploadRecordDAO.store(ur);
                        if (comment.length() > 0){
                            modelMap.addAttribute("script", "            window.parent.document.getElementById('comment" + ur.getId() + "').text = 'See Comment';\n            window.parent.$['inspectOverlay']().close();\n");
                        } else {
                            modelMap.addAttribute("script", "            window.parent.$['inspectOverlay']().close();\n");
                        }
                    }
                    modelMap.addAttribute("urid", urid);
                    modelMap.addAttribute("comment", ur.getUserComment() != null ? ur.getUserComment() : "");
                    return "uploadrecordcomment";
                }
            }
        }
        return "redirect:/api/Login";
    }
}