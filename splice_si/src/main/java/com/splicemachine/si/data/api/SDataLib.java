package com.splicemachine.si.data.api;

import com.splicemachine.hbase.KVPair;
import com.splicemachine.hbase.MeasuredRegionScanner;
import com.splicemachine.metrics.MetricFactory;
import com.splicemachine.utils.ByteSlice;
import org.apache.hadoop.hbase.client.OperationWithAttributes;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.InternalScanner;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.util.Pair;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Defines an abstraction over the construction and manipulate of HBase operations. Having this abstraction allows an
 * alternate lightweight store to be used instead of HBase (e.g. for rapid testing).
 */
public interface SDataLib<Data,
        Put extends OperationWithAttributes,
        Delete,
        Get extends OperationWithAttributes,Scan>{
    byte[] newRowKey(Object[] args);

    byte[] encode(Object value);

    <T> T decode(byte[] value,Class<T> type);

    <T> T decode(byte[] value,int offset,int length,Class<T> type);

    List<Data> listResult(Result result);

    Put newPut(byte[] key);

    Put newPut(ByteSlice key);

    Put newPut(byte[] key,Integer lock);

    void addKeyValueToPut(Put put,byte[] family,byte[] qualifier,long timestamp,byte[] value);

    Iterable<Data> listPut(Put put);

    byte[] getPutKey(Put put);

    Get newGet(byte[] rowKey,List<byte[]> families,List<List<byte[]>> columns,Long effectiveTimestamp);

    Get newGet(byte[] rowKey,List<byte[]> families,List<List<byte[]>> columns,Long effectiveTimestamp,int maxVersions);

    void setGetTimeRange(Get get,long minTimestamp,long maxTimestamp);

    void setGetMaxVersions(Get get);

    void setGetMaxVersions(Get get,int max);

    Scan newScan(byte[] startRowKey,byte[] endRowKey,List<byte[]> families,List<List<byte[]>> columns,Long effectiveTimestamp);

    void setScanTimeRange(Scan get,long minTimestamp,long maxTimestamp);

    void setScanMaxVersions(Scan get);

    Delete newDelete(byte[] rowKey);

    void addFamilyQualifierToDelete(Delete delete,byte[] family,byte[] qualifier,long timestamp);

    void addDataToDelete(Delete delete,Data data,long timestamp);

    Put toPut(KVPair kvPair,byte[] family,byte[] column,long longTransactionId);

    boolean singleMatchingColumn(Data element,byte[] family,byte[] qualifier);

    boolean singleMatchingFamily(Data element,byte[] family);

    boolean singleMatchingQualifier(Data element,byte[] qualifier);

    boolean matchingQualifier(Data element,byte[] qualifier);

    boolean matchingValue(Data element,byte[] value);

    boolean matchingRowKeyValue(Data element,Data other);

    Data newValue(Data element,byte[] value);

    Data newValue(byte[] rowKey,byte[] family,byte[] qualifier,Long timestamp,byte[] value);

    boolean isAntiTombstone(Data element,byte[] antiTombstone);

    Comparator getComparator();

    long getTimestamp(Data element);

    String getFamilyAsString(Data element);

    String getQualifierAsString(Data element);

    void setRowInSlice(Data element,ByteSlice slice);

    boolean isFailedCommitTimestamp(Data element);

    Data newTransactionTimeStampKeyValue(Data element,byte[] value);

    long getValueLength(Data element);

    long getValueToLong(Data element);

    long optionalValueToLong(Data element,long defaultValue);

    byte[] getDataFamily(Data element);

    byte[] getDataQualifier(Data element);

    byte[] getDataValue(Data element);

    byte[] getDataRow(Data element);

    byte[] getDataValueBuffer(Data element);

    byte[] getDataRowBuffer(Data element);

    byte[] getDataQualifierBuffer(Data element);

    int getDataQualifierOffset(Data element);

    int getDataRowOffset(Data element);

    int getDataRowlength(Data element);

    int getDataValueOffset(Data element);

    int getDataValuelength(Data element);

    int getLength(Data element);

    Result newResult(List<Data> element);

    Data[] getDataFromResult(Result result);

    Data getColumnLatest(Result result,byte[] family,byte[] qualifier);

    boolean regionScannerNext(RegionScanner regionScanner,List<Data> data) throws IOException;

    void setThreadReadPoint(RegionScanner delegate);

    boolean regionScannerNextRaw(RegionScanner regionScanner,List<Data> data) throws IOException;

    MeasuredRegionScanner<Data> getBufferedRegionScanner(HRegion region,
                                                         RegionScanner delegate,
                                                         Scan scan,
                                                         int bufferSize,
                                                         MetricFactory metricFactory);

    MeasuredRegionScanner<Data> getRateLimitedRegionScanner(HRegion region,
                                                            RegionScanner delegate,
                                                            Scan scan,
                                                            int bufferSize,
                                                            int readsPerSecond,
                                                            MetricFactory metricFactory);

    Filter getActiveTransactionFilter(long beforeTs,long afterTs,byte[] destinationTable);

    boolean internalScannerNext(InternalScanner internalScanner,List<Data> data) throws IOException;

    boolean isDataInRange(Data data,Pair<byte[], byte[]> range);

    Data matchKeyValue(Iterable<Data> kvs,byte[] columnFamily,byte[] qualifier);

    Data matchKeyValue(Data[] kvs,byte[] columnFamily,byte[] qualifier);

    Data matchDataColumn(Data[] kvs);

    Data matchDataColumn(List<Data> kvs);

    Data matchDataColumn(Result result);
}