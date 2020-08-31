package org.smallpearl.compiler.SymbolTable;

import org.antlr.v4.runtime.ParserRuleContext;
import org.smallpearl.compiler.Initializer;
import org.smallpearl.compiler.SmallPearlParser;
import org.smallpearl.compiler.TypeDefinition;

public class StructureComponentEntry extends SymbolTableEntry {

    private TypeDefinition m_type;
    private Boolean m_hasAssigmentProtection;
    private Boolean m_loopControlVariable;
    private Initializer m_initializer;

    public StructureComponentEntry() {
    }

    public StructureComponentEntry(String name, TypeDefinition type, ParserRuleContext ctx) {
        super(name);
        this.m_ctx = ctx;
        this.m_type = type;
        this.m_hasAssigmentProtection = false;
        this.m_loopControlVariable = false;
        this.m_initializer = null;
    }

    public StructureComponentEntry(String name,
                                   TypeDefinition type,
                                   Boolean hasAssigmentProtection,
                                   SmallPearlParser.VariableDenotationContext ctx,
                                   Initializer initializer) {
        super(name);
        this.m_ctx = ctx;
        this.m_type = type;
        this.m_hasAssigmentProtection = hasAssigmentProtection;
        this.m_loopControlVariable = false;
        this.m_initializer = initializer;
    }

    public String toString(int level) {
        String assigmenProtection = this.m_hasAssigmentProtection ? "INV" : "";
        String constant = null;

        return indentString(level) +
                super.toString(level) +
                "STRUCTCOMPONENT " +
                m_type + " " +
                assigmenProtection +
                (this.m_loopControlVariable ? " LC" : "") +
                (this.m_initializer != null ? "  INIT(" + m_initializer + ")" : "");
    }

    public int getSourceLineNo() {
        return m_ctx.getStart().getLine();
    }

    public int getCharPositionInLine() {
        return m_ctx.getStart().getCharPositionInLine();
    }

    private ParserRuleContext m_ctx;

    public TypeDefinition getType() {
        return m_type;
    }

    //   public ParserRuleContext getConstantCtx() { return m_constantCtx; }
    public Boolean getAssigmentProtection() {
        return m_hasAssigmentProtection;
    }

    public Void setLoopControlVariable() {
        m_loopControlVariable = true;
        return null;
    }

    public Boolean getLoopControlVariable() {
        return m_loopControlVariable;
    }

    public Initializer getInitializer() {
        return m_initializer;
    }
}