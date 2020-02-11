package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 *
 * To multi thread the headings made by permute items
 */
class ComboBuilder implements Callable<Void> {

    private final int comboSize;
    private final List<Name> permuteNames;
    private final int sourceFrom;// inclusive
    private final int sourceTo;// NOT inclusive
    private final Set<List<Name>> foundCombinations;
    private final List<Name> sharedNamesList;

    ComboBuilder(int comboSize, List<Name> permuteNames, int sourceFrom, int sourceTo, Set<List<Name>> foundCombinations, List<Name> sharedNamesList) {
        this.comboSize = comboSize;
        this.permuteNames = permuteNames;
        this.sourceFrom = sourceFrom;
        this.sourceTo = sourceTo;
        this.foundCombinations = foundCombinations;
        this.sharedNamesList = sharedNamesList;
    }

    @Override
    public Void call() {
        for (int i = sourceFrom; i < sourceTo; i++) {
            List<Name> foundCombination = new ArrayList<>(comboSize);
            for (Name pName : permuteNames) {
                foundCombination.add(sharedNamesList.get(i).memberName(pName));
            }
            foundCombinations.add(foundCombination);
        }
        return null;
    }
}
