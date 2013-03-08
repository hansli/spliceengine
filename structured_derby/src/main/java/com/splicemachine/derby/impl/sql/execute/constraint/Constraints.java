package com.splicemachine.derby.impl.sql.execute.constraint;

import com.splicemachine.constants.HBaseConstants;
import com.splicemachine.derby.impl.sql.execute.index.PrimaryKey;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;

import java.io.IOException;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Scott Fines
 *         Created on: 2/28/13
 */
public class Constraints {
    private static final Constraint EMPTY_CONSTRAINT = new Constraint() {
        @Override
        public Type getType() {
            return Type.NONE;
        }

        @Override
        public boolean validate(Mutation mutation, RegionCoprocessorEnvironment rce) throws IOException {
            return true;
        }

        @Override
        public boolean validate(Collection<Mutation> mutations, RegionCoprocessorEnvironment rce) throws IOException {
            return true;
        }
    };

    private Constraints(){} //can't make me!

    public static byte[] getReferencedRowKey(Map<byte[],byte[]> dataMap,BitSet columns) throws IOException{
        //get the columns from the put
        byte[][] cols = new byte[columns.size()][];
        int size = 0;
        for(int fk=columns.nextSetBit(0),pos=0;fk!=-1;pos++,fk = columns.nextSetBit(fk+1)){
            byte[] value = dataMap.get(Integer.toString(fk).getBytes());
            if(value ==null){
                //the put is missing a value for the column, can't have that--validation fails right off
                return null;
            }
            cols[pos] = value;
            size+=value.length;
        }
        return constructCompositeKey(cols, size);
    }

    public static byte[] getReferencedRowKey(Put put, BitSet columns) throws IOException{
        //get the columns from the put
        byte[][] cols = new byte[columns.size()][];
        int size = 0;
        for(int fk=columns.nextSetBit(0),pos=0;fk!=-1;pos++,fk = columns.nextSetBit(fk+1)){
            List<KeyValue> values = put.get(HBaseConstants.DEFAULT_FAMILY_BYTES, Integer.toString(fk).getBytes());
            if(values ==null||values.isEmpty()){
                //the put is missing a value for the column, can't have that--validation fails right off
                return null;
            }
            cols[pos] = values.get(0).getBuffer();
            size+=cols[pos].length;
        }
        return constructCompositeKey(cols, size);
    }

    public static Constraint noConstraint(){
        return EMPTY_CONSTRAINT;
    }

     private static byte[] constructCompositeKey(byte[][] cols, int size) {
        byte[] finalRowKey = new byte[size];
        int offset=0;
        for(byte[] col:cols){
            System.arraycopy(col,0,finalRowKey,offset,col.length);
            offset+=col.length;
        }
        return finalRowKey;
    }
}
