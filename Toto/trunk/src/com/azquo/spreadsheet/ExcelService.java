package com.azquo.spreadsheet;

import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;

public class ExcelService {

    public static UserRegionOptions getUserRegionOptions(LoggedInUser loggedInUser, String optionsSource, int reportId, String region) {
        UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, optionsSource);
        // UserRegionOptions from MySQL will have limited fields filled
        UserRegionOptions userRegionOptions2 = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
        // only these five fields are taken from the table
        if (userRegionOptions2 != null) {
            if (userRegionOptions.getSortColumn() == null) {
                userRegionOptions.setSortColumn(userRegionOptions2.getSortColumn());
                userRegionOptions.setSortColumnAsc(userRegionOptions2.getSortColumnAsc());
            }
            if (userRegionOptions.getSortRow() == null) {
                userRegionOptions.setSortRow(userRegionOptions2.getSortRow());
                userRegionOptions.setSortRowAsc(userRegionOptions2.getSortRowAsc());
            }
            userRegionOptions.setHighlightDays(userRegionOptions2.getHighlightDays());

        }
        return userRegionOptions;
    }






}
