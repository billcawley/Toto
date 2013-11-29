package com.azquo.toto.controller;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.Value;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 19/11/13
 * Time: 22:14
 * Excel should make individual calls for provenance. This may also deliver lists then
 */

@Controller
@RequestMapping("/Provenance")

public class ProvenanceController {

    @Autowired
    private LoginService loginService;
    @Autowired
    private NameService nameService;
//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "connectionid", required = false) final String connectionId, @RequestParam(value = "name", required = false) String name
            , @RequestParam(value = "region", required = false) final String region, @RequestParam(value = "col", required = false) String col, @RequestParam(value = "row", required = false) String row) throws Exception {
        // we assume row and col starting at 0
        try {
            if (connectionId == null) {
                return "error:no connection id";
            }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }

            if (name != null && name.length() > 0){
                Name theName = nameService.findByName(loggedInConnection, name);
                if (theName != null){
                    System.out.println("In provenance controller name found : " + name);
                    return formatProvenanceForExcel(theName.getProvenance());
                } else {
                    return "error:name not found :" + name;
                }
            }

            if (row != null && row.length() > 0 && col != null && col.length() > 0){
                int rowInt = 0;
                int colInt = 0;
                try{
                    rowInt = Integer.parseInt(row);
                    colInt = Integer.parseInt(col);
                } catch (Exception e){
                    return "error: row/col must be integers";
                }
                final List<List<List<Value>>> dataValueMap = loggedInConnection.getDataValueMap(region);

                // going to assume row and col start at 1 hence for index bits on here need to decrement
                if (dataValueMap != null){
                    if (dataValueMap.get(rowInt) != null){
                        List<List<Value>> rowValues = dataValueMap.get(rowInt);
                        if (rowValues.get(colInt) != null){
                            List<Value> valuesForCell = rowValues.get(colInt);
                            if (valuesForCell.size() == 1){
                                return formatProvenanceForExcel(valuesForCell.get(0).getProvenance());
                            } else {
                                // TODO : deal with provanence on a cell where there were mulitple values to make that cell's value
                            }
                            // TODO : the cell was made from no values? error or message
                            return "error: no values and hence provenance for : " + col + ", " + row;
                        } else {
                            return "error: col out of range : " + col;
                        }
                    } else {
                        return "error: row out of range : " + row;
                    }
                } else {
                    return "error: data has not been sent for that row/col/region";
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
        return "no action taken";
    }

    public String formatProvenanceForExcel(Provenance provenance){
        if (provenance == null){
            return "no provenance";
        } else {
            return provenance.getUser() + "\r" + provenance.getTimeStamp() + "\r" + provenance.getMethod() + "\r" + provenance.getName() + "\r" + provenance.getColumnHeadings() + "\r" + provenance.getRowHeadings() + "\r" + provenance.getContext();
        }
    }

}
