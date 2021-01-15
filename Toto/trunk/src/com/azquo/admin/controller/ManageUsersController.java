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
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@RequestMapping("/ManageUsers")
public class ManageUsersController {

//    private static final Logger logger = Logger.getLogger(ManageUsersController.class);
    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "apiKey", required = false) String apiKey
            , @RequestParam(value = "apiAction", required = false) String apiAction
            , @RequestParam(value = "business", required = false) String business
            , @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "recentId", required = false) String recentId
            , @RequestParam(value = "downloadRecentId", required = false) String downloadRecentId
            , @RequestParam(value = "endDate", required = false) String endDate
            , @RequestParam(value = "email", required = false) String email
            , @RequestParam(value = "name", required = false) String name
            , @RequestParam(value = "status", required = false) String status
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "team", required = false) String team
            , @RequestParam(value = "databaseId", required = false) String databaseId
            , @RequestParam(value = "reportId", required = false) String reportId
            , @RequestParam(value = "selections", required = false) String selections
            , @RequestParam(value = "submit", required = false) String submit
            , @RequestParam(value = "downloadRecentActivity", required = false) String downloadRecentActivity
    ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // added for Ed Broking but could be used by others. As mentioned this should only be used on a private network
        if (apiKey != null && apiKey.equals(SpreadsheetService.getManageusersapikey())){
            // api access will give basic crud against users. There may be some duplication with the edit bit below, deal with that after if it's an issue
            if (business == null || business.isEmpty()){
                model.put("content", "business required");
                return "utf8page";
            }
            Business b = BusinessDAO.findByName(business);
            if (b == null){
                model.put("content", "business " + business + " not found");
                return "utf8page";
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            switch (apiAction){
                case "delete" :
                    if (AdminService.deleteUserByLogin(email, b)){
                        model.put("content", "deleted " + email);
                        return "utf8page";
                    } else {
                        model.put("content", "could not find " + email);
                        return "utf8page";
                    }
                case "set" :
                    User toEdit = UserDAO.findByEmailAndBusinessId(email, loggedInUser.getUser().getBusinessId()); // may or may not be null
                    // ok check to see if data was submitted
                    StringBuilder error = new StringBuilder();
                        if (endDate == null || endDate.isEmpty()) {
                            error.append("End date required (yyyy-MM-dd)\n");
                        } else {
                            try {
                                formatter.parse(endDate);
                            } catch (DateTimeParseException e) {
                                error.append("End date format not yyyy-MM-dd\n");
                            }
                        }
                        if (email == null || email.isEmpty()) {
                            error.append("Email required\n");
                        } else {
                            email = email.trim();
                        }
                        if (name == null || name.isEmpty()) {
                            error.append("Name required\n");
                        }
                        if (password == null || password.isEmpty()) {
                            error.append("Password required\n");
                        }
                        if (error.length() == 0) {
                            assert endDate != null;
                            // then store, it might be new
                            if (toEdit == null) {
                                // Have to use  a LocalDate on the parse which is annoying http://stackoverflow.com/questions/27454025/unable-to-obtain-localdatetime-from-temporalaccessor-when-parsing-localdatetime
                                AdminService.createUser(email, name, LocalDate.parse(endDate, formatter).atStartOfDay(), status, password, loggedInUser
                                        , NumberUtils.isDigits(databaseId) ? Integer.parseInt(databaseId) : 0
                                        , NumberUtils.isDigits(reportId) ? Integer.parseInt(reportId) : 0, selections, team);
                                model.put("content", "user " + email + " created");
                                return "utf8page";
                            } else {
                                toEdit.setEndDate(LocalDate.parse(endDate, formatter).atStartOfDay());
                                toEdit.setEmail(email);
                                toEdit.setName(name);
                                toEdit.setStatus(status);
                                toEdit.setTeam(team);
                                toEdit.setDatabaseId(NumberUtils.isDigits(databaseId) ? Integer.parseInt(databaseId) : 0);
                                toEdit.setReportId(NumberUtils.isDigits(reportId) ? Integer.parseInt(reportId) : 0);
                                    final String salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                                    toEdit.setSalt(salt);
                                    toEdit.setPassword(AdminService.encrypt(password, salt));
                                toEdit.setSelections(selections);
                                UserDAO.store(toEdit);
                                model.put("content", "user " + email + " updated");
                                return "utf8page";
                            }
                        } else {
                            model.put("content", "error :  " + error);
                            return "utf8page";
                        }
                default:
                    model.put("content", "unknown apiAction : " + apiAction);
                    return "utf8page";
            }
        }
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (NumberUtils.isDigits(deleteId)) {
                AdminService.deleteUserById(Integer.parseInt(deleteId), loggedInUser);
            }
            if (NumberUtils.isDigits(recentId)) {
                User u = AdminService.getUserById(Integer.parseInt(recentId), loggedInUser);
                List<UserActivity> userActivities = UserActivityDAO.findForUserAndBusinessId(loggedInUser.getUser().getBusinessId(), u.getEmail(), 0, 500);
                model.put("useractivities", userActivities);
                model.put("id", recentId);
                AdminService.setBanner(model, loggedInUser);
                return "recentuseractivity";
            }

            if (NumberUtils.isDigits(downloadRecentId)) {
                User u = AdminService.getUserById(Integer.parseInt(downloadRecentId), loggedInUser);
                List<UserActivity> userActivities = UserActivityDAO.findForUserAndBusinessId(loggedInUser.getUser().getBusinessId(), u.getEmail(), 0, 500);
                XSSFWorkbook wb = new XSSFWorkbook();
                XSSFSheet user_activity = wb.createSheet("User Activity");
                int rownum = 0;
                XSSFRow toprow = user_activity.createRow(rownum);
                toprow.createCell(0).setCellValue("User Email");
                toprow.createCell(1).setCellValue("Time");
                toprow.createCell(2).setCellValue("Activity");
                toprow.createCell(3).setCellValue("Parameters");
                for (UserActivity userActivity : userActivities){
                    rownum++;
                    XSSFRow row = user_activity.createRow(rownum);
                    row.createCell(0).setCellValue(userActivity.getUser());
                    row.createCell(1).setCellValue(userActivity.getTimeStamp().toString());
                    row.createCell(2).setCellValue(userActivity.getActivity());
                    row.createCell(3).setCellValue(userActivity.getParametersForWorkbook());
                }
                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                response.addHeader("Content-Disposition", "attachment; filename=useractivity.xlsx");
                OutputStream out = response.getOutputStream();
                wb.write(out);
                out.close();
            }

            if ("true".equals(downloadRecentActivity)){
                AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser);
                Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                XSSFWorkbook wb = new XSSFWorkbook();
                XSSFSheet user_activity = wb.createSheet("User Activity");
                int rownum = 0;
                XSSFRow toprow = user_activity.createRow(rownum);
                DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                toprow.createCell(0).setCellValue(b.getBusinessName() +  " user stats week ending " + formatter2.format(LocalDateTime.now()));
                rownum++;
                user_activity.createRow(rownum);
                rownum++;
                XSSFRow row = user_activity.createRow(rownum);
                row.createCell(0).setCellValue("");
                row.createCell(1).setCellValue("User ID");
                row.createCell(2).setCellValue("Number of Logins");
                row.createCell(3).setCellValue("Most recent login");
                row.createCell(4).setCellValue("Total Duration");
                row.createCell(5).setCellValue("Files Uploaded");
                row.createCell(6).setCellValue("Files Downloaded");
                rownum++;
                user_activity.createRow(rownum);

                for (User user : AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser)){
                    rownum++;
                    List<UserActivity> userActivities = UserActivityDAO.findForUserAndBusinessIdSince(loggedInUser.getUser().getBusinessId(), user.getEmail(), LocalDateTime.now().plusWeeks(-1));
                    LocalDateTime loginTime = null;
                    long totalSeconds = 0;
                    int numberOfLogins = 0;
                    int filesUploaded = 0;
                    int filesDownloaded = 0;
                    LocalDateTime mostRecetLogin = null;
                    for (UserActivity activity : userActivities){
                        if (activity.getActivity().equalsIgnoreCase("Login")){
                            loginTime = activity.getTimeStamp();
                            numberOfLogins++;
                            if (mostRecetLogin == null || loginTime.isAfter(mostRecetLogin)){
                                mostRecetLogin = loginTime;
                            }
                        }
                        if (activity.getActivity().startsWith("Logout") && loginTime != null){
                            Duration d = Duration.between(loginTime, activity.getTimeStamp());
                            totalSeconds += d.getSeconds();
                            loginTime = null;
                        }
                        if (activity.getActivity().equalsIgnoreCase("upload file")){
                            filesUploaded++;
                        }
                        if (activity.getActivity().equalsIgnoreCase("save") && activity.getParameters().containsKey("File")){
                            filesDownloaded++;
                        }
                    }
                    row = user_activity.createRow(rownum);
                    row.createCell(0).setCellValue("");
                    row.createCell(1).setCellValue(user.getEmail());
                    row.createCell(2).setCellValue(numberOfLogins);
                    row.createCell(3).setCellValue(mostRecetLogin != null ? mostRecetLogin.toString() : "n/a");
                    row.createCell(4).setCellValue(String.format("%d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, (totalSeconds % 60)));
                    row.createCell(5).setCellValue(filesUploaded);
                    row.createCell(6).setCellValue(filesDownloaded);
                }
                user_activity.autoSizeColumn(1);
                user_activity.autoSizeColumn(2);
                user_activity.autoSizeColumn(3);
                user_activity.autoSizeColumn(4);
                user_activity.autoSizeColumn(5);
                user_activity.autoSizeColumn(6);
                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                response.addHeader("Content-Disposition", "attachment; filename=useractivitysummary.xlsx");
                OutputStream out = response.getOutputStream();
                wb.write(out);
                out.close();
            }

            if (NumberUtils.isDigits(editId)) {
                User toEdit = AdminService.getUserById(Integer.parseInt(editId), loggedInUser);
                // ok check to see if data was submitted
                StringBuilder error = new StringBuilder();
                if (submit != null) {
                    if (endDate == null || endDate.isEmpty()) {
                        error.append("End date required (yyyy-MM-dd)<br/>");
                    } else {
                        try {
                            formatter.parse(endDate);
                        } catch (DateTimeParseException e) {
                            error.append("End date format not yyyy-MM-dd<br/>");
                        }
                    }
                    if (email == null || email.isEmpty()) {
                        error.append("Email required<br/>");
                    } else {
                        email = email.trim();
                    }
                    if (toEdit == null && UserDAO.findByEmailAndBusinessId(email, loggedInUser.getUser().getBusinessId()) != null) {
                        error.append("User Exists<br/>");
                    }
                    if (name == null || name.isEmpty()) {
                        error.append("Name required<br/>");
                    }
                    if (toEdit == null && (password == null || password.isEmpty())) {
                        error.append("Password required<br/>");
                    }
                    if (error.length() == 0) {
                        assert endDate != null;
                        // then store, it might be new
                        if (toEdit == null) {
                            // Have to use  a LocalDate on the parse which is annoying http://stackoverflow.com/questions/27454025/unable-to-obtain-localdatetime-from-temporalaccessor-when-parsing-localdatetime
                            AdminService.createUser(email, name, LocalDate.parse(endDate, formatter).atStartOfDay(), status, password, loggedInUser
                                    , NumberUtils.isDigits(databaseId) ? Integer.parseInt(databaseId) : 0
                                    , NumberUtils.isDigits(reportId) ? Integer.parseInt(reportId) : 0, selections, team);
                        } else {
                            toEdit.setEndDate(LocalDate.parse(endDate, formatter).atStartOfDay());
                            toEdit.setEmail(email);
                            toEdit.setName(name);
                            toEdit.setStatus(status);
                            toEdit.setTeam(team);
                            toEdit.setDatabaseId(NumberUtils.isDigits(databaseId) ? Integer.parseInt(databaseId) : 0);
                            toEdit.setReportId(NumberUtils.isDigits(reportId) ? Integer.parseInt(reportId) : 0);
                            if (password != null && !password.isEmpty()) {
                                final String salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                                toEdit.setSalt(salt);
                                toEdit.setPassword(AdminService.encrypt(password, salt));
                            }
                            toEdit.setSelections(selections);
                            UserDAO.store(toEdit);
                        }
                        model.put("users", AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser));
                        AdminService.setBanner(model, loggedInUser);
                        return "manageusers";
                    } else {
                        model.put("error", error.toString());
                    }
                    model.put("id", editId);
                    model.put("endDate", endDate);
                    model.put("email", email);
                    model.put("name", name);
                    model.put("status", status);
                    model.put("team", team);
                } else {
                    if (toEdit != null) {
                        model.put("id", toEdit.getId());
                        model.put("endDate", formatter.format(toEdit.getEndDate()));
                        model.put("email", toEdit.getEmail());
                        model.put("name", toEdit.getName());
                        model.put("status", toEdit.getStatus());
                        model.put("user", toEdit);
                        model.put("selections", toEdit.getSelections());
                        model.put("team", toEdit.getTeam());
                    } else {
                        model.put("id", "0");
                    }
                }
                model.put("databases", AdminService.getDatabaseListForBusinessWithBasicSecurity(loggedInUser));
                model.put("reports", AdminService.getReportList(loggedInUser, true));
                AdminService.setBanner(model, loggedInUser);
                return "edituser";
            }
            final List<User> userListForBusiness = AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser);
            if (userListForBusiness != null) {
                userListForBusiness.sort(Comparator.comparing(User::getEmail));
            }
            model.put("users", userListForBusiness);
            if (userListForBusiness != null && userListForBusiness.size() > 1) {
                model.put("showDownload", true);
            }
            AdminService.setBanner(model, loggedInUser);
            return "manageusers";
        }
    }

    @RequestMapping(headers = "content-type=multipart/*")
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "uploadFile", required = false) MultipartFile uploadFile
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser != null && loggedInUser.getUser().isAdministrator()) {
            if (uploadFile != null) {
                try {
                    String fileName = uploadFile.getOriginalFilename();
                    File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // timestamp to stop file overwriting
                    uploadFile.transferTo(moved);


                    // this chunk moved from ImportService - perhaps it could be moved from here but


                    FileInputStream fs = new FileInputStream(moved);
                    OPCPackage opcPackage = OPCPackage.open(fs);
                    XSSFWorkbook book = new XSSFWorkbook(opcPackage);
                    List<String> notAllowed = new ArrayList<>();
                    List<String> rejected = new ArrayList<>();
                    Sheet userSheet = book.getSheet("Users"); // literals not best practice, could it be factored between this and the xlsx file?
                    if (userSheet != null) {
                        int row;
                        Name listRegion = BookUtils.getName(book,ReportRenderer.AZLISTSTART);
//                SName listRegion = book.getInternalBook().getNameByName(ReportRenderer.AZLISTSTART);
                        if (listRegion != null && listRegion.getRefersToFormula() != null) {
                            AreaReference aref = new AreaReference(listRegion.getRefersToFormula());
                            row = aref.getFirstCell().getRow();
                        } else {
                            if ("Email/logon".equalsIgnoreCase(userSheet.getRow(4).getCell(0).getStringCellValue())) {
                                row = 5;
                            } else {
                                throw new Exception("az_ListStart not found, typically it is A6");
                            }
                        }
                        // keep them to use if not set. Should I be updating records instead? I'm not sure.
                        Map<String, String> oldPasswordMap = new HashMap<>();
                        Map<String, String> oldSaltMap = new HashMap<>();
                        List<User> userList = UserDAO.findForBusinessId(loggedInUser.getUser().getBusinessId()); // don't use the admin call, it will just return for this user, we want all for the business so we can check not allowed
                        //todo - work out what users DEVELOPERs can upload
                        for (User user : userList) {
                            if (user.getId() != loggedInUser.getUser().getId()) { // leave the logged in user alone!
                                if (loggedInUser.getUser().getBusinessId() != user.getBusinessId()
                                        || (loggedInUser.getUser().getStatus().equals("MASTER") && !user.getCreatedBy().equals(loggedInUser.getUser().getEmail()))
                                        || user.getStatus().equals(User.STATUS_ADMINISTRATOR)) { // don't zap admins
                                    notAllowed.add(user.getEmail());
                                } else {
                                    oldPasswordMap.put(user.getEmail(), user.getPassword());
                                    oldSaltMap.put(user.getEmail(), user.getSalt());
                                    UserDAO.removeById(user);
                                }
                            }
                        }
                        while (userSheet.getRow(row).getCell(0).getStringCellValue() != null && userSheet.getRow(row).getCell(0).getStringCellValue().length() > 0) {
                            //Email	Name  Password	End Date	Status	Database	Report
                            String user = userSheet.getRow(row).getCell(1).getStringCellValue().trim();
                            String email = userSheet.getRow(row).getCell(0).getStringCellValue().trim();
                            if (notAllowed.contains(email)) rejected.add(email);
                            if (!loggedInUser.getUser().getEmail().equals(email) && !notAllowed.contains(email)) { // leave the logged in user alone!
                                String salt = "";
                                String password = userSheet.getRow(row).getCell(2).getStringCellValue();
                                String selections = userSheet.getRow(row).getCell(7).getStringCellValue();
                                if (password == null) {
                                    password = "";
                                }
                                LocalDate end = LocalDate.now().plusYears(10);
                                try {
                                    end = LocalDate.parse(userSheet.getRow(row).getCell(3).getStringCellValue(), CreateExcelForDownloadController.dateTimeFormatter);
                                } catch (Exception ignored) {
                                }
                                String status = userSheet.getRow(row).getCell(4).getStringCellValue();
                                if (!loggedInUser.getUser().isAdministrator()) {
                                    status = "USER"; // only admins can set status
                                }
                                // Probably could be factored somewhere
                                if (password.length() > 0) {
                                    salt = AdminService.shaHash(System.currentTimeMillis() + "salt");
                                    password = AdminService.encrypt(password, salt);
                                } else if (oldPasswordMap.get(email) != null) {
                                    password = oldPasswordMap.get(email);
                                    salt = oldSaltMap.get(email);
                                }
                                if (password.isEmpty()) {
                                    throw new Exception("Blank password for " + email);
                                }
                                Database d = DatabaseDAO.findForNameAndBusinessId(userSheet.getRow(row).getCell(5).getStringCellValue(), loggedInUser.getUser().getBusinessId());
                                OnlineReport or = OnlineReportDAO.findForNameAndBusinessId(userSheet.getRow(row).getCell(6).getStringCellValue(), loggedInUser.getUser().getBusinessId());
                                if (!status.equalsIgnoreCase(User.STATUS_ADMINISTRATOR) && !status.equalsIgnoreCase(User.STATUS_DEVELOPER) && or == null) {
                                    throw new Exception("Unable to find report " + userSheet.getRow(row).getCell(6).getStringCellValue());
                                }
                                String team = userSheet.getRow(row).getCell(7).getStringCellValue();

                                // todo - master and user types need to check for a report and error if it's not there
                                if (!loggedInUser.getUser().isAdministrator()) { // then I need to check against the session for allowable reports and databases
                                    boolean stored = false;
                                    if (d != null && or != null) {
                                        final Map<String, LoggedInUser.ReportIdDatabaseId> reportIdDatabaseIdPermissionsFromReport = loggedInUser.getReportIdDatabaseIdPermissions();
                                        for (LoggedInUser.ReportIdDatabaseId allowedCombo : reportIdDatabaseIdPermissionsFromReport.values()) {
                                            if (allowedCombo.getReportId() == or.getId() && allowedCombo.getDatabaseId() == d.getId()) { // then we can add the user with this info
                                                User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail(), d.getId(), or.getId(), selections, team);
                                                UserDAO.store(user1);
                                                stored = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (!stored) { // default to the current users home menu
                                        User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status,
                                                password, salt, loggedInUser.getUser().getEmail(), loggedInUser.getDatabase().getId(), loggedInUser.getUser().getReportId(), selections, team);
                                        UserDAO.store(user1);
                                    }
                                } else {
                                    User user1 = new User(0, end.atStartOfDay(), loggedInUser.getUser().getBusinessId(), email, user, status, password, salt, loggedInUser.getUser().getEmail(), d != null ? d.getId() : 0, or != null ? or.getId() : 0, selections, team);
                                    UserDAO.store(user1);
                                }
                            }
                            row++;
                        }
                    }
                    opcPackage.revert();
                    StringBuilder message = new StringBuilder("User file uploaded.");
                    if (rejected.size() > 0) {
                        message.append("  Some users rejected: ");
                        for (String reject : rejected) {
                            message.append(reject).append(", ");
                        }
                    }
                    model.put("error", message);
                } catch (Exception e) { // now the import has it's on exception catching
                    String exceptionError = e.getMessage();
                    e.printStackTrace();
                    model.put("error", exceptionError);
                }
            }
            model.put("users", AdminService.getUserListForBusinessWithBasicSecurity(loggedInUser));
            AdminService.setBanner(model, loggedInUser);
            return "manageusers";
        } else {
            return "redirect:/api/Login";
        }
    }
}