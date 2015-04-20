package com.azquo.spreadsheet.view;

/**
 * Created by cawley on 24/02/15
 *
 * After a little thinking I want this to simply deliver a book prepared by the controller.
 *
 */
import java.io.File;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.azquo.controller.OnlineController;
import org.zkoss.zss.api.*;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.jsp.BookProvider;
// svn test

public class ZKAzquoBookProvider implements BookProvider{
    public Book loadBook(ServletContext servletContext, HttpServletRequest request, HttpServletResponse res) {
        final Book book = (Book)request.getAttribute(OnlineController.BOOK);
        if (book != null) {
            return book;
        } else {
            try {
                // todo, replace with a blank book?
                return Importers.getImporter().imports(new File("/home/cawley/databases/Magen_swimshop/onlinereports/MagentoBestSellers.xlsx") , "Best Sellers By Period");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
