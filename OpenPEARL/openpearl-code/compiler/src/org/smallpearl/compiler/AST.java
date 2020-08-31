package org.smallpearl.compiler;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

public class AST {

    private ParseTreeProperty<ASTAttribute> m_astAttribute = null;

    public AST() {
        m_astAttribute = new ParseTreeProperty<ASTAttribute>();
    }

    public Void put(ParserRuleContext ctx, ASTAttribute astAttribute) {
        Log.debug("AST:put:ctx " + CommonUtils.printContext(ctx));
        Log.debug("AST:put:astAttribute)= " + astAttribute);

        if ( ctx == null ) {
            Log.debug("AST:put:null ctx skipped!");
            return null;
        }

        if ( astAttribute == null ) {
            Log.debug("AST:put:null ast attribute skipped!");
            return null;
        }

        ASTAttribute attr = m_astAttribute.get(ctx);

        if ( attr != null ) {
            Log.debug("AST:put:ctx is already stored!");
        }

        m_astAttribute.put(ctx,astAttribute);
        return null;
    }

    public TypeDefinition lookupType(ParserRuleContext ctx) {
        ASTAttribute attr = m_astAttribute.get(ctx);
        TypeDefinition typedef = null;

        if ( attr != null ) {
            typedef = attr.getType();
        }

        if ( typedef == null ) {
            Log.debug("AST:lookupType:not found ctx " + CommonUtils.printContext(ctx));
        }
        else {
            Log.debug("AST:lookupType:found ctx " + CommonUtils.printContext(ctx));
            Log.debug("AST:lookupType:type=" + typedef);
        }

        return typedef;
    }

    public ASTAttribute lookup(ParserRuleContext ctx) {
        ASTAttribute attr = m_astAttribute.get(ctx);

        if ( attr == null ) {
            Log.debug("AST:lookupType:not found ctx " + CommonUtils.printContext(ctx));
        }
        else {
            Log.debug("AST:lookupType:found ctx " + CommonUtils.printContext(ctx));
            Log.debug("AST:lookupType:attr=" + attr);
        }

        return attr;
    }

    public ParserRuleContext lookup(ASTAttribute attr) {
        ParserRuleContext ctx = null;


        return ctx;
    }

}
