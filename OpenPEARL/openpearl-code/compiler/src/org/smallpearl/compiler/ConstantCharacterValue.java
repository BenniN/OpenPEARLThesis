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

import org.smallpearl.compiler.Exception.InternalCompilerErrorException;

import java.io.UnsupportedEncodingException;

public class ConstantCharacterValue  extends ConstantValue {
    private String m_value;
    private String m_uuid;
    private int length;
    /**
     * 
     * @param str as written in the PEARL-Application including escaped control characters
     */
    ConstantCharacterValue(String str) {
        str = CommonUtils.removeQuotes(str);
        m_value = CommonUtils.compressPearlString(str);
        length = CommonUtils.getStringLength(m_value);
        m_uuid = CommonUtils.getUUIDString();
    }

    public int getLength() {
      return length;
    }

    public String getValue() {
        return m_value;
    }

    public String getBaseType() {
        return "Character";
    }

    public String toString() {
        String name = "CONST_" + getBaseType().toUpperCase();
        name += "_" + m_uuid;
        return name;
    }

    public String canonicalize(String str) {
        String res = "";

        if( str.startsWith("'")) {
            str = str.substring(1, str.length());
        }

        if( str.endsWith("'")) {
            str = str.substring(0, str.length() - 1);
        }

        for ( int i = 0; i < str.length(); i++) {
            Character ch = str.charAt(i);

            if ( !(( ch >= 'a' && ch <= 'z') || ( ch >= 'A' && ch <= 'Z' ) || ( ch >= '0' && ch <= '9'))) {
                ch = '_';
            }

            res += String.valueOf(ch);

        }

        return res;
    }
}