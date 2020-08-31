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


package org.smallpearl.compiler.SymbolTable;

import org.smallpearl.compiler.*;
import org.smallpearl.compiler.Exception.NotYetImplementedException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;


public class SymbolTable {

    /**
     * Allocate a new symtab of the given size.  The size is the number of
     * table m_entries (not bytes).  All m_entries are initialized to null, the
     * parent is initialized to null, and m_level to 0.  Parent and m_level are
     * only set to non-null/non-zero values when a SymbolTable is constructed
     * with the newLevel method.
     */
    public SymbolTable() {
        m_entries = new HashMap();
        m_level = 0;
        m_usesSystemElements = false;
    }

    /**
     * Allocate a new symtab and add it as a new m_level to this symtab.  The new
     * m_level is linked into the existing symtab via the scope field of the
     * given function entry, and the parent entry of this, as illustrated in
     * the class documnentation.  The m_level field of the the new symtab is set
     * to this.m_level+1.  The return value is a reference to the new m_level.
     */
    public SymbolTable newLevel(ModuleEntry moduleEntry) {
        SymbolTable newst;

        enter(moduleEntry);
        newst = moduleEntry.scope = new SymbolTable();
        newst.parent = this;
        newst.m_level = m_level + 1;

        return newst;

    }

    public SymbolTable newLevel(ProcedureEntry procedureEntry) {
        SymbolTable newst;

        enter(procedureEntry);
        newst = procedureEntry.scope = new SymbolTable();
        newst.parent = this;
        newst.m_level = m_level + 1;

        return newst;

    }

    public SymbolTable newLevel(BlockEntry blockEntry) {
        SymbolTable newst;

        enter(blockEntry);
        newst = blockEntry.scope = new SymbolTable();
        newst.parent = this;
        newst.m_level = m_level + 1;

        return newst;

    }

    public SymbolTable newLevel(TaskEntry taskEntry) {
        SymbolTable newst;

        enter(taskEntry);
        newst = taskEntry.scope = new SymbolTable();
        newst.parent = this;
        newst.m_level = m_level + 1;

        return newst;

    }

    public SymbolTable newLevel(LoopEntry loopEntry) {
        SymbolTable newst;
        enter(loopEntry);
        newst = loopEntry.scope = new SymbolTable();
        newst.parent = this;
        newst.m_level = m_level + 1;
        return newst;
    }

    public SymbolTableEntry lookup(String name) {
        int i;
        SymbolTable st;
        SymbolTableEntry se;
        Log.debug("SymbolTable:lookup: name=" + name);

       for (st = this; st != null; st = st.parent) {
            if ((se = (SymbolTableEntry) st.m_entries.get(name)) != null) {
                return se;
            }
        }

        return null;
    }

    public SymbolTableEntry lookupLocal(String name) {
        Log.debug("SymbolTable:lookupLocal: name=" + name);
        return (SymbolTableEntry) m_entries.get(name);
    }

    /**
     * 
     * @param se
     * @return false, if the symbol was already in the symbol table
     * <br> true, if the symbol was added
     */
    public boolean enter(SymbolTableEntry se) {
        if (lookupLocal(se.getName()) != null) {
            return false;
        }
        se.setLevel(m_level);
        m_entries.put(se.getName(), se);
        return true;
    }

    public boolean enterOrReplace(SymbolTableEntry se) {
        if (lookupLocal(se.getName()) != null) {
            m_entries.remove(se.getName());
        }
        m_entries.put(se.getName(), se);
        return true;
    }

    public SymbolTable ascend() {
        return parent != null ? parent : this;
    }

    public SymbolTable descend(String name) {
        SymbolTableEntry se = lookupLocal(name);

        try {
            if ( se == null) {
                return this;
            }

            if ( se.getClass() != Class.forName("ModuleEntry")) {
                return ((ModuleEntry) se).scope;
            }

            if ( se.getClass() != Class.forName("ProcedureEntry")) {
                return ((ProcedureEntry) se).scope;
            }

            if ( se.getClass() != Class.forName("TaskEntry")) {
                return ((TaskEntry) se).scope;
            }

            if ( se.getClass() != Class.forName("BlockEntry")) {
                return ((BlockEntry) se).scope;
            }

            return this;
        }
        catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            return null;
        }
    }

    public void dump() {
        System.out.println();
        System.out.println("Symboltable:");
        System.out.println(toString()+"\n");
    }

    public String toString() {
        return toString(this.m_level);
    }

    public String toString(int level) {
        SymbolTableEntry e;
        String indent = "", output = "";
        int nextLevel = level + 1;

        for (int i = 0; i < level; i++) {
            indent += "  ";
        }

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            output += ((SymbolTableEntry)it.next()).toString(nextLevel) +
                    (it.hasNext() ? "\n" : "");
        }

        return output;
    }

    public LinkedList<TaskEntry> getTaskDeclarations() {
        LinkedList<TaskEntry>  listOfTaskEntries = new  LinkedList<TaskEntry>();
        SymbolTableEntry e;

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry symbolTableEntry = (SymbolTableEntry) it.next();
            if ( symbolTableEntry instanceof TaskEntry ) {
                TaskEntry taskEntry = (TaskEntry)symbolTableEntry;
                listOfTaskEntries.add(taskEntry);
            }
        }

        return listOfTaskEntries;
    }

    public LinkedList<VariableEntry> getVariableDeclarations() {
        LinkedList<VariableEntry>  listOfVariableDeclarationsEntries = new  LinkedList<VariableEntry>();
        SymbolTableEntry e;

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry symbolTableEntry = (SymbolTableEntry) it.next();
            if ( symbolTableEntry instanceof VariableEntry ) {
                VariableEntry variableEntry = (VariableEntry)symbolTableEntry;
                listOfVariableDeclarationsEntries.add(variableEntry);
            }
        }

        return listOfVariableDeclarationsEntries;
    }


    public LinkedList<VariableEntry> getAllArrayDeclarations(SymbolTable symbolTable) {
        LinkedList<VariableEntry> listOfArrayDeclarations = new LinkedList<VariableEntry>();

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry entry = (SymbolTableEntry)it.next();
       }

        return listOfArrayDeclarations;
    }

    public LinkedList<SemaphoreEntry> getSemaphoreDeclarations() {
        LinkedList<SemaphoreEntry>  listOfSemaEntries = new  LinkedList<SemaphoreEntry>();
        SymbolTableEntry e;

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry symbolTableEntry = (SymbolTableEntry) it.next();
            if (symbolTableEntry instanceof SemaphoreEntry) {
                SemaphoreEntry semaEntry = (SemaphoreEntry) symbolTableEntry;
                listOfSemaEntries.add(semaEntry);
            }
        }

        return listOfSemaEntries;
    }

    public LinkedList<BoltEntry> getBoltDeclarations() {
        LinkedList<BoltEntry>  listOfBoltEntries = new  LinkedList<BoltEntry>();
        SymbolTableEntry e;

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry symbolTableEntry = (SymbolTableEntry) it.next();
            if (symbolTableEntry instanceof BoltEntry) {
                BoltEntry boltEntry = (BoltEntry) symbolTableEntry;
                listOfBoltEntries.add(boltEntry);
            }
        }

        return listOfBoltEntries;
    }

    public LinkedList<ModuleEntry> getModules() {
        LinkedList<ModuleEntry>  listOfModules = new  LinkedList<ModuleEntry>();

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry symbolTableEntry = (SymbolTableEntry) it.next();
            if (symbolTableEntry instanceof ModuleEntry) {
                ModuleEntry moduleEntry = (ModuleEntry) symbolTableEntry;
                listOfModules.add(moduleEntry);
            }
        }

        return listOfModules;
    }

    public HashMap<String,TypeStructure> getStructureDeclarations() {
        HashMap<String,TypeStructure>  structures = new  HashMap<>();
        SymbolTableEntry e;

        for (Iterator it = m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry symbolTableEntry = (SymbolTableEntry) it.next();
            if (symbolTableEntry instanceof ModuleEntry) {
                getStructureDeclarationsForSymboltable(((ModuleEntry) symbolTableEntry).scope, structures);
            }
        }

        return structures;
    }

    private void getStructureDeclarationsForSymboltable(SymbolTable symbolTable, HashMap<String,TypeStructure>  structures) {
        SymbolTableEntry e;

        for (Iterator it = symbolTable.m_entries.values().iterator(); it.hasNext(); ) {
            SymbolTableEntry symbolTableEntry = (SymbolTableEntry) it.next();

            if (symbolTableEntry instanceof VariableEntry) {
                VariableEntry entry = (VariableEntry)symbolTableEntry;
                if ( entry.getType() instanceof TypeStructure ) {
                    TypeStructure struct = (TypeStructure)entry.getType();
                    getStructureDeclarationsForStructure(struct.getStructureName(),struct,structures);
                } else if (entry.getType() instanceof TypeArray) {
                    TypeArray array = (TypeArray)entry.getType();
                    if ( array.getBaseType() instanceof TypeStructure) {
                        TypeStructure struct = (TypeStructure) array.getBaseType();
                        getStructureDeclarationsForStructure(struct.getStructureName(), struct, structures);
                    }
                }
            }
            else if (symbolTableEntry instanceof ProcedureEntry) {
                ProcedureEntry procedureEntry = (ProcedureEntry) symbolTableEntry;

                if (procedureEntry.getResultType() != null) {
                    if (procedureEntry.getResultType() instanceof TypeStructure) {
                        TypeStructure result = (TypeStructure) procedureEntry.getResultType();
                        structures.put(result.getStructureName(), result);
                    }
                }

                getStructureDeclarationsForSymboltable(((ProcedureEntry) symbolTableEntry).scope, structures);
            }
            else if (symbolTableEntry instanceof TaskEntry) {
                getStructureDeclarationsForSymboltable(((TaskEntry) symbolTableEntry).scope, structures);
            }
            else if (symbolTableEntry instanceof BlockEntry) {
                getStructureDeclarationsForSymboltable(((BlockEntry) symbolTableEntry).scope, structures);
            }
            else if (symbolTableEntry instanceof LoopEntry) {
                getStructureDeclarationsForSymboltable(((LoopEntry) symbolTableEntry).scope, structures);
            }
        }
    }

    private void getStructureDeclarationsForStructure(String name, TypeStructure structure, HashMap<String,TypeStructure>  structures) {
        SymbolTableEntry e;

        structures.put(name, structure);

        for (Iterator it = structure.m_listOfComponents.iterator(); it.hasNext(); ) {
            StructureComponent structureComponent = (StructureComponent) it.next();

            if ( structureComponent.m_type instanceof TypeStructure) {
                TypeStructure struct = (TypeStructure)structureComponent.m_type;
                getStructureDeclarationsForStructure(struct.getStructureName(), struct,structures);
            }
            else if ( structureComponent.m_type instanceof TypeArray) {
                TypeArray array = (TypeArray)structureComponent.m_type;

                if ( array.getBaseType() instanceof TypeStructure) {
                    TypeStructure struct = (TypeStructure) array.getBaseType();
                    getStructureDeclarationsForStructure(struct.getStructureName(), struct, structures);
                }
            }
        }
    }

    public int lookupDefaultFixedLength() {
        SymbolTableEntry entry = this.lookup("~LENGTH_FIXED~");

        if ( entry != null ) {
            if ( entry instanceof LengthEntry) {
                LengthEntry e = (LengthEntry) entry;
                if (e.getType() instanceof TypeFixed ) {
                    TypeFixed typ = (TypeFixed) e.getType();
                    return typ.getPrecision();
                }
            }
        } else {
            return Defaults.FIXED_LENGTH;
        }

        return -1;
    }

    public int lookupDefaultFloatLength() {
        SymbolTableEntry entry = this.lookup("~LENGTH_FLOAT~");

        if ( entry != null ) {
            if ( entry instanceof LengthEntry) {
                LengthEntry e = (LengthEntry) entry;
                if (e.getType() instanceof TypeFloat ) {
                    TypeFloat typ = (TypeFloat) e.getType();
                    return typ.getPrecision();
                }
            }
        } else {
            return Defaults.FLOAT_PRECISION;
        }

        return -1;
    }

    public int lookupDefaultCharLength() {
        SymbolTableEntry entry = this.lookup("~LENGTH_CHAR~");

        if ( entry != null ) {
            if ( entry instanceof LengthEntry) {
                LengthEntry e = (LengthEntry) entry;
                if (e.getType() instanceof TypeChar) {
                    TypeChar typ = (TypeChar) e.getType();
                    return typ.getSize();
                }
            }
        } else {
            return Defaults.CHARACTER_LENGTH;
        }

        return -1;
    }

    public int lookupDefaultBitLength() {
        SymbolTableEntry entry = this.lookup("~LENGTH_BIT~");

        if ( entry != null ) {
            if ( entry instanceof LengthEntry) {
                LengthEntry e = (LengthEntry) entry;
                if (e.getType() instanceof TypeBit ) {
                    TypeBit typ = (TypeBit) e.getType();
                    return typ.getPrecision();
                }
            }
        } else {
            return Defaults.BIT_LENGTH;
        }

        return -1;
    }

    public int getNumberOfComponents(TypeStructure typ) {
        int numberOfComponents = 0;

        for (int i = 0; i < typ.m_listOfComponents.size(); i++ ) {
            TypeDefinition componentType = typ.m_listOfComponents.get(i).m_type;

            if (componentType instanceof TypeFixed) {
                numberOfComponents++;
            }
            else {
                numberOfComponents += getNumberOfComponents(componentType);
            }
        }

        return numberOfComponents;
    }

    public int getNumberOfComponents(TypeArray typ) {
        return typ.getTotalNoOfElements() * getNumberOfComponents(typ.getBaseType());
    }

    public int getNumberOfComponents(TypeDefinition typ) {
        if ( typ instanceof TypeArray) {
            return getNumberOfComponents((TypeArray)typ);
        } else if ( typ instanceof TypeStructure) {
            return getNumberOfComponents((TypeStructure)typ);
        } else {
            return 1;
        }
    }


    public void setUsesSystemElements() { m_usesSystemElements = true;}
    public boolean usesSystemElements() { return m_usesSystemElements;}

    public SymbolTable parent;
    protected HashMap m_entries;
    public int m_level;
    private boolean m_usesSystemElements;
}
