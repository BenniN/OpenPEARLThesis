package org.smallpearl.compiler.SymbolTable;

import org.smallpearl.compiler.SmallPearlParser;
import org.smallpearl.compiler.TypeDefinition;

public class FormalParameter extends VariableEntry {

    public  Boolean			passIdentical;
    
    public FormalParameter() {
    }

    public FormalParameter(String name, TypeDefinition type, Boolean assignmentProtection, Boolean passIdentical, SmallPearlParser.FormalParameterContext ctx) {
    	super(name, type, assignmentProtection, ctx);
    	this.passIdentical = passIdentical;
        
    }

    public String toString() {
        return (super.getAssigmentProtection() ? " INV " : " " ) + super.getType();
    }

    /* other methods inherited from parent class */
   

}