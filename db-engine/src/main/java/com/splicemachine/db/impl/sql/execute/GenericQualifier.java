/*
 * This file is part of Splice Machine.
 * Splice Machine is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3, or (at your option) any later version.
 * Splice Machine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with Splice Machine.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 * Some parts of this source code are based on Apache Derby, and the following notices apply to
 * Apache Derby:
 *
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified the Apache Derby code in this file.
 *
 * All such Splice Machine modifications are Copyright 2012 - 2017 Splice Machine, Inc.,
 * and are licensed to you under the GNU Affero General Public License.
 */

package com.splicemachine.db.impl.sql.execute;

import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.store.access.Qualifier;
import com.splicemachine.db.iapi.types.DataValueDescriptor;
import com.splicemachine.db.iapi.services.loader.GeneratedMethod;
import com.splicemachine.db.iapi.services.sanity.SanityManager;

/**
 *	This is the implementation for Qualifier.  It is used for generated scans.
 *
 */

public class GenericQualifier implements Qualifier
{
	private int columnId;
    private int storagePosition;
	private int operator;
	private GeneratedMethod orderableGetter;
	private Activation	activation;
	private boolean orderedNulls;
	private boolean unknownRV;
	private boolean negateCompareResult;
	protected int variantType;
    protected String text;

	private DataValueDescriptor orderableCache = null;

    public GenericQualifier(int columnId,
                            int storagePosition,
                            int operator,
                            GeneratedMethod orderableGetter,
                            Activation activation,
                            boolean orderedNulls,
                            boolean unknownRV,
                            boolean negateCompareResult,
                            int variantType)
    {
        this.columnId = columnId;
        this.storagePosition = storagePosition;
        this.operator = operator;
        this.orderableGetter = orderableGetter;
        this.activation = activation;
        this.orderedNulls = orderedNulls;
        this.unknownRV = unknownRV;
        this.negateCompareResult = negateCompareResult;
        this.variantType = variantType;
    }

	public GenericQualifier(int columnId,
                            int storagePosition,
							int operator,
							GeneratedMethod orderableGetter,
							Activation activation,
							boolean orderedNulls,
							boolean unknownRV,
							boolean negateCompareResult,
							int variantType,
                            String text)
	{
		this.columnId = columnId;
        this.storagePosition = storagePosition;
		this.operator = operator;
		this.orderableGetter = orderableGetter;
		this.activation = activation;
		this.orderedNulls = orderedNulls;
		this.unknownRV = unknownRV;
		this.negateCompareResult = negateCompareResult;
		this.variantType = variantType;
        this.text = text;
	}

	/* 
	 * Qualifier interface
	 */

    @Override
    public String getText() {
        return text;
    }
	/** 
	 * @see Qualifier#getColumnId
	 */
	public int getColumnId()
	{
		return columnId;
	}

    public int getStoragePosition()
    {
        return storagePosition;
    }

    /**
	 * @see Qualifier#getOrderable
	 *
	 * @exception StandardException		Thrown on error
	 */
	public DataValueDescriptor getOrderable() throws StandardException {
		if (variantType != VARIANT) {
			if (orderableCache == null) {
				try {
					orderableCache = (DataValueDescriptor) (orderableGetter.invoke(activation));
				} catch (Exception e) {
					throw StandardException.unexpectedUserException(e);
				}
			}
			return orderableCache;
		}
		try {
			return (DataValueDescriptor) (orderableGetter.invoke(activation));
		} catch (Exception e) {
			throw StandardException.unexpectedUserException(e);
		}
	}

	/** Get the operator to use in the comparison. 
     *
     *  @see Qualifier#getOperator
     **/
	public int getOperator()
	{
		return operator;
	}

	/** Should the result from the compare operation be negated?  If true
     *  then only rows which fail the compare operation will qualify.
     *
     *  @see Qualifier#negateCompareResult
     **/
	public boolean negateCompareResult()
	{
		return negateCompareResult;
	}

	/** Get the getOrderedNulls argument to use in the comparison. 
     *  
     *  @see Qualifier#getOrderedNulls
     **/
    public boolean getOrderedNulls()
	{
		return orderedNulls;
	}

	/** Get the getOrderedNulls argument to use in the comparison.
     *  
     *  @see Qualifier#getUnknownRV
     **/
    public boolean getUnknownRV()
	{
		return unknownRV;
	}

	/** Clear the DataValueDescriptor cache, if one exists.
	 *  (The DataValueDescriptor can be 1 of 3 types:
	 *		o  VARIANT		  - cannot be cached as its value can 
	 *							vary within a scan
	 *		o  SCAN_INVARIANT - can be cached within a scan as its
	 *							value will not change within a scan
	 *		o  QUERY_INVARIANT- can be cached across the life of the query
	 *							as its value will never change
	 *		o  CONSTANT		  - never changes
     *  
     *  @see Qualifier#getUnknownRV
	 */
	public void clearOrderableCache() {
		if ((variantType == SCAN_INVARIANT) || (variantType == VARIANT)) {
			orderableCache = null;
		}
	}
	
	/** 
	 * This method reinitializes all the state of
	 * the Qualifier.  It is used to distinguish between
	 * resetting something that is query invariant
	 * and something that is constant over every
	 * execution of a query.  Basically, clearOrderableCache()
	 * will only clear out its cache if it is a VARIANT
	 * or SCAN_INVARIANT value.  However, each time a
	 * query is executed, the QUERY_INVARIANT qualifiers need
	 * to be reset.
	 */
	public void reinitialize()
	{
		if (variantType != CONSTANT)
		{
			orderableCache = null;
		}
	}

	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			return "columnId: "+columnId+
                "\nstoragePosition: "+storagePosition+
				"\noperator: "+operator+
				"\norderedNulls: "+orderedNulls+
				"\nunknownRV: "+unknownRV+
				"\nnegateCompareResult: "+negateCompareResult;
		}
		else
		{
			return "";
		}
	}

	@Override
	public int getVariantType() {
		return variantType;
	}
}
