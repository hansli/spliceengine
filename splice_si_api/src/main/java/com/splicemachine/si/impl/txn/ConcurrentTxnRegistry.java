/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.si.impl.txn;

import com.splicemachine.si.api.txn.Txn;
import com.splicemachine.si.api.txn.TxnRegistry;

import javax.management.*;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author Scott Fines
 *         Date: 11/21/16
 */
public class ConcurrentTxnRegistry implements TxnRegistry,TxnRegistry.TxnRegistryView{
    private final NavigableSet<Txn> activeTxns =new ConcurrentSkipListSet<>((Txn o1,Txn o2)->{
        if(o1==null){
            if(o2==null) return 0;
            return -1;
        }else if(o2==null) return 1;
        return Long.compare(o1.getBeginTimestamp(),o2.getBeginTimestamp());
    });

    @Override
    public int activeTxnCount(){
        return activeTxns.size();
    }

    @Override
    public long minimumActiveTransactionId(){
        Txn first=activeTxns.first();
        if(first==null) return 0L;
        return first.getBeginTimestamp();
    }

    @Override public TxnRegistryView asView(){ return this; }

    @Override public int getActiveTxnCount(){ return activeTxnCount(); }
    @Override public long getMinimumActiveTransactionId(){ return minimumActiveTransactionId(); }

    @Override
    public void registerTxn(Txn txn){
        activeTxns.add(txn);
    }

    @Override
    public void deregisterTxn(Txn txn){
        activeTxns.remove(txn);
    }

    public void registerJmx(MBeanServer mbs) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException{
        ObjectName name = new ObjectName("com.splicemachine.si.txn:type=TxnRegistry.TxnRegistryView");
        mbs.registerMBean(this,name);
    }
}
