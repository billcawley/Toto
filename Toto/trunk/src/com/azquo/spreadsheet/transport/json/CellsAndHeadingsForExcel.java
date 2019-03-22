package com.azquo.spreadsheet.transport.json;

import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.transport.RegionOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by edward on 24/11/16.
 * <p>
 * A stripped down version of CellsAndHeadingsForDisplay but for Excel.
 * <p>
 * I decided that the Excel code should not replicate CellForDisplay etc. In time there may be a better abstraction.
 * <p>
 * Will be turned into json to send to Excel.
 */
public class CellsAndHeadingsForExcel implements Serializable {

    private final List<List<String>> columnHeadingsSource;
    private final List<List<String>> rowHeadingsSource;
    private final List<List<String>> columnHeadings;
    private final List<List<String>> rowHeadings;
    private final List<List<String>> context;
    private final List<List<String>> data;
    private final List<List<Boolean>> highlight;
    private final List<List<String>> comments;
    private final String options;
    private final String lockResult;

    public CellsAndHeadingsForExcel(CellsAndHeadingsForDisplay source) {
        this.rowHeadingsSource = null;
        this.columnHeadingsSource = null;
        this.columnHeadings = source.getColumnHeadings();
        this.rowHeadings = source.getRowHeadings();
        this.context = source.getContextSource();
        this.data = new ArrayList<>();
        this.highlight = new ArrayList<>();
        this.options = null;
        List<List<String>> tempComments = new ArrayList<>();

        boolean comments = false;
        for (List<CellForDisplay> row : source.getData()) {

            List<String> dataRow = new ArrayList<>();
            this.data.add(dataRow);

            List<Boolean> highlightRow = new ArrayList<>();
            this.highlight.add(highlightRow);

            List<String> commentRow = new ArrayList<>();
            tempComments.add(commentRow);

            for (CellForDisplay cell : row) {
                dataRow.add(cell.getStringValue()); // should be fine
                if (cell.getComment() != null) {
                    comments = true;
                }
                commentRow.add(cell.getComment());
                highlightRow.add(cell.isHighlighted());
            }
        }

        this.comments = comments ? tempComments : null; // maybe check those variable names . . .
        this.lockResult = source.getLockResult();
    }

    public List<List<String>> getColumnHeadings() {
        return columnHeadings;
    }

    public List<List<String>> getRowHeadings() {
        return rowHeadings;
    }

    public List<List<String>> getContext() {return context; }

    public List<List<String>> getData() {
        return data;
    }

    public List<List<Boolean>> getHighlight() { return highlight; };

    public String getLockResult() {
        return lockResult;
    }

    public List<List<String>> getComments() {
        return comments;
    }


}
