package com.azquo;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracted from DSSpreadsheet services by edward on 28/10/16.
 *
 * Functions to manipulate arrays (typically of headings). Transpose is all that's left now. DataRegionHeading Service does expanding and permuting.
 */
public class MultidimensionalListUtils {

    /* ok so transposing happens here
    this is because the expand headings function is orientated for row headings and the column heading definitions are unsurprisingly set up for columns
    what is notable here is that the headings are then stored this way in column headings, we need to say "give me the headings for column x"

    NOTE : this means the column heading are not stored according to the orientation used in the above function hence, to output them we have to transpose them again!

    OK, having generified the function we should only need one function. The list could be anything, names, list of names, HashMaps whatever
    generics ensure that the return type will match the sent type now rather similar to the stack overflow example :)

    Variable names assume first list is of rows and the second is each row. down then across.
    So the size of the first list is the y size (number of rows) and the size of the nested list the xsize (number of columns)
    I'm going to model it that way round as when reading data from excel that's the default (we go line by line through each row, that's how the data is delivered), the rows is the outside list
    of course could reverse all descriptions and the function could still work

    */

    public static <T> List<List<T>> transpose2DList(final List<List<T>> source2Dlist) {
        if (source2Dlist.size() == 0) {
            return new ArrayList<>();
        }
        final int oldXMax = source2Dlist.get(0).size(); // size of nested list, as described above (that is to say get the length of one row)
        final List<List<T>> flipped = new ArrayList<>(oldXMax);
        for (int newY = 0; newY < oldXMax; newY++) {
            List<T> newRow = new ArrayList<>(source2Dlist.size()); // make a new row
            for (List<T> oldRow : source2Dlist) { // and step down each of the old rows
                newRow.add(oldRow.get(newY));//so as we're moving across the new row we're moving down the old rows on a fixed column
                // the transposing is happening as a list which represents a row would typically be accessed by an x value but instead it's being accessed by an y value
                // in this loop the row being read from changes but the cell in that row does not
            }
            flipped.add(newRow);
        }
        return flipped;
    }
}
