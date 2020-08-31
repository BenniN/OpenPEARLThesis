/*
 * [A "BSD license"]
 *  Copyright (c) 2019 Rainer Mueller
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.smallpearl.compiler;

import org.antlr.v4.runtime.*;

/**
 * container for the current environment of a sematic check
 * 
 * The container is used in the ErrorStack.
 */
public class ErrorEnvironment {

	private String m_errorPrefix;
	private ParserRuleContext m_ctx;
	private long m_localCount = 0;

	/**
	 * create a new ErrorEnvironment
	 * 
	 * @param ctx
	 *            the current ParserRuleContext of the analyzed part. This is
	 *            used for the formating of the error mesage
	 * @param errorPrefix
	 *            a short identifier of the current checked element
	 */
	public ErrorEnvironment(ParserRuleContext ctx, String errorPrefix) {
		m_ctx = ctx;
		m_errorPrefix = errorPrefix;
	}

	/**
	 * @return the number of detected errors in this element
	 */
	public long getLocalCount() {
		return m_localCount;
	}

	/**
	 * increment the number of detected errors in this element
	 */
	public Void incLocalCount() {
		m_localCount++;
		return null;
	}

	/**
	 * reset the local error count.
	 * 
	 * This is done when the ErrorEnvironment is entered via the error stack.
	 * Thus a errorEnvironment may reused.
	 */
	public Void resetLocalCount() {
		m_localCount = 0;
		return null;
	}

	/**
	 * @return the current identification text of the current element
	 */
	public String getErrorPrefix() {
		return m_errorPrefix;
	}

	/**
	 * @return the current ParserRuleContext
	 */
	public ParserRuleContext getCtx() {
		return m_ctx;
	}

}
