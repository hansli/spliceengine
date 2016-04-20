package com.splicemachine.derby.impl.sql.execute.operations.window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.derby.iapi.sql.execute.SpliceRuntimeContext;
import com.splicemachine.derby.impl.sql.execute.operations.window.function.SpliceGenericWindowFunction;
import com.splicemachine.derby.utils.PartitionAwarePushBackIterator;

/**
 *
 * Created by jyuan on 9/15/14.
 */
abstract public class BaseFrameBuffer implements WindowFrameBuffer{
    protected final SpliceRuntimeContext runtimeContext;
    protected final long frameStart;
    protected final long frameEnd;
    private final WindowAggregator[] aggregators;
    private final ExecRow templateRow;
    private ResultBuffer resultBuffer;

    protected int start;
    protected int end;
    protected int current;
    protected List<ExecRow> rows;
    protected PartitionAwarePushBackIterator<ExecRow> partitionIter;
    protected byte[] partition;
    protected boolean endOfPartition;

    public static WindowFrameBuffer createFrameBuffer(SpliceRuntimeContext runtimeContext,
                                                      WindowAggregator[] aggregators,
                                                      PartitionAwarePushBackIterator<ExecRow> source,
                                                      FrameDefinition frameDefinition,
                                                      int[] sortColumns,
                                                      ExecRow templateRow) throws StandardException {

        FrameDefinition.FrameMode frameMode = frameDefinition.getFrameMode();
        if (frameMode == FrameDefinition.FrameMode.ROWS) {
            return new PhysicalGroupFrameBuffer(
                    runtimeContext, aggregators, source, frameDefinition, templateRow);
        }
        else {
            return new LogicalGroupFrameBuffer(
                    runtimeContext, aggregators, source, frameDefinition, sortColumns, templateRow);
        }
    }

    public BaseFrameBuffer (SpliceRuntimeContext runtimeContext,
                            WindowAggregator[] aggregators,
                            PartitionAwarePushBackIterator<ExecRow> source,
                            FrameDefinition frameDefinition,
                            ExecRow templateRow) throws StandardException {
        this.runtimeContext = runtimeContext;
        this.aggregators = aggregators;
        this.partitionIter = source;
        this.templateRow = templateRow;

        for (WindowAggregator aggregator: this.aggregators) {
            aggregator.initialize(this.templateRow);
        }
        // All aggregators in this frame buffer share the same over() clause
        // so should all have the same frame definition.
        // The frame definition will not change over the life of this frame buffer
        this.frameStart = frameDefinition.getFrameStart().getValue();
        this.frameEnd = frameDefinition.getFrameEnd().getValue();
        this.rows = new ArrayList<>();
        this.resultBuffer = new ResultBuffer();
    }

    @Override
    public ExecRow next(SpliceRuntimeContext runtimeContext) throws StandardException, IOException {
        while (! this.resultBuffer.isFinished()) {
            processFrame(runtimeContext);
        }
        return this.resultBuffer.next();
    }


    private ExecRow processFrame(SpliceRuntimeContext runtimeContext) throws StandardException, IOException {

        ExecRow row = this.next();

        if (row == null) {
            // This is the end of one partition, peek the next row
            row = partitionIter.next(runtimeContext);
            if (row == null) {
                // This is the end of the region/frame
                finishFrame();
                return null;
            }
            else {
                // init a new window buffer for the next partition
                partitionIter.pushBack(row);
                this.reset();
                row = this.next();
            }
        }

        this.move();

        return row;
    }

    protected void add(ExecRow row) throws StandardException{
        for(WindowAggregator aggregator : aggregators) {
            // this applies the function given the current row
            aggregator.accumulate(row, templateRow);
        }
    }

    protected void remove() throws StandardException {
        for(WindowAggregator aggregator : aggregators) {
            int aggregatorColumnId = aggregator.getFunctionColumnId();
            SpliceGenericWindowFunction windowFunction =
                    (SpliceGenericWindowFunction) templateRow.getColumn(aggregatorColumnId).getObject();
            // This applies a "negative transition function" to the previously calculated result
            // when the window frame is advanced within the partition and rows are dropping off the end.
            // This is required by "running aggregates" (sum, count, etc) so that the whole frame doesn't
            // have to be recalculated every time we advance a row.
            windowFunction.remove();
        }
    }

    private ExecRow next() throws IOException, StandardException{
        if (current >= rows.size()) {
            return null;
        }
        ExecRow row = rows.get(current);
        for (WindowAggregator aggregator : aggregators) {
            // This set the current row's result after it's been calculated.
            // Currently, this is called right after add(), so at every application of the function on the current row.
            int aggregatorColumnId = aggregator.getFunctionColumnId();
            int resultColumnId = aggregator.getResultColumnId();
            SpliceGenericWindowFunction function = (SpliceGenericWindowFunction) templateRow.getColumn(aggregatorColumnId).getObject();
            row.setColumn(resultColumnId, function.getResult().cloneValue(false));
        }
        this.resultBuffer.bufferResult(row);
        return row;
    }

    private void reset() throws StandardException, IOException {
        rows = new ArrayList<>();

        // Initialize window functions
        for (WindowAggregator aggregator : this.aggregators) {
            int aggregatorColumnId = aggregator.getFunctionColumnId();
            SpliceGenericWindowFunction windowFunction =
                    (SpliceGenericWindowFunction) templateRow.getColumn(aggregatorColumnId).getObject();
            windowFunction.reset();
            aggregator.initialize(templateRow);
        }

        // initializes frame buffer
        loadFrame();
    }

    private void finishFrame() throws StandardException {
        for (WindowAggregator aggregator : aggregators) {
            SpliceGenericWindowFunction cachedAggregator = aggregator.getCachedAggregator();
            if (cachedAggregator != null) {
                List<DataValueDescriptor> results = cachedAggregator.finishFrame();
                if (results != null) {
                    int resultColumnId = aggregator.getResultColumnId();
                    resultBuffer.setColumnResults(resultColumnId, results);
                }
            }
        }
        resultBuffer.setFinished();
    }

    abstract protected void loadFrame() throws IOException, StandardException;

    private static class ResultBuffer implements Iterator<ExecRow> {
        private final List<ExecRow> results = new ArrayList<>();
        private Iterator<ExecRow> resultItr;
        private boolean finished;

        void bufferResult(ExecRow resultRow) {
            results.add(resultRow);
        }

        boolean isFinished() {
            return finished;
        }

        void reset() {
            resultItr = null;
            results.clear();
            finished = false;
        }

        public void setFinished() {
            finished = true;
            resultItr = results.iterator();
        }

        public int size() {
            return results.size();
        }

        @Override
        public boolean hasNext() {
            return (resultItr != null && resultItr.hasNext());
        }

        @Override
        public ExecRow next() {
            if (resultItr == null || ! resultItr.hasNext()) {
                return null;
            }
            ExecRow resultRow = resultItr.next();
            if (! hasNext()) {
                reset();
            }
            return resultRow;
        }

        @Override
        public void remove() {
            // not implemented
        }

        public void setColumnResults(int resultColumnId, List<DataValueDescriptor> columnResults) {
            for (int i = 0; i < results.size(); i++) {
                ExecRow row = results.get(i);
                row.setColumn(resultColumnId, columnResults.get(i));
            }
        }
    }
}