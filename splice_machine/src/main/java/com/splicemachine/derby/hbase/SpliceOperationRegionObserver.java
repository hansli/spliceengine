package com.splicemachine.derby.hbase;

import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.pipeline.api.Service;
import com.splicemachine.si.api.TransactionalRegion;
import com.splicemachine.si.impl.TransactionalRegions;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.coprocessor.BaseRegionObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.InternalScanner;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Region Observer looking for a scan with <i>SpliceServerInstructions</i> set on the attribute map of the scan.
 *
 * @author johnleach
 *
 */
public class SpliceOperationRegionObserver extends BaseRegionObserver {
    private static Logger LOG = Logger.getLogger(SpliceOperationRegionObserver.class);
    public static String SPLICE_OBSERVER_INSTRUCTIONS = "Z"; // Reducing this so the amount of network traffic will be reduced...
    public static String SPLICE_OBSERVER_CACHING = "C";
    public static DerbyFactory derbyFactory = DerbyFactoryDriver.derbyFactory;
    /*make volatile to avoid thread visibility issues*/
    private volatile TransactionalRegion txnRegion;

    /**
     * Logs the start of the observer.
     */
    @Override
    public void start(final CoprocessorEnvironment e) throws IOException {
        SpliceLogUtils.info(LOG, "Starting TransactionalManagerRegionObserver CoProcessor %s", SpliceOperationRegionObserver.class);

        SpliceDriver.driver().registerService(new Service() {
            @Override
            public boolean start() {
                HRegion region = ((RegionCoprocessorEnvironment) e).getRegion();
                SpliceOperationRegionObserver.this.txnRegion = TransactionalRegions.get(region);
                return true;
            }
            @Override public boolean shutdown() { return false; }
        });
        SpliceLogUtils.info(LOG, "Started CoProcessor %s", SpliceOperationRegionObserver.class);
        super.start(e);
    }

    /**
     * Logs the stop of the observer.
     */
    @Override
    public void stop(CoprocessorEnvironment e) throws IOException {
        SpliceLogUtils.info(LOG, "Stopping the CoProcessor %s",SpliceOperationRegionObserver.class);
        super.stop(e);
        if(txnRegion!=null) {
            txnRegion.close();
            txnRegion=null; //dereference to avoid memory leakage
        }
    }

    @Override
    public RegionScanner preScannerOpen(ObserverContext<RegionCoprocessorEnvironment> e, Scan scan, RegionScanner s) throws IOException {
        if(scan.getAttribute(SPLICE_OBSERVER_INSTRUCTIONS)!=null)
            return super.preScannerOpen(e,scan,s);


        return super.preScannerOpen(e, scan, s);
    }

    /**
     * Override the postScannerOpen to wrap the scan with the SpliceOperationRegionScanner.  This allows for cases
     * where the hbase scanner will be limited (ProjectRestrictOperation) or where new records would be added (LeftOuterJoin).
     */
    @Override
    public RegionScanner postScannerOpen(ObserverContext<RegionCoprocessorEnvironment> e, Scan scan,RegionScanner s) throws IOException {
        if (scan.getAttribute(SPLICE_OBSERVER_INSTRUCTIONS) != null){
            SpliceLogUtils.trace(LOG, "postScannerOpen called, wrapping SpliceOperationRegionScanner");
            if (scan.getCaching() < 0) // Async Scanner is corrupting this value..
                scan.setCaching(SpliceConstants.DEFAULT_CACHE_SIZE);
            HRegion region = e.getEnvironment().getRegion();
            return super.postScannerOpen(e, scan, derbyFactory.getOperationRegionScanner(s,scan, region,getTxnRegion(region)));
        }
        return super.postScannerOpen(e, scan, s);
    }

    @Override
    public void postScannerClose(ObserverContext<RegionCoprocessorEnvironment> e, InternalScanner s) throws IOException {
        super.postScannerClose(e, s);
    }

    @Override
    public boolean preScannerNext(ObserverContext<RegionCoprocessorEnvironment> e, InternalScanner s, List<Result> results, int limit, boolean hasMore) throws IOException {
        if(s instanceof SpliceBaseOperationRegionScanner){
            ((SpliceBaseOperationRegionScanner)s).setupBatch();
        }
        return super.preScannerNext(e, s, results, limit, hasMore);
    }

    @Override
    public boolean postScannerNext(ObserverContext<RegionCoprocessorEnvironment> e, InternalScanner s, List<Result> results, int limit, boolean hasMore) throws IOException {
        if(s instanceof SpliceBaseOperationRegionScanner){
            ((SpliceBaseOperationRegionScanner)s).cleanupBatch();
        }
        return super.postScannerNext(e, s, results, limit, hasMore);
    }

    /******************************************************************************************************************/
    /*private helper methods*/
    private TransactionalRegion getTxnRegion(HRegion region) throws IOException {
        /*
         * Get the transactional region. in 99.999% of cases, the transactional region is set in the
         * SpliceDriver.Service before the server allows connections, and we are up and running. However, it
         * IS possible that we attempt to perform a scan against a region BEFORE the SpliceDriver has finished
         * its boot sequence. In this case, the txnRegion is null BY DESIGN, in order to avoid a race condition
         * on startup. As a result, we really can't perform query actions, and we have to bail. We do this
         * by throwing a retryable IOException back to the client--presumably, at that point, the HBase client
         * (or AsyncHBase) will automatically retry the request, and allow the boot sequence time to complete.
         */
        TransactionalRegion tr = txnRegion; //assign to local variable to avoid double-reading a volatile variable
        if (tr == null) {
            if (!SpliceDriver.driver().isStarted()) {
                /* We originally threw ServerNotRunningYetException here but asyc hbase is un-aware of this
                 * exception and just lets it propagate to the client. */
                throw new NotServingRegionException("Server is not yet online, please retry");
            }
            tr = TransactionalRegions.get(region);
            txnRegion = tr;
        }
        return tr;
    }
}
