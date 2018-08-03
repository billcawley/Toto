package com.azquo.dataimport;

import com.fasterxml.jackson.databind.MappingIterator;

import java.util.Iterator;
import java.util.List;

class HeadingsWithIteratorAndBatchSize {
    MappingIterator<String[]> originalIterator;
    Iterator<String[]> lineIterator;
    int batchSize;
    List<ImmutableImportHeading> headings;

    HeadingsWithIteratorAndBatchSize(MappingIterator<String[]> originalIterator, int batchSize) {
        this.originalIterator = originalIterator;
        this.batchSize = batchSize;
        this.lineIterator = originalIterator; // initially use the original, it may be overwritten
    }
}
