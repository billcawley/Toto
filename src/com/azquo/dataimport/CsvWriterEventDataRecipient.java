package com.azquo.dataimport;

import com.csvreader.CsvWriter;

import java.io.IOException;

public class CsvWriterEventDataRecipient implements POIEventDataRecipient {

    final CsvWriter csvWriter;

    public CsvWriterEventDataRecipient(CsvWriter csvWriter) {
        this.csvWriter = csvWriter;
    }

    @Override
    public void cellData(String s) {
        try {
            csvWriter.write(s.replace("\n", "\\\\n").replace("\r", "")
                    .replace("\t", "\\\\t"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void endRow() {
        try {
            csvWriter.endRecord();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
