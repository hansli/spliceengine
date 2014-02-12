package com.splicemachine.si.data.hbase;

import com.splicemachine.si.impl.RowAccumulator;
import com.splicemachine.storage.EntryAccumulator;
import com.splicemachine.storage.EntryDecoder;
import com.splicemachine.storage.EntryPredicateFilter;
import com.splicemachine.storage.index.BitIndex;

import java.io.IOException;

import org.apache.hadoop.hbase.KeyValue;

public class HRowAccumulator implements RowAccumulator<byte[],KeyValue> {
    private final EntryPredicateFilter predicateFilter;
    private final EntryAccumulator entryAccumulator;
    private final EntryDecoder decoder;

		private long bytesAccumulated = 0l;

    public HRowAccumulator(EntryPredicateFilter predicateFilter, EntryDecoder decoder) {
        this.predicateFilter = predicateFilter;
        this.entryAccumulator = predicateFilter.newAccumulator();
        this.decoder = decoder;
    }

    @Override
    public boolean isOfInterest(KeyValue keyValue) {
        decoder.set(keyValue.getBuffer(),keyValue.getValueOffset(),keyValue.getValueLength());
        final BitIndex currentIndex = decoder.getCurrentIndex();
				return entryAccumulator.isInteresting(currentIndex);
    }

    @Override
    public boolean accumulate(KeyValue keyValue) throws IOException {
				bytesAccumulated+=keyValue.getLength();
        decoder.set(keyValue.getBuffer(),keyValue.getValueOffset(),keyValue.getValueLength());
        boolean pass = predicateFilter.match(decoder, entryAccumulator);
        if(!pass)
            entryAccumulator.reset();
        return pass;
    }

    @Override
    public boolean isFinished() {
        return entryAccumulator.isFinished();
    }

    @Override
    public byte[] result() {
        final byte[] result = entryAccumulator.finish();
        entryAccumulator.reset();
        return result;
    }

		@Override public long getBytesVisited() { return bytesAccumulated; }
}
