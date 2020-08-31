package org.smallpearl.compiler.SymbolTable;

import org.antlr.v4.runtime.ParserRuleContext;
import org.smallpearl.compiler.Initializer;
import org.smallpearl.compiler.SmallPearlParser;
import org.smallpearl.compiler.TypeDefinition;
import org.smallpearl.compiler.TypeStructure;

public class TypeEntry extends SymbolTableEntry {

    private TypeStructure    m_structure;

    public TypeEntry() {
    }

    public TypeEntry(String name, TypeStructure struct, ParserRuleContext ctx)
    {
        super(name);
        this.m_ctx = ctx;
        this.m_structure = struct;
    }

    public String toString(int level) {
        String constant = null;

        return indentString(level) +
                super.toString(level) +
                "type " + m_structure;
    }

    public int getSourceLineNo() {
        return m_ctx.getStart().getLine();
    }
    public int getCharPositionInLine() {
        return m_ctx.getStart().getCharPositionInLine();
    }
    private ParserRuleContext m_ctx;

    public TypeDefinition getType() { return m_structure; }
}