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

import org.smallpearl.compiler.SymbolTable.SymbolTableEntry;
import org.smallpearl.compiler.SymbolTable.VariableEntry;

/*
 * change for support of multiple flags
 * boolean m_readOnly replaced by 
 * int m_flags
 * 
 * all flags are cleared at creation of an ASTAttribute
 * the individual flags are defined as static final in bitReadOnly, ..
 * they are set and cleared via bit operations in the m_flags attribute
 */
public class ASTAttribute {
    public TypeDefinition  m_type;
    //public boolean m_readonly;
    private int m_flags=0;
 //   public VariableEntry m_variable;
    private SymbolTableEntry m_entry;
    public ConstantValue m_constant;
    public ConstantSelection m_selection;
    private static final int bitReadOnly = 0x01;
    private static final int bitIsFunctionCall = 0x02;
    
    public ASTAttribute(TypeDefinition type) {
        m_type = type;
      //  m_readonly = false;
        m_flags = 0;
     //   m_variable = null;
        m_entry = null;
        m_constant = null;
        m_selection    = null;
    }

    ASTAttribute(ConstantSelection slice) {
        m_type     = null;
       // m_readonly = false;
        m_flags = 0;
      //  m_variable = null;
        m_entry=null;
        m_constant = null;
        m_selection    = slice;
    }

    ASTAttribute(TypeDefinition type, boolean constant) {
        m_type = type;
       // m_readonly = constant;
        setReadOnly(constant);
        //m_variable = null;
        m_entry = null;
        m_selection    = null;
    }

    ASTAttribute(TypeDefinition type, boolean constant, VariableEntry variable ) {
        m_type = type;
        //m_variable = variable;
        m_entry=variable;
        m_constant = null;
        m_selection    = null;

        if ( variable.getLoopControlVariable()) {
            //m_readonly = false;
          setReadOnly(false);
        }
        else {
            //m_readonly = constant;
          setReadOnly(constant);
        }
    }
    ASTAttribute(TypeDefinition type, SymbolTableEntry entry ) {
      m_type = type;
      //m_variable = variable;
      m_entry=entry;
      m_constant = null;
      m_selection    = null;

      if (getVariable()== null || getVariable().getLoopControlVariable()) {
        setReadOnly(false);
//          m_readonly = false;
      }
   
  }

    /**
     * indicate whether the element is a constant or an expression of constants
     * 
     * @return
     */
    public boolean isReadOnly() {
      return getFlag(bitReadOnly);
//        return this.m_readonly;
    }
    
    public void setReadOnly(boolean newValue) {
      setFlag(bitReadOnly, newValue);
    }

    public boolean isLoopControlVariable() {
      //  return ( m_variable != null && m_variable.getLoopControlVariable());
      return m_entry != null && getVariable() != null && getVariable().getLoopControlVariable();
    }


    public boolean isWritable() { return !this.isReadOnly(); }
    public TypeDefinition getType() { return this.m_type; }
    public VariableEntry getVariable() {
//    return this.m_variable; }
      if (m_entry instanceof VariableEntry) {
        return (VariableEntry)m_entry;
      }
      return null;
    }
    
    public SymbolTableEntry getSymbolTableEntry() {
      return m_entry;
    }

    public ConstantValue getConstant() { return this.m_constant; }

    public Void setConstant(ConstantValue val) {
        m_constant = val;
        //m_readonly = true;
        setReadOnly(true);
        return null;
    }

    public Void setConstantFixedValue(ConstantFixedValue val) {
        m_constant = val;
        //m_readonly = true;
        setReadOnly(true);
        return null;
    }

    public ConstantFixedValue getConstantFixedValue() { 
        if (m_constant instanceof ConstantFixedValue) {
            return (ConstantFixedValue) this.m_constant;
        } else {
            return null;
        }
    }

    public Void setConstantFloatValue(ConstantFloatValue val) {
        m_constant = val;
        //m_readonly = true;
        setReadOnly(true);
        return null;
    }

    public ConstantFloatValue getConstantFloatValue() {
        if (m_constant instanceof ConstantFloatValue) {
            return (ConstantFloatValue) this.m_constant;
        } else {
            return null;
        }
    }

    public Void setConstantDurationValue(ConstantDurationValue val) {
        m_constant = val;
        //m_readonly = true;
        setReadOnly(true);
        return null;
    }

    public ConstantDurationValue getConstantDurationValue() {
        if (m_constant instanceof ConstantDurationValue) {
            return (ConstantDurationValue) this.m_constant;
        } else {
            return null;
        }
    }


    public void setConstantSelection(ConstantSelection m_slice) {
      this.m_selection = m_slice;
    }
    
    public ConstantSelection getConstantSelection() {
        return this.m_selection;
    }

    public String toString() {
//        return "(" + this.m_type + " " + this.isReadOnly() + " " + this.m_variable + " " + this.m_constant + " " + this.m_selection + ")";
      return "(" + this.m_type + " " + this.isReadOnly() + " " + this.m_entry + " " + this.m_constant + " " + this.m_selection + ")";
    }

    public void setVariable(VariableEntry ve) {
      //this.m_variable = ve;
      m_entry = ve;
    }

    public void setIsFunctionCall(boolean newValue) {
      setFlag(bitIsFunctionCall, newValue);
    }
    
    public boolean isFunctionCall() {
      return getFlag(bitIsFunctionCall);
    }

    private boolean getFlag(int whichFlag) {
      return ((m_flags & whichFlag) == whichFlag);

    }

    private void setFlag(int whichFlag, boolean set) {
      if (set) {
        m_flags = m_flags | whichFlag;
      } else {
        m_flags = m_flags & ~whichFlag;
      }
    }

}
