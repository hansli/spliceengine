package com.splicemachine.si.testenv;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author Scott Fines
 *         Date: 12/16/15
 */
public class SITestEnvironment{
    private static volatile SITestEnv testEnv;
    private static volatile SITestDataEnv testDataEnv;

    public static SITestDataEnv loadTestDataEnvironment(){
        SITestDataEnv env = testDataEnv;
        if(env==null){
            env = testEnv;
            if(env==null){
                env = initializeDataEnvironment();
            }
        }
        return env;
    }


    public static SITestEnv loadTestEnvironment(){
        if(testEnv==null)
            initializeFullEnvironment();

        return testEnv;
    }

    private static void initializeFullEnvironment(){
        synchronized(SITestEnvironment.class){
            if(testEnv==null){
                ServiceLoader<SITestEnv> load=ServiceLoader.load(SITestEnv.class);
                Iterator<SITestEnv> iter=load.iterator();
                if(!iter.hasNext())
                    throw new IllegalStateException("No SITestEnv found!");
                testDataEnv = testEnv = iter.next();
                if(iter.hasNext())
                    throw new IllegalStateException("Only one SITestEnv is allowed!");
            }
        }
    }

    private static SITestDataEnv initializeDataEnvironment(){
        synchronized(SITestEnvironment.class){
            if(testDataEnv==null){
               ServiceLoader<SITestDataEnv> load = ServiceLoader.load(SITestDataEnv.class);
                Iterator<SITestDataEnv> iter=load.iterator();
                if(!iter.hasNext())
                    throw new IllegalStateException("No SITestEnv found!");
                testDataEnv = iter.next();
                if(iter.hasNext())
                    throw new IllegalStateException("Only one SITestEnv is allowed!");
            }
        }
        return testDataEnv;
    }

}
