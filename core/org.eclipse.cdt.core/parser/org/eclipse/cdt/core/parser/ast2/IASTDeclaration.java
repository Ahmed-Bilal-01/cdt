/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.cdt.core.parser.ast2;

/**
 * Introduces a type, function, or variable name into scope. This
 * interface is extended for each one of these.
 * 
 * @author Doug Schaefer
 */
public interface IASTDeclaration extends IASTNode {

	/**
	 * @return the scope for the declaration
	 */
	public IASTScope getScope();

	/**
	 * @return the identifier being introduced into the scope
	 */
	public IASTIdentifier getName();

	/**
	 * @return the next declaration in this scope
	 */
	public IASTDeclaration getNextDeclaration();
	
}
