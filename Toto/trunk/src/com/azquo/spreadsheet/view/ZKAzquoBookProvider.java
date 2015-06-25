package com.azquo.spreadsheet.view;

/**
 * Created by cawley on 24/02/15
 * <p/>
 * After a little thinking I want this to simply deliver a book prepared by the controller. The point here is to implement the interface.
 */

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.azquo.spreadsheet.controller.OnlineController;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.jsp.BookProvider;

public class ZKAzquoBookProvider implements BookProvider {
    public Book loadBook(ServletContext servletContext, HttpServletRequest request, HttpServletResponse res) {
        return (Book) request.getAttribute(OnlineController.BOOK); // screw it keep it really simple. Remove from session?
    }
}
