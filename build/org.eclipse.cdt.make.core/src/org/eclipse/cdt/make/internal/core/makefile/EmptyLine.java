
/**********************************************************************
 * Copyright (c) 2002,2003 QNX Software Systems and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
***********************************************************************/
package org.eclipse.cdt.make.internal.core.makefile;

import org.eclipse.cdt.make.core.makefile.IEmptyLine;

public class EmptyLine extends Directive implements IEmptyLine {

	final public static char NL = '\n';
	final public static String NL_STRING = "\n";

	public EmptyLine(Directive parent) {
		super(parent);
	}

	public String toString() {
		return NL_STRING;
	}

	public boolean equals(IEmptyLine stmt) {
		return true;
	}
}
