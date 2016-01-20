package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.user.Permission;
import com.azquo.admin.user.User;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by edward on 19/01/16.
 *
 */
@Controller
@RequestMapping("/CreateExcelForDownload")
public class CreateExcelForDownloadController {

    @Autowired
    private AdminService adminService;

    @Autowired
    ServletContext servletContext;

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String USERSPERMISSIONSFILENAME = "UsersPermissions.xlsx";

    @RequestMapping
    public void handleRequest(final HttpServletRequest request, HttpServletResponse response) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);

        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            response.sendRedirect("/api/Login");
        } else {
            // really necessary? Maybe check
            request.setCharacterEncoding("UTF-8");
//        resp.setCharacterEncoding("UTF-8");
//        resp.setContentType("application/json");
            Book book = Importers.getImporter().imports(servletContext.getResourceAsStream("/WEB-INF/" + USERSPERMISSIONSFILENAME), "Report name");
            // modify book to add the users and permissions
            Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
            if (userSheet != null){
                final List<User> userListForBusiness = adminService.getUserListForBusiness(loggedInUser);
                int row = 1;
                for (User user : userListForBusiness){
                    userSheet.getInternalSheet().getCell(row,0).setStringValue(user.getName());
                    userSheet.getInternalSheet().getCell(row,1).setStringValue(user.getEmail());
                    userSheet.getInternalSheet().getCell(row,2).setStringValue(dateTimeFormatter.format(user.getStartDate()));
                    userSheet.getInternalSheet().getCell(row,3).setStringValue(dateTimeFormatter.format(user.getEndDate()));
                    final Business businessById = adminService.getBusinessById(user.getBusinessId());
                    userSheet.getInternalSheet().getCell(row,4).setStringValue(businessById != null ? businessById.getBusinessName() : "");
                    userSheet.getInternalSheet().getCell(row,5).setStringValue(user.getStatus());
                    row++;
                }
            }
            Sheet permissionsSheet = book.getSheet("Permissions"); // literals not best practice, could it be factored between this and the xlsx file?
            if (permissionsSheet != null){
                final List<Permission.PermissionForDisplay> displayPermissionList = adminService.getDisplayPermissionList(loggedInUser);
                int row = 1;
                for (Permission.PermissionForDisplay permission : displayPermissionList){
                    permissionsSheet.getInternalSheet().getCell(row,0).setStringValue(permission.getDatabaseName());
                    permissionsSheet.getInternalSheet().getCell(row,1).setStringValue(permission.getUserEmail());
                    permissionsSheet.getInternalSheet().getCell(row,2).setStringValue(dateTimeFormatter.format(permission.getStartDate()));
                    permissionsSheet.getInternalSheet().getCell(row,3).setStringValue(dateTimeFormatter.format(permission.getEndDate()));
                    permissionsSheet.getInternalSheet().getCell(row,4).setStringValue(permission.getReadList());
                    permissionsSheet.getInternalSheet().getCell(row,5).setStringValue(permission.getWriteList());
                    row++;
                }
            }
            response.setContentType("application/vnd.ms-excel"); // Set up mime type
            response.addHeader("Content-Disposition", "attachment; filename=" + USERSPERMISSIONSFILENAME);
            OutputStream out = response.getOutputStream();
            Exporter exporter = Exporters.getExporter();
            exporter.export(book, out);
        }
    }
}
