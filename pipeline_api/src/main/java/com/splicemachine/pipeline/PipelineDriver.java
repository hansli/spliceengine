package com.splicemachine.pipeline;

import com.splicemachine.access.api.PartitionFactory;
import com.splicemachine.access.api.SConfiguration;
import com.splicemachine.pipeline.api.BulkWriterFactory;
import com.splicemachine.pipeline.api.PipelineExceptionFactory;
import com.splicemachine.pipeline.api.PipelineMeter;
import com.splicemachine.pipeline.client.WriteCoordinator;
import com.splicemachine.pipeline.contextfactory.ContextFactoryDriver;
import com.splicemachine.pipeline.contextfactory.ContextFactoryLoader;
import com.splicemachine.pipeline.traffic.SpliceWriteControl;
import com.splicemachine.pipeline.utils.PipelineCompressor;

import javax.management.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Scott Fines
 *         Date: 12/23/15
 */
public class PipelineDriver{
    private static final int ipcReserved=10;
    private static PipelineDriver INSTANCE;

    private final SpliceWriteControl writeControl;
    private final MappedPipelineFactory writePipelineFactory=new MappedPipelineFactory();
    private final PipelineMeter pipelineMeter;
    private final PipelineWriter pipelineWriter;
    private final PipelineCompressor compressor;
    private final ActiveWriteHandlers handlerMeter = new ActiveWriteHandlers();
    private final WriteCoordinator writeCoordinator;
    private final PipelineExceptionFactory pef;
    private final ContextFactoryDriver ctxFactoryDriver;
    private final AtomicBoolean jmxRegistered = new AtomicBoolean(false);

    public static void loadDriver(PipelineEnvironment env){
        SConfiguration config = env.configuration();
        PipelineExceptionFactory pef = env.pipelineExceptionFactory();
        PartitionFactory partitionFactory = env.tableFactory();
        ContextFactoryDriver ctxFactoryDriver = env.contextFactoryDriver();
        PipelineCompressor compressor = env.pipelineCompressor();
        BulkWriterFactory writerFactory = env.writerFactory();
        PipelineMeter meter = env.pipelineMeter();

        INSTANCE = new PipelineDriver(config,ctxFactoryDriver,pef,partitionFactory,compressor,writerFactory,meter);
        writerFactory.setWriter(INSTANCE.pipelineWriter);
    }

    public static PipelineDriver driver(){ return INSTANCE; }

    private PipelineDriver(SConfiguration config,
                           ContextFactoryDriver ctxFactoryDriver,
                           PipelineExceptionFactory pef,
                           PartitionFactory partitionFactory,
                           PipelineCompressor compressor,
                           BulkWriterFactory channelFactory,
                           PipelineMeter meter){
        this.ctxFactoryDriver = ctxFactoryDriver;
        this.pef = pef;
        this.compressor = compressor;
        this.pipelineMeter= meter;

        int ipcThreads = config.getInt(PipelineConfiguration.IPC_THREADS);
        int maxIndependentWrites = config.getInt(PipelineConfiguration.MAX_INDEPENDENT_WRITES);
        int maxDependentWrites = config.getInt(PipelineConfiguration.MAX_DEPENDENT_WRITES);

        this.writeControl= new SpliceWriteControl(ipcThreads/2,ipcThreads/2,maxDependentWrites,maxIndependentWrites);
        this.pipelineWriter = new PipelineWriter(pef, writePipelineFactory,writeControl);
        channelFactory.setWriter(pipelineWriter);
        channelFactory.setPipeline(writePipelineFactory);
//        CoprocessorWriterFactory writerFactory=new CoprocessorWriterFactory(pipelineWriter,
//                writePipelineFactory,
//                compressor,
//                partitionInfoCache,
//                pef,
//                channelFactory);
        try{
            this.writeCoordinator=WriteCoordinator.create(config,channelFactory,pef,partitionFactory);
            pipelineWriter.setWriteCoordinator(writeCoordinator);
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public PipelineCompressor compressor(){
        return compressor;
    }

    public PipelineWriter writer(){
        return pipelineWriter;
    }

    public PipelineMeter meter(){
        return pipelineMeter;
    }

    public WriteCoordinator writeCoordinator(){
        return writeCoordinator;
    }

    public PipelineExceptionFactory exceptionFactory(){
        return pef;
    }

    public ContextFactoryLoader getContextFactoryLoader(long conglomId){
        return ctxFactoryDriver.getLoader(conglomId);
    }

    public void registerJMX(MBeanServer mbs) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException{
        if(jmxRegistered.compareAndSet(false,true)){
            ObjectName coordinatorName=new ObjectName("com.splicemachine.derby.hbase:type=ActiveWriteHandlers");
            mbs.registerMBean(handlerMeter,coordinatorName);
        }
    }

    public void registerPipeline(String name,PartitionWritePipeline writePipeline){
        writePipelineFactory.registerPipeline(name,writePipeline);
    }

    public void deregisterPipeline(String partitionName){
        writePipelineFactory.deregisterPipeline(partitionName);
    }

    @MXBean
    @SuppressWarnings("UnusedDeclaration")
    public interface ActiveWriteHandlersIface{
        int getIpcReservedPool();
        int getIndependentWriteThreads();
        int getIndependentWriteCount();
        int getDependentWriteThreads();
        int getDependentWriteCount();
        void setMaxDependentWriteCount(int newMaxDependentWriteCount);
        void setMaxIndependentWriteCount(int newMaxIndependentWriteCount);
        void setMaxDependentWriteThreads(int newMaxDependentWriteThreads);
        void setMaxIndependentWriteThreads(int newMaxIndependentWriteThreads);
        int getMaxIndependentWriteCount();
        int getMaxDependentWriteCount();
        int getMaxIndependentWriteThreads();
        int getMaxDependentWriteThreads();
        double getOverallAvgThroughput();
        double get1MThroughput();
        double get5MThroughput();
        double get15MThroughput();
        long getTotalRejected();
    }


    public class ActiveWriteHandlers implements ActiveWriteHandlersIface{

        private ActiveWriteHandlers(){ }

        @Override public int getIpcReservedPool(){ return ipcReserved; }
        @Override public int getMaxDependentWriteThreads(){ return writeControl.maxDependendentWriteThreads(); }
        @Override public int getMaxIndependentWriteThreads(){ return writeControl.maxIndependentWriteThreads(); }
        @Override public int getMaxDependentWriteCount(){ return writeControl.maxDependentWriteCount(); }
        @Override public int getMaxIndependentWriteCount(){ return writeControl.maxIndependentWriteCount(); }
        @Override public double getOverallAvgThroughput(){ return pipelineMeter.throughput(); }
        @Override public double get1MThroughput(){ return pipelineMeter.oneMThroughput(); }
        @Override public double get5MThroughput(){ return pipelineMeter.fiveMThroughput(); }
        @Override public double get15MThroughput(){ return pipelineMeter.fifteenMThroughput(); }
        @Override public long getTotalRejected(){ return pipelineMeter.rejectedCount(); }

        @Override
        public void setMaxIndependentWriteThreads(int newMaxIndependentWriteThreads){
            writeControl.setMaxIndependentWriteThreads(newMaxIndependentWriteThreads);
        }

        @Override
        public void setMaxDependentWriteThreads(int newMaxDependentWriteThreads){
            writeControl.setMaxDependentWriteThreads(newMaxDependentWriteThreads);
        }

        @Override
        public void setMaxIndependentWriteCount(int newMaxIndependentWriteCount){
            writeControl.setMaxIndependentWriteCount(newMaxIndependentWriteCount);
        }

        @Override
        public void setMaxDependentWriteCount(int newMaxDependentWriteCount){
            writeControl.setMaxDependentWriteCount(newMaxDependentWriteCount);
        }

        @Override
        public int getDependentWriteCount(){
            return writeControl.getWriteStatus().get().getDependentWriteCount();
        }

        @Override
        public int getDependentWriteThreads(){
            return writeControl.getWriteStatus().get().getDependentWriteThreads();
        }

        @Override
        public int getIndependentWriteCount(){
            return writeControl.getWriteStatus().get().getIndependentWriteCount();
        }

        @Override
        public int getIndependentWriteThreads(){
            return writeControl.getWriteStatus().get().getIndependentWriteThreads();
        }

    }
}
