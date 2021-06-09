package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.UserActivity;
import com.azquo.admin.onlinereport.UserActivityDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.CreateExcelForDownloadController;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportRenderer;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.poi.openxml4j.opc.OPCPackage;
import org.zkoss.poi.ss.usermodel.Name;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.util.AreaReference;
import org.zkoss.poi.xssf.usermodel.XSSFRow;
import org.zkoss.poi.xssf.usermodel.XSSFSheet;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * User CRUD.
 */
@Controller
@RequestMapping("/AzquoUsersReport")
public class AzquoUsersReportController {

    //    private static final Logger logger = Logger.getLogger(ManageUsersController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "download", required = false) String download
            , HttpServletResponse response
    ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // hack initially
        // added for Ed Broking but could be used by others. As mentioned this should only be used on a private network
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()

                    || !loggedInUser.getUser().getEmail().endsWith("@azquo.com")
        ) {
            return "redirect:/api/Login";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            // so we need to show data across all businesses. I guess  keep it to the last month?
            Map<String, List<Map<String, String>>> dataForHTML = new HashMap<>();

            List<Business> all = BusinessDAO.findAll();
            for (Business business : all) {
                List<Map<String, String>> linesForBusiness = new ArrayList<>();
                // this is ordered by timestamp
                List<UserActivity> forBusinessIdSince = UserActivityDAO.findForBusinessIdSince(business.getId(), LocalDateTime.now().plusMonths(-1));
                Map<String, List<UserActivity>> activityPerUser = new HashMap<>();
                for (UserActivity userActivity : forBusinessIdSince) {
                    activityPerUser.computeIfAbsent(userActivity.getUser(), t -> new ArrayList<>()).add(userActivity);
                }
                // broken down per business then per user
                // number of sessions, average session time, reports accessed, last session date, errors, uploads/downloads
                for (String user : activityPerUser.keySet()) {
                    Map<String, String> lineForBusiness = new HashMap<>();
                    List<UserActivity> forUser = activityPerUser.get(user);
                    int logins = 0;
                    LocalDateTime lastLogin = null;
                    LocalDateTime lastSessionDate = null;
                    List<Long> sessionTimesInSeconds = new ArrayList<>();
                    Set<String> reports = new HashSet<>();
                    for (UserActivity userActivity : forUser) {
                        if (userActivity.getActivity().equalsIgnoreCase("login")) {
                            lastLogin = userActivity.getTimeStamp();
                            lastSessionDate = userActivity.getTimeStamp();
                            logins++;
                        }
                        if (userActivity.getActivity().toLowerCase().startsWith("logout")) {
                            if (lastLogin != null) {
                                Duration duration = Duration.between(lastLogin, userActivity.getTimeStamp());
                                sessionTimesInSeconds.add(duration.getSeconds());
                            }
                        }
                        if (userActivity.getParameters().get("Report") != null) {
                            reports.add(userActivity.getParameters().get("Report"));
                        }
                        // now, errors and upload/downloads??
                    }
                    long totalSessionTime = 0;
                    for (Long l : sessionTimesInSeconds) {
                        totalSessionTime += l;
                    }
                    lineForBusiness.put("user", user);
                    lineForBusiness.put("numberofsessions", logins + "");
                    lineForBusiness.put("averagesessiontime", (totalSessionTime / sessionTimesInSeconds.size()) + "");
                    StringBuilder reportsAccessed = new StringBuilder();
                    for (String report : reports) {
                        if (reportsAccessed.length() > 0) {
                            reportsAccessed.append(", ");
                        }
                        reportsAccessed.append(report);
                    }
                    lineForBusiness.put("reportsaccessed", reportsAccessed.toString());
                    lineForBusiness.put("lastaccesseddate", lastSessionDate + "");
                    linesForBusiness.add(lineForBusiness);
                }
                dataForHTML.put(business.getBusinessName(), linesForBusiness);

            }
            if (download != null) {
                XSSFWorkbook wb = new XSSFWorkbook();
                XSSFSheet user_activity = wb.createSheet("Azquo User Activity");
                int rownum = -1;
                for (String b : dataForHTML.keySet()) {
                    rownum++;
                    XSSFRow brow = user_activity.createRow(rownum);
                    brow.createCell(0).setCellValue(b);
                    rownum++;
                    XSSFRow toprow = user_activity.createRow(rownum);
                    toprow.createCell(0).setCellValue("User");
                    toprow.createCell(1).setCellValue("Number of Sessions");
                    toprow.createCell(2).setCellValue("Average Session");
                    toprow.createCell(3).setCellValue("Reports Accessed");
                    toprow.createCell(4).setCellValue("Last Accessed Date");
                    toprow.createCell(5).setCellValue("Errors");
                    toprow.createCell(6).setCellValue("Uploads/Downloads");
                    for (Map<String, String> dataLine : dataForHTML.get(b)) {
                        rownum++;
                        XSSFRow row = user_activity.createRow(rownum);
                        row.createCell(0).setCellValue(dataLine.get("user"));
                        row.createCell(1).setCellValue(dataLine.get("numberofsessions"));
                        row.createCell(2).setCellValue(dataLine.get("averagesessiontime"));
                        row.createCell(3).setCellValue(dataLine.get("reportsaccessed"));
                        row.createCell(4).setCellValue(dataLine.get("lastaccesseddate"));
//                            toprow.createCell(5).setCellValue("Errors");
//                            toprow.createCell(6).setCellValue("Uploads/Downloads");

                    }

                }

                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                response.addHeader("Content-Disposition", "attachment; filename=useractivity.xlsx");
                OutputStream out = response.getOutputStream();
                wb.write(out);
                out.close();
            } else {
                model.put("businesses", dataForHTML);
            }

        }
        return "azquousersreport";
    }
}
