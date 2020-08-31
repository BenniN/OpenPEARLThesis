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

public class TypeArraySlice extends TypeDefinition {
    private TypeDefinition m_baseType;
    private ConstantFixedValue startIndex;
    private ConstantFixedValue endIndex;

    TypeArraySlice() {
        super("ARRAYSLICE");
        this.m_baseType = null;
    }

    TypeArraySlice(TypeArray type) {
        super("ARRAY");
        this.m_baseType = type;
    }


    public Void setBaseType(TypeDefinition type) {
        this.m_baseType = type;
        return null;
    }

    public TypeDefinition getBaseType() {
        return this.m_baseType;
    }


    public void setStartIndex(ConstantFixedValue startIndex) {
      this.startIndex = startIndex;
    }

    public void setEndIndex(ConstantFixedValue endIndex) {
      this.endIndex = endIndex;
    }
    
    public boolean hasConstantSize() {
      return startIndex != null && endIndex != null;
    }
    
    public long getTotalNoOfElements() {
      long result = 0;
      if (hasConstantSize()) {
        result = endIndex.getValue()-startIndex.getValue()+1;
      }
      return result;
    }

    public String toString() {
      String result = this.getName() + "(";
      if (startIndex!=null) {
        result += startIndex.getValue()+":";
      } else {
        result += "?:";
      }
      if (endIndex != null) {
        result += endIndex.getValue();
      } else {
        result += "?";
      }
      result += ") " + this.m_baseType;
      return result;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TypeArraySlice)) {
            return false;
        }

        TypeArraySlice that = (TypeArraySlice) other;

        // Custom equality check here.
        return this.m_baseType.equals(that.getBaseType());
    }
}