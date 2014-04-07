package com.splicemachine.derby.impl.sql.execute.operations;

import com.google.common.base.Strings;
import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.derby.hbase.SpliceDriver;
import com.splicemachine.derby.iapi.sql.execute.SpliceNoPutResultSet;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperationContext;
import com.splicemachine.derby.iapi.sql.execute.SpliceRuntimeContext;
import com.splicemachine.derby.impl.sql.execute.LazyDataValueFactory;
import com.splicemachine.derby.impl.store.access.hbase.HBaseRowLocation;
import com.splicemachine.derby.utils.marshall.KeyMarshaller;
import com.splicemachine.encoding.MultiFieldDecoder;
import com.splicemachine.storage.EntryDecoder;
import com.splicemachine.storage.EntryPredicateFilter;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.derby.catalog.IndexDescriptor;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.services.loader.GeneratedMethod;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.IndexRowGenerator;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.NoPutResultSet;
import org.apache.derby.iapi.store.access.Qualifier;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.BitSet;

public abstract class ScanOperation extends SpliceBaseOperation {
	private static Logger LOG = Logger.getLogger(ScanOperation.class);
	private static long serialVersionUID=7l;

	public int lockMode;
	public int isolationLevel;

    protected ScanInformation scanInformation;
	protected String tableName;
	protected String indexName;
	public boolean isConstraint;
	public boolean forUpdate;	
    protected ExecRow currentTemplate;
    protected int[] columnOrdering;
    protected ExecRow keyTemplate;
    protected int[] getColumnOrdering;
    protected DataValueDescriptor[] kdvds;
    protected KeyMarshaller keyMarshaller;
    protected EntryDecoder rowDecoder;
    protected MultiFieldDecoder keyDecoder;
    protected EntryPredicateFilter predicateFilter;
    private boolean cachedPredicateFilter = false;
    protected BitSet descColumns;

    public ScanOperation () {
		super();
	}

	public ScanOperation (long conglomId, Activation activation, int resultSetNumber,
												GeneratedMethod startKeyGetter, int startSearchOperator,
												GeneratedMethod stopKeyGetter, int stopSearchOperator,
												boolean sameStartStopPosition,
												String scanQualifiersField,
												GeneratedMethod resultRowAllocator,
												int lockMode, boolean tableLocked, int isolationLevel,
												int colRefItem,
												double optimizerEstimatedRowCount,
												double optimizerEstimatedCost) throws StandardException {
		super(activation, resultSetNumber, optimizerEstimatedRowCount, optimizerEstimatedCost);
		this.lockMode = lockMode;
		this.isolationLevel = isolationLevel;

        this.scanInformation = new DerbyScanInformation(resultRowAllocator.getMethodName(),
                startKeyGetter!=null? startKeyGetter.getMethodName(): null,
                stopKeyGetter!=null ? stopKeyGetter.getMethodName(): null,
                scanQualifiersField!=null? scanQualifiersField : null,
                conglomId,
                colRefItem,
                sameStartStopPosition,
                startSearchOperator,
                stopSearchOperator
                );
	}

    public ScanOperation(ScanInformation scanInformation,
                         OperationInformation operationInformation,
                         int lockMode,
                         int isolationLevel) throws StandardException {
        super(operationInformation);
        this.lockMode = lockMode;
        this.isolationLevel = isolationLevel;
        this.scanInformation = scanInformation;
    }

    protected KeyMarshaller getKeyMarshaller () {
        if (keyMarshaller == null)
            keyMarshaller = new KeyMarshaller(descColumns);

        return keyMarshaller;
    }

    protected MultiFieldDecoder getKeyDecoder() {
        if (keyDecoder == null)
            keyDecoder = MultiFieldDecoder.create(SpliceDriver.getKryoPool());
        return keyDecoder;
    }

    protected EntryDecoder getRowDecoder() {
        if(rowDecoder==null)
            rowDecoder = new EntryDecoder(SpliceDriver.getKryoPool());
        return rowDecoder;
    }

    protected int[] getColumnOrdering() throws StandardException{
        if (columnOrdering == null) {
            columnOrdering = scanInformation.getColumnOrdering();
        }
        return columnOrdering;
    }

    protected DataValueDescriptor[] getColumnDVDs() throws StandardException{
        if (kdvds == null) {
            int[] columnOrdering = getColumnOrdering();
            int[] format_ids = scanInformation.getConglomerate().getFormat_ids();
            kdvds = new DataValueDescriptor[columnOrdering.length];
            for (int i = 0; i < columnOrdering.length; ++i) {
                kdvds[i] = LazyDataValueFactory.getLazyNull(format_ids[columnOrdering[i]]);
            }
        }

        return kdvds;
    }

    protected EntryPredicateFilter getPredicateFilter(SpliceRuntimeContext spliceRuntimeContext) throws StandardException,IOException{
        if (!cachedPredicateFilter) {
            Scan scan = getScan(spliceRuntimeContext);
            predicateFilter = EntryPredicateFilter.fromBytes(scan.getAttribute(SpliceConstants.ENTRY_PREDICATE_LABEL));
            cachedPredicateFilter = true;
        }
        return predicateFilter;
    }
    @Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		lockMode = in.readInt();
		isolationLevel = in.readInt();
        scanInformation = (ScanInformation)in.readObject();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(lockMode);
		out.writeInt(isolationLevel);
        out.writeObject(scanInformation);
	}

    private BitSet getDescColumns() throws StandardException{
        LanguageConnectionContext lcc = activation.getLanguageConnectionContext();
        DataDictionary dd = lcc.getDataDictionary();
        ConglomerateDescriptor conglomDesc =
                dd.getConglomerateDescriptor(scanInformation.getConglomerateId());
        BitSet descCols = null;
        if (conglomDesc.isIndex()) {
            IndexRowGenerator irg = conglomDesc.getIndexDescriptor();
            IndexDescriptor indexDescriptor = irg.getIndexDescriptor();
            descCols = new BitSet(indexDescriptor.baseColumnPositions().length);
            boolean[] ascending = indexDescriptor.isAscending();
            for(int i=0;i<ascending.length;i++){
                if(!ascending[i])
                    descCols.set(i);
            }
        }
        return descCols;
    }
	@Override
    public void init(SpliceOperationContext context) throws StandardException{
        SpliceLogUtils.trace(LOG, "init called");
        super.init(context);
        scanInformation.initialize(context);
        try {
            ExecRow candidate = scanInformation.getResultRow();
            FormatableBitSet accessedCols = scanInformation.getAccessedColumns();
            boolean isKeyed = scanInformation.isKeyed();
            //TODO -sf- remove this call to getLanguageConnectionContext()
            currentRow = operationInformation.compactRow(candidate, scanInformation);
            currentTemplate = currentRow.getClone();
            if (currentRowLocation == null)
            	currentRowLocation = new HBaseRowLocation();
            this.descColumns = getDescColumns();
        } catch (Exception e) {
            SpliceLogUtils.logAndThrowRuntime(LOG, "Operation Init Failed!", e);
        }
    }

    @Override
    public SpliceNoPutResultSet executeScan(SpliceRuntimeContext runtimeContext) throws StandardException {
        SpliceLogUtils.trace(LOG, "executeScan");
        return new SpliceNoPutResultSet(activation,this, getMapRowProvider(this,OperationUtils.getPairDecoder(this,runtimeContext), runtimeContext));
    }

	@Override
	public SpliceOperation getLeftOperation() {
		return null;
	}

	protected void initIsolationLevel() {
		SpliceLogUtils.trace(LOG, "initIsolationLevel");
	}

	protected Scan buildScan(SpliceRuntimeContext ctx) {
		try{
            return getScan(ctx);
        } catch (StandardException e) {
			SpliceLogUtils.logAndThrowRuntime(LOG,e);
		}
		return null;
	}


    protected Scan getScan(SpliceRuntimeContext ctx) throws StandardException {
        return scanInformation.getScan(getTransactionID(), ctx.getScanStartOverride());
    }

    @Override
	public int[] getRootAccessedCols(long tableNumber) {
        return operationInformation.getBaseColumnMap();
	}

    @Override
    public boolean isReferencingTable(long tableNumber){
        return tableName.equals(String.valueOf(tableNumber));
    }
    
    public FormatableBitSet getAccessedCols()  {
        try {
            return scanInformation.getAccessedColumns();
        } catch (StandardException e) {
            LOG.error(e);
            throw new RuntimeException(e);
        }
    }
    
    public Qualifier[][] getScanQualifiers()  {
        try {
            return scanInformation.getScanQualifiers();
        } catch (StandardException e) {
            SpliceLogUtils.logAndThrowRuntime(LOG,e);
            return null;
        }
    }

		@Override
		public String getName() {
				/*
				 * TODO -sf- tableName and indexName are actually conglomerate ids, not
				 * human-readable table names. Unfortunately, there doesn't appear
				 * to be any mechanism to get the human readable name short of
				 * issuing a query to find it (which isn't really an option). For now,
				 * we'll just set the conglomerate id on here, and then allow people
				 * to look for them later; at some point the Query Optimizer will
				 * need to be invoked to ensure that the human readable name gets
				 * passed through.
				 */
				String baseName =  super.getName();
				if(this.tableName!=null){
						baseName+="(table="+tableName+")";
				}else if(this.indexName!=null)
						baseName+="(index="+indexName+")";
				return baseName;
		}

		public String getTableName(){
    	return this.tableName;
    }
    
    public String getIndexName() {
    	return this.indexName;
    }
    
    @Override
    public void close() throws StandardException, IOException {
        SpliceLogUtils.trace(LOG, "closing");
        super.close();
    }
    
    public String printStartPosition() {
        try {
            return scanInformation.printStartPosition(numOpens);
        } catch (StandardException e) {
            throw new RuntimeException(e);
        }
    }

   	public String printStopPosition() {
           try {
               return scanInformation.printStopPosition(numOpens);
           } catch (StandardException e) {
               throw new RuntimeException(e);
           }
   	}

    @Override
    public String prettyPrint(int indentLevel) {
        String indent = "\n"+ Strings.repeat("\t",indentLevel);
        return new StringBuilder("Scan:")
                .append(indent).append("resultSetNumber:").append(resultSetNumber)
                .append(indent).append("scanInformation:").append(scanInformation)
                .append(indent).append("tableName:").append(tableName)
                .toString();
    }
}
