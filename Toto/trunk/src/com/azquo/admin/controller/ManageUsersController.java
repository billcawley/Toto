package com.azquo.admin.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.BackupService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
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
import org.zkoss.poi.xssf.usermodel.XSSFName;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
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
            , @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
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
    ) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            if (NumberUtils.isDigits(deleteId)) {
                AdminService.deleteUserById(Integer.parseInt(deleteId));
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
                    if (toEdit == null && UserDAO.findByEmail(email) != null) {
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
                                        final Map<String, TypedPair<Integer, Integer>> reportIdDatabaseIdPermissionsFromReport = loggedInUser.getReportIdDatabaseIdPermissions();
                                        for (TypedPair<Integer, Integer> allowedCombo : reportIdDatabaseIdPermissionsFromReport.values()) {
                                            if (allowedCombo.getFirst() == or.getId() && allowedCombo.getSecond() == d.getId()) { // then we can add the user with this info
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