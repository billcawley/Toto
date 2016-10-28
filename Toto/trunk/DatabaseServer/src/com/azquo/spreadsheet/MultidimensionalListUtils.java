package com.azquo.spreadsheet;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracted from DSSpreadsheet services by edward on 28/10/16.
 *
 * Functions to manipulate arrays (typically of headings). Transpose and simple permute.
 */
public class MultidimensionalListUtils {


    /* ok we're passed a list of lists
    what is returned is a 2d array (also a list of lists) featuring every possible variation in order
    so if the initial lists passed were
    A,B
    1,2,3,4
    One, Two, Three

    The returned list will be the size of each passed list multiplied together (on that case 2*4*3 so 24)
    and each entry on that list will be the size of the number of passed lists, in this case 3
    so

    A, 1, ONE
    A, 1, TWO
    A, 1, THREE
    A, 2, ONE
    A, 2, TWO
    A, 2, THREE
    A, 3, ONE
    A, 3, TWO
    A, 3, THREE
    A, 4, ONE
    A, 4, TWO
    A, 4, THREE
    B, 1, ONE
    B, 1, TWO
    B, 1, THREE
    B, 2, ONE
    B, 2, TWO

    etc.

    Row/column reference below is based off the above example - in toReturn the index of the outside list is y and the the inside list x

    I tried optimising based on calculating the size of the ArrayList that would be required but it seemed to provide little additional benefit,
     plus I don't think this is a big performance bottleneck. Commented attempt at optimising will be in SVN if required.
    */

    public static <T> List<List<T>> get2DPermutationOfLists(final List<List<T>> listsToPermute) {
        //this version does full permute
        List<List<T>> toReturn = null;
        for (List<T> permutationDimension : listsToPermute) {
            if (permutationDimension == null) {
                permutationDimension = new ArrayList<>();
                permutationDimension.add(null);
            }
            if (toReturn == null) { // first one, just assign the single column
                toReturn = new ArrayList<>(permutationDimension.size());
                for (T item : permutationDimension) {
                    List<T> createdRow = new ArrayList<>();
                    createdRow.add(item);
                    toReturn.add(createdRow);
                }
            } else {
                // this is better as a different function as internally it created a new 2d array which we can then assign back to this one
                toReturn = get2DArrayWithAddedPermutation(toReturn, permutationDimension);
            }
        }
        return toReturn;
    }


    /*

    so say we already have
    a,1
    a,2
    a,3
    a,4
    b,1
    b,2
    b,3
    b,4

    for example

    and want to add the permutation ONE, TWO, THREE onto it.

    The returned list of lists will be the size of the list of lists passed * the size of teh passed new dimension
    and the nested lists in the returned list will be one bigger, featuring the new dimension

    if we looked at the above as a reference it would be 3 times as high and 1 column wider
     */


    public static <T> List<List<T>> get2DArrayWithAddedPermutation(final List<List<T>> existing2DArray, List<T> permutationWeWantToAdd) {
        List<List<T>> toReturn = new ArrayList<>(existing2DArray.size() * permutationWeWantToAdd.size());
        int existing;
        for (existing = permutationWeWantToAdd.size() - 1; existing > 0; existing--) {
            if (permutationWeWantToAdd.get(existing) != null) {
                break;
            }
        }
        existing++;
        for (List<T> existingRow : existing2DArray) {
            int count = 0;
            for (T elementWeWantToAdd : permutationWeWantToAdd) { // for each new element
                if (count++ == existing) {
                    break;
                }
                List<T> newRow = new ArrayList<>(existingRow); // copy the existing row
                newRow.add(elementWeWantToAdd);// add the extra element
                toReturn.add(newRow);
            }
        }
        //make up blank lines if necessary
        while (existing2DArray.size() > 0 && toReturn.size() < permutationWeWantToAdd.size()) {
            List<T> newRow = new ArrayList<>();
            while (newRow.size() <= existing2DArray.get(0).size()) {
                newRow.add(null);
            }
            toReturn.add(newRow);
        }
        return toReturn;
    }

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

    static <T> List<List<T>> transpose2DList(final List<List<T>> source2Dlist) {
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
