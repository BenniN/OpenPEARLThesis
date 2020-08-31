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

/*
 * 2020-04-07: rm : m_hasAssignmentProtection added
 */
package org.smallpearl.compiler;

import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

public abstract class TypeDefinition {
    private String m_name;
    private boolean m_hasAssignmentProtection;
    

    public boolean hasAssignmentProtection() {
      return m_hasAssignmentProtection;
    }

    public void setHasAssignmentProtection(boolean m_hasAssignmentProtection) {
      this.m_hasAssignmentProtection = m_hasAssignmentProtection;
    }

    public TypeDefinition() {
        this.m_name = null;
        this.m_hasAssignmentProtection = false;
    }

    public TypeDefinition(String name) {
        this.m_name = name;
        this.m_hasAssignmentProtection = false;
    }

    public Void setName(String name) {
        this.m_name = name;
        return null;
    }

    public String getName() {
        if (this.m_name == null) {
            return "~NO NAME~";
        } else {
            return this.m_name;
        }
    }

    public String toString() {
      String s="";
      if (m_hasAssignmentProtection) {
        s += "INV ";
      }
      s += this.getName();
        return s;
    }

    public ST toST(STGroup group) {
        return null;
    }

    public Integer getPrecision() { return 0;}

    public int getNoOfBytes() { return 0;}
}
