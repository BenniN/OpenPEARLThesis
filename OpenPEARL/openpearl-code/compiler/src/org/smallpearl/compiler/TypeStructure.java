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


import org.smallpearl.compiler.Exception.NotYetImplementedException;
import org.smallpearl.compiler.SymbolTable.SemaphoreEntry;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

import java.util.ArrayList;
import java.util.LinkedList;

public class TypeStructure extends TypeDefinition {
    public LinkedList<StructureComponent> m_listOfComponents;

    TypeStructure() {
        super("STRUCT");
        m_listOfComponents = new LinkedList<StructureComponent>();
    }

    public String toString() {
        String line = this.getName() + " [ ";

        for (int i = 0; i < m_listOfComponents.size(); i++) {
            String prefix = " ";
            if ( i > 0 ) {
                prefix = ",";
            }

            line += prefix + m_listOfComponents.get(i).toString();
        }

        return line + " ] ";
    }

    public int noOfEntries() {
        return this.m_listOfComponents.size();
    }

    public boolean add(StructureComponent component) {
        m_listOfComponents.add(component);
        component.m_index = m_listOfComponents.size() - 1;
        component.m_alias = "m"+component.m_index;
        return true;
    }

    public boolean add(TypeStructure typeStructure) {
        StructureComponent component = new StructureComponent();
        component.m_type = typeStructure;
        m_listOfComponents.add(component);
        component.m_index = m_listOfComponents.size() - 1;
        component.m_alias = "m"+component.m_index;
        return true;
    }

    /*
            Datatype      letter   REF
            --------------------------
            FIXED         A        a
            FLOAT         B        b
            BIT           C        c
            CHARACTER     D        d
            CLOCK         E        e
            DURATION      F        f
            TASK                   g
            PROC                   h
            SEMA          I        i
            BOLT          J        j
            STRUCT        S        s
 */

    private String getDataTypeEncoding(TypeDefinition type) {
        if ( type instanceof TypeFixed)           return "A" + type.getPrecision().toString();
        if ( type instanceof TypeFloat)           return "B" + type.getPrecision().toString();
        if ( type instanceof TypeBit)             return "C" + type.getPrecision().toString();
        if ( type instanceof TypeChar)            return "D" + type.getPrecision().toString();
        if ( type instanceof TypeClock)           return "E" + type.getPrecision().toString();
        if ( type instanceof TypeDuration)        return "F" + type.getPrecision().toString();
        if ( type instanceof TypeSemaphore)       return "I" + type.getPrecision().toString();
        if ( type instanceof TypeBolt)            return "J" + type.getPrecision().toString();
        if ( type instanceof TypeStructure)       return "S" + type.getPrecision().toString();

        if ( type instanceof TypeArray ) {
            TypeArray typeArray = (TypeArray) type;
            String encoding =  Integer.toString(typeArray.getNoOfDimensions());
            ArrayList<ArrayDimension> dimensionList = typeArray.getDimensions();

            for ( int i = 0; i <  dimensionList.size(); i++ ) {
                encoding += "_" + dimensionList.get(i).getLowerBoundary() + "_" + dimensionList.get(i).getUpperBoundary();
            }

            return getDataTypeEncoding(typeArray.getBaseType()) + "_" + encoding;
        }

        if ( type instanceof TypeReference) {
            TypeReference reftype = (TypeReference) type;

            if ( reftype.getBaseType() instanceof TypeFixed)           return "a" + type.getPrecision().toString();
            if ( reftype.getBaseType() instanceof TypeFloat)           return "b" + type.getPrecision().toString();
            if ( reftype.getBaseType() instanceof TypeBit)             return "c" + type.getPrecision().toString();
            if ( reftype.getBaseType() instanceof TypeChar)            return "d" + type.getPrecision().toString();
            if ( reftype.getBaseType() instanceof TypeClock)           return "e" + type.getPrecision().toString();
            if ( reftype.getBaseType() instanceof TypeDuration)        return "f" + type.getPrecision().toString();
            if ( reftype.getBaseType() instanceof TypeTask)            return "g";
            if ( reftype.getBaseType() instanceof TypeProcedure)       return "h";
            if ( reftype.getBaseType() instanceof TypeSemaphore)       return "i";
            if ( reftype.getBaseType() instanceof TypeBolt)            return "j";
            if ( reftype.getBaseType() instanceof TypeStructure)       return "s";
        }

        return "~?~";
    }

    private int getNumberOfBytes(TypeDefinition type) {
        if ( type instanceof TypeFixed)           return type.getNoOfBytes();
        if ( type instanceof TypeFloat)           return type.getNoOfBytes();

        return 0;
    }

    public String getStructureName() {
        String sname = "";

        for (int i = 0; i < m_listOfComponents.size(); i++ ) {
            TypeDefinition typ = m_listOfComponents.get(i).m_type;
            sname += getComponentName(typ);
        }

        return "S" + sname.length() + sname;
    }

    private String getComponentName(TypeDefinition type) {
        String componentName = "";

        if ( type instanceof TypeStructure) {
            TypeStructure typeStructure = (TypeStructure) type;
            String components = "";
            for (int i = 0; i < typeStructure.m_listOfComponents.size(); i++ ) {
                TypeDefinition typ = typeStructure.m_listOfComponents.get(i).m_type;
                components += getComponentName(typ);
            }
            componentName += "S" + components.length() + components;
        }
        else {
            componentName = getDataTypeEncoding(type);
        }

        return componentName;
    }

    /**
     * Search the given identifier in the list of structure elements.
     *
     * @param id Identifier to look for
     * @return StructureComponent of the identifier
     *         null if not found
     */
    public StructureComponent lookup(String id) {
        for (int i = 0; i < m_listOfComponents.size(); i++ ) {
            if ( m_listOfComponents.get(i).m_id.equals(id) ) {
                return m_listOfComponents.get(i);
            }
        }

        return null;
    }

    public ST toST(STGroup group) {
        ST st = group.getInstanceOf("StructureType");
        st.add("type", getStructureName());
        return st;
    }

}
