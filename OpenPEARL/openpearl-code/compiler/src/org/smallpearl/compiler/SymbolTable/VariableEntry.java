package org.smallpearl.compiler.SymbolTable;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.smallpearl.compiler.*;
import org.smallpearl.compiler.ConstantFixedValue;

import java.util.ArrayList;

public class VariableEntry extends SymbolTableEntry {

    private TypeDefinition    m_type;
    private Boolean           m_hasAssigmentProtection;
    private Boolean           m_loopControlVariable;
    private Initializer       m_initializer;
   
    public VariableEntry()
    {
        super("");
        this.m_ctx = null;
        this.m_type = null;
        this.m_hasAssigmentProtection = false;
        this.m_loopControlVariable = false;
        this.m_initializer = null;
    }

    public VariableEntry(String name)
    {
        super(name);
        this.m_ctx = null;
        this.m_type = null;
        this.m_hasAssigmentProtection = false;
        this.m_loopControlVariable = false;
        this.m_initializer = null;
    }

    public VariableEntry(String name, TypeDefinition type, org.antlr.v4.runtime.ParserRuleContext ctx)
    {
        super(name);
        this.m_ctx = ctx;
        this.m_type = type;
        this.m_hasAssigmentProtection = false;
        this.m_loopControlVariable = false;
        this.m_initializer = null;
      

    }
    public VariableEntry(String name, TypeDefinition type, Boolean hasAssignmentProtection, org.antlr.v4.runtime.ParserRuleContext ctx)
    {
        super(name);
        this.m_ctx = ctx;
        this.m_type = type;
        this.m_hasAssigmentProtection = hasAssignmentProtection;
        this.m_loopControlVariable = false;
        this.m_initializer = null;
       
    }

    
    public VariableEntry(String name, TypeDefinition type, Boolean hasAssigmentProtection, org.antlr.v4.runtime.ParserRuleContext ctx, Initializer initializer)
    {
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

//        if (this.m_constantCtx != null ) {
//            if (this.m_constantCtx instanceof SmallPearlParser.InitElementContext) {
//                SmallPearlParser.InitElementContext ctx = (SmallPearlParser.InitElementContext) this.m_constantCtx;
//
//                if (ctx.constantExpression() != null) {
//                    constant = ctx.constantExpression().getText();
//                } else if (ctx.constant() != null) {
//                    constant = ctx.constant().getText();
//                } else if (ctx.ID() != null) {
//                    constant = ctx.ID().getText();
//                }
//            } else if (this.m_constantCtx instanceof SmallPearlParser.ConstantContext) {
//                SmallPearlParser.ConstantContext ctx = (SmallPearlParser.ConstantContext) this.m_constantCtx;
//                if (ctx.bitStringConstant() != null) {
//                    constant = ctx.bitStringConstant().getText();
//                } else if (ctx.durationConstant() != null) {
//                    constant = ctx.durationConstant().getText();
//                } else if (ctx.floatingPointConstant() != null) {
//                    constant = ctx.floatingPointConstant().FloatingPointNumberWithoutPrecision().getText();
//                } else if (ctx.fixedConstant() != null) {
//                    constant = ctx.fixedConstant().IntegerConstant().getText();
//                } else if (ctx.StringLiteral() != null) {
//                    constant = ctx.StringLiteral().getText();
//                } else if (ctx.timeConstant() != null) {
//                    constant = ctx.timeConstant().getText();
//                }
//            }
//        }
//

        return indentString(level) +
                super.toString(level) +
                "var " +
                m_type + " " +
                assigmenProtection +
                (this.m_loopControlVariable ? " LC" :"") +
                (this.m_initializer != null ? "  INIT(" + m_initializer + ")" : "");
    }

    public int getSourceLineNo() {
        return m_ctx.getStart().getLine();
    }
    public int getCharPositionInLine() {
        return m_ctx.getStart().getCharPositionInLine();
    }
    private org.antlr.v4.runtime.ParserRuleContext m_ctx;
    public org.antlr.v4.runtime.ParserRuleContext getCtx() {
		return m_ctx;
	}

	public TypeDefinition getType() { return m_type; }
 //   public ParserRuleContext getConstantCtx() { return m_constantCtx; }
    public Boolean getAssigmentProtection() { return m_hasAssigmentProtection; }

    public Void setLoopControlVariable() {
        m_loopControlVariable = true;
        return null;
    }

    public Boolean getLoopControlVariable() { return m_loopControlVariable; }

    public Initializer getInitializer() { return m_initializer; }

}