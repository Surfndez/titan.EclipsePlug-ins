/******************************************************************************
 * Copyright (c) 2000-2019 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.log.viewer.parsers.token;

import org.eclipse.titan.log.viewer.parsers.Constants;

/**
 * Sets the type EOR (End of record)
 */
public class EOR extends Token {

	/**
	 * Constructor
	 * @param token the token
	 */
	public EOR(final String token) {
		super(token);
	}

	@Override
	public int getType() {
		return Constants.END_OF_RECORD;
	}

}
