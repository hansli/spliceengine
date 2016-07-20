package com.splicemachine.db.iapi.types;

import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Order;
import org.apache.hadoop.hbase.util.PositionedByteRange;
import org.apache.hadoop.hbase.util.SimplePositionedMutableByteRange;
import org.apache.spark.sql.catalyst.expressions.UnsafeRow;
import org.apache.spark.sql.catalyst.expressions.codegen.BufferHolder;
import org.apache.spark.sql.catalyst.expressions.codegen.UnsafeRowWriter;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Date;
import java.sql.Time;
import java.util.GregorianCalendar;

/**
 *
 * Test Class for SQLTime
 *
 */
public class SQLTimeTest {

        @Test
        public void serdeValueData() throws Exception {
                UnsafeRowWriter writer = new UnsafeRowWriter();
                writer.initialize(new BufferHolder(),1);
                Time time = new Time(System.currentTimeMillis());
                SQLTime value = new SQLTime(time);
                SQLTime valueA = new SQLTime();
                value.write(writer, 0);
                UnsafeRow row = new UnsafeRow();
                row.pointTo(writer.holder().buffer,1,writer.holder().cursor);
                valueA.read(row,0);
                Assert.assertEquals("SerdeIncorrect",time.toString(),valueA.getTime(new GregorianCalendar()).toString());
            }

        @Test
        public void serdeNullValueData() throws Exception {
                UnsafeRowWriter writer = new UnsafeRowWriter();
                writer.initialize(new BufferHolder(),1);
                Time time = new Time(System.currentTimeMillis());
                SQLTime value = new SQLTime(time);
                SQLTime valueA = new SQLTime();
                value.write(writer, 0);
                UnsafeRow row = new UnsafeRow();
                row.pointTo(writer.holder().buffer,1,writer.holder().cursor);
                Assert.assertTrue("SerdeIncorrect", valueA.isNull());
            }
    
        @Test
        public void serdeKeyData() throws Exception {
                GregorianCalendar gc = new GregorianCalendar();
                long currentTimeMillis = System.currentTimeMillis();
                SQLTime value1 = new SQLTime(new Time(currentTimeMillis));
                SQLTime value2 = new SQLTime(new Time(currentTimeMillis+200));
                SQLTime value1a = new SQLTime();
                SQLTime value2a = new SQLTime();
                PositionedByteRange range1 = new SimplePositionedMutableByteRange(value1.encodedKeyLength());
                PositionedByteRange range2 = new SimplePositionedMutableByteRange(value2.encodedKeyLength());
                value1.encodeIntoKey(range1, Order.ASCENDING);
                value2.encodeIntoKey(range2, Order.ASCENDING);
                range1.setPosition(0);
                range2.setPosition(0);
                value1a.decodeFromKey(range1);
                value2a.decodeFromKey(range2);
                Assert.assertEquals("1 incorrect",value1.getTime(gc),value1a.getTime(gc));
                Assert.assertEquals("2 incorrect",value2.getTime(gc),value2a.getTime(gc));
        }
}