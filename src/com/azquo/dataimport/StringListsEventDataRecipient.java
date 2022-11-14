package com.azquo.dataimport;

import java.util.ArrayList;
import java.util.List;

public class StringListsEventDataRecipient implements POIEventDataRecipient {

    private final List<List<String>> data;
    private int row = 0;

    public StringListsEventDataRecipient() {
        data = new ArrayList<>(); // will assume single threaded for the mo
        data.add(new ArrayList<>());
    }

    @Override
    public void cellData(String s) {
        data.get(row).add(s);
    }

    @Override
    public void endRow() {
        data.add(new ArrayList<>());
        row++;
    }

    public List<List<String>> getData() {
        while (data.size() > 1){
            if (data.get(row).isEmpty()){
                data.remove(row);
                row--;
            } else {
                break;
            }
        }
        return data;
    }
}
