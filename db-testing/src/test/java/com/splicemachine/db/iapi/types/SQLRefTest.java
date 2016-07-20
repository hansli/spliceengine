package com.splicemachine.db.iapi.types;

import org.apache.hadoop.hbase.util.*;
import org.apache.spark.sql.catalyst.expressions.UnsafeRow;
import org.apache.spark.sql.catalyst.expressions.codegen.BufferHolder;
import org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Test Class for SQLTinyint
 *
 */
public class SQLRefTest {

        @Test
        public void serdeValueData() throws Exception {
                UnsafeRowWriter writer = new UnsafeRowWriter();
                writer.initialize(new BufferHolder(),1);
                SQLRef value = new SQLRef(new SQLRowId("1".getBytes()));
                SQLRef valueA = new SQLRef();
                value.write(writer, 0);
                UnsafeRow row = new UnsafeRow();
                row.pointTo(writer.holder().buffer,1,writer.holder().cursor);
                valueA.read(row,0);
                Assert.assertEquals("SerdeIncorrect",(Object) new SQLRowId("1".getBytes()),(Object) valueA.getObject());
        }

        @Test
        public void serdeNullValueData() throws Exception {
                UnsafeRowWriter writer = new UnsafeRowWriter();
                writer.initialize(new BufferHolder(),1);
                SQLRef value = new SQLRef();
                SQLRef valueA = new SQLRef();
                value.write(writer, 0);
                UnsafeRow row = new UnsafeRow();
                row.pointTo(writer.holder().buffer,1,writer.holder().cursor);
                Assert.assertTrue("SerdeIncorrect", row.isNullAt(0));
                value.read(row, 0);
                Assert.assertTrue("SerdeIncorrect", valueA.isNull());
        }
    
        @Test
        public void serdeKeyData() throws Exception {
                SQLRef value1 = new SQLRef(new SQLRowId("1".getBytes()));
                SQLRef value2 = new SQLRef(new SQLRowId("2".getBytes()));
                SQLRef value1a = new SQLRef();
                SQLRef value2a = new SQLRef();
                PositionedByteRange range1 = new SimplePositionedMutableByteRange(value1.encodedKeyLength());
                PositionedByteRange range2 = new SimplePositionedMutableByteRange(value2.encodedKeyLength());
                value1.encodeIntoKey(range1, Order.ASCENDING);
                value2.encodeIntoKey(range2, Order.ASCENDING);
                Assert.assertTrue("Positioning is Incorrect", Bytes.compareTo(range1.getBytes(), 0, 9, range2.getBytes(), 0, 9) < 0);
                range1.setPosition(0);
                range2.setPosition(0);
                value1a.decodeFromKey(range1);
                value2a.decodeFromKey(range2);
                Assert.assertEquals("1 incorrect",value1.getObject(),value1a.getObject());
                Assert.assertEquals("2 incorrect",value2.getObject(),value2a.getObject());
        }
}