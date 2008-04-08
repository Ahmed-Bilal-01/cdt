/*********************************************************************************
 * Copyright (c) 2008 IBM Corporation. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is 
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * David Dykstal (IBM) - [225988] need API to mark persisted profiles as migrated
 *********************************************************************************/

package org.eclipse.rse.core;

/**
 * Codes for use in constructing IStatus objects.
 * These are unique across org.eclipse.rse.core 
 * @since 3.0
 */
public interface IRSECoreStatusCodes {

	/*
	 * General codes (1 to 100)
	 */

	/**
	 * A code used for constructing IStatus objects.
	 * Value 1. An exception occurred during the operation.   
	 * @since 3.0
	 */
	public static final int EXCEPTION_OCCURRED = 1;
	
	/**
	 * A code used for constructing IStatus objects.
	 * Value 2. An invalid format was encountered operation.
	 * The object in question must be assumed to be corrupted.
	 * @since 3.0
	 */
	public static final int INVALID_FORMAT = 2;

	/*
	 * Persistence manager and provider codes (101 to 200)
	 */

	/**
	 * A code used for constructing IStatus objects.
	 * Value 101. A persistent form of a profile is not found.
	 * @since 3.0
	 */
	public static final int PROFILE_NOT_FOUND = 101;

	/**
	 * A code used for constructing IStatus objects.
	 * Value 102.
	 * The marking of profiles as migrated is not supported by this provider.   
	 * @since 3.0
	 */
	public static final int MIGRATION_NOT_SUPPORTED = 102;
	
}
