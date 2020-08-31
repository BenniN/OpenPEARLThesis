/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2016 Marcel Schaible
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

import org.antlr.v4.runtime.ParserRuleContext;


public class ArrayDimension  {
    private int m_lowerBoundary;
    private int m_upperBoundary;
    private ParserRuleContext m_ctx;

    ArrayDimension() {
        this.m_lowerBoundary = 0;
        this.m_upperBoundary = 0;
    }

    ArrayDimension(int lowerBoundary, int upperBoundary, ParserRuleContext ctx) {
        this.m_lowerBoundary = lowerBoundary;
        this.m_upperBoundary = upperBoundary;
        this.m_ctx = ctx;
    }

    public ParserRuleContext getCtx() {
    	return m_ctx;
    }
    
    public String toString() {
        return Integer.toString(this.m_lowerBoundary) + ":" + Integer.toString(this.m_upperBoundary);
    }

    public int getNoOfElements() {
        return m_upperBoundary - m_lowerBoundary + 1;
    }

    public int getLowerBoundary() { return this.m_lowerBoundary; }
    public int getUpperBoundary() { return this.m_upperBoundary; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ArrayDimension)) {
            return false;
        }

        ArrayDimension that = (ArrayDimension) other;

        // Custom equality check here.
        return this.m_lowerBoundary == that.m_lowerBoundary && this.m_upperBoundary == that.m_upperBoundary;
    }
}
