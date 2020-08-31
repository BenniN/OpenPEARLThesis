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

import java.util.LinkedList;
import org.smallpearl.compiler.SymbolTable.FormalParameter;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

public class TypeProcedure extends TypeDefinition {
  private LinkedList<FormalParameter> m_formalParameters;
  private TypeDefinition m_resultType;
  
    TypeProcedure() {
        super("PROC");
        m_formalParameters = null;
        m_resultType = null;
    }

    public TypeProcedure(LinkedList<FormalParameter> formalParameters, TypeDefinition resultType) {
      super("PROC");
      m_formalParameters = formalParameters;
      m_resultType = resultType;
    }

    public String toString() {
      String s = this.getName();
      if (m_formalParameters != null && m_formalParameters.size()> 0) {
        s += " ("+m_formalParameters.get(0).toString();
        for (int i=1; i<m_formalParameters.size(); i++) {
          s += ", "+m_formalParameters.get(i).toString();
        }
        s += ")";
      }
      if (m_resultType != null) {
        s += " RETURNS ("+m_resultType.toString()+")";
      }

      return s;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TypeProcedure)) {
            return false;
        }

        TypeProcedure that = (TypeProcedure) other;

        if ((this.m_resultType != null) != (that.m_resultType != null)) return false;
        if (this.m_resultType != null && (!this.m_resultType.equals(that.m_resultType))) return false;
        if ((this.m_formalParameters != null) != (that.m_formalParameters != null)) return false;
        if (this.m_formalParameters == null) return true;
        if (this.m_formalParameters.size()  != that.m_formalParameters.size() ) return false;
        for (int i=0; i<this.m_formalParameters.size(); i++) {
          if (!this.m_formalParameters.get(i).getType().equals(that.m_formalParameters.get(i).getType())) return false;
        }
        
        return true;
    }
    
    public ST toST(STGroup group) {
      ST st = group.getInstanceOf("TypeProcedure");
      if (m_resultType!= null) {
        st.add("resultAttribute", m_resultType.toST(group));
      }
      ST parameters = group.getInstanceOf("ListOfFormalParameters");
//      parameters.add("FormalParameters", group.getInstanceOf("TypeReferenceTaskType"));
      if (m_formalParameters!= null) {
        for (int i=0; i<m_formalParameters.size(); i++) {
          parameters.add("FormalParameters", m_formalParameters.get(i).getType().toST(group));
        }
  
        st.add("listOfFormalParameters", parameters);
      }
      return st;
  }

    public TypeDefinition getResultType() {
      return m_resultType;
    }
    
    public LinkedList<FormalParameter> getFormalParameters() {
      return m_formalParameters;
    }
}
