/*
 * [The "BSD license"]
 *  Copyright (c) 2018-2020 Marcel Schaible
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
 *     derived from this software without specific prior written permissision.
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

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;

public class ArrayOrStructureInitializer extends Initializer {
    private ArrayList<Initializer> m_initElementList = null;

    public ArrayOrStructureInitializer(ParserRuleContext ctx, ArrayList<Initializer> initElementList) {
	    	super(ctx);
	    	m_initElementList = initElementList;
	    }

	public ParserRuleContext getContext() { return m_context; }
	public ArrayList<Initializer> getInitElementList() { return m_initElementList; }

	public String toString() {
    	if ( m_initElementList != null) {
    		String str = "";
    		for ( int i = 0; i < m_initElementList.size(); i++) {
    			if (i > 0) {
    				str +=",";
				}
    			str += m_initElementList.get(i).toString();
			}

			return str;
		}
    	else {
    		return "nil";
		}
	}


}
