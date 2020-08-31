/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2017 Marcel Schaible
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

package org.smallpearl.compiler.SemanticAnalysis;

import org.antlr.v4.runtime.ParserRuleContext;
import org.smallpearl.compiler.*;
import org.smallpearl.compiler.Exception.*;
import org.smallpearl.compiler.SymbolTable.ModuleEntry;
import org.smallpearl.compiler.SymbolTable.SymbolTable;
import org.smallpearl.compiler.SymbolTable.SymbolTableEntry;
import org.smallpearl.compiler.SymbolTable.VariableEntry;


/**
 * @author mueller
 *
 * check if all referenced system dations are specified 
 * verify compatibility of system dation attributes with user dation attributes
 * create entry in symbol table with the dations attributes
 * 
 */
public class CheckDationDeclaration extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

    private int m_verbose;
    private boolean m_debug;
    private String m_sourceFileName;
    private ExpressionTypeVisitor m_expressionTypeVisitor;
    private SymbolTableVisitor m_symbolTableVisitor;
    private SymbolTable m_symboltable;
    private SymbolTable m_currentSymbolTable;
    private ModuleEntry m_module;
    private AST m_ast = null;

    public CheckDationDeclaration(String sourceFileName,
                    int verbose,
                    boolean debug,
                    SymbolTableVisitor symbolTableVisitor,
                    ExpressionTypeVisitor expressionTypeVisitor,
                    AST ast) {

        m_debug = debug;
        m_verbose = verbose;
        m_sourceFileName = sourceFileName;
        m_symbolTableVisitor = symbolTableVisitor;
        m_expressionTypeVisitor = expressionTypeVisitor;
        m_symboltable = symbolTableVisitor.symbolTable;
        m_currentSymbolTable = m_symboltable;
        m_ast = ast;

        Log.debug( "    Check DationDeclaration");
    }

    @Override
    public Void visitModule(SmallPearlParser.ModuleContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check RST: visitModule");
        }

        org.smallpearl.compiler.SymbolTable.SymbolTableEntry symbolTableEntry = m_currentSymbolTable.lookupLocal(ctx.ID().getText());
        m_currentSymbolTable = ((ModuleEntry)symbolTableEntry).scope;
        visitChildren(ctx);
        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check RST: visitProcedureDeclaration");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check RST: visitTaskDeclaration");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check RST: visitBlock_statement");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check RST: visitLoopStatement");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitDationDeclaration(SmallPearlParser.DationDeclarationContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: visitDationDeclaration");
        }
        for (int i = 0; i<ctx.identifierDenotation().ID().size(); i++) {
        	String dationName = ctx.identifierDenotation().ID(i).toString();
            //System.out.println("DationName: "+ dationName);
        
            ErrorStack.enter(ctx, "DationDCL");
            

    	    SymbolTableEntry entry1 = this.m_currentSymbolTable.lookup(dationName);

            if (entry1 == null) {
        	   throw new InternalCompilerErrorException("Symbol table does not contain:"+dationName);
            }
            SymbolTableEntry se = this.m_currentSymbolTable.lookup(dationName);
            if (se != null &&
            	! (se instanceof VariableEntry)	&&
            	!(((VariableEntry)se).getType() instanceof TypeDation)
            		) {
            	throw new InternalCompilerErrorException("symbol "+dationName+" not found/or no dation");
            }
        	TypeDation d = (TypeDation)(((VariableEntry)se).getType());
        	
        	// userdation must be 
        	// of type ALPHIC					   -> DationPG
        	// or type BASIC + typeOfTransmission  -> DationTS
        	// or type of       typeOfTransmission -> DationRW
        	// this is all enforced by the grammar
        	
        	if (d.isSystemDation()) {
        		ErrorStack.add("SYSTEM dations may not be declared");
        	}
        	// dimension settings must be >0 or '*' if given
        	// only the last dimension may be '*' - the not 0 check is not in the grammar
        	switch (d.getNumberOfDimensions()) {
        	case  3:
         		if (d.getDimension3()<=0 || d.getDimension2()<=0) {
        			// '*' not allowed
        			ErrorStack.add("only first dimension may be '*'");
        		}
         		break;
        	case  2:
        		if (d.getDimension2()==0) {
        			// '*' not allowed
        			ErrorStack.add("only first dimension may be '*'");
        		}
        		break;
        	case  1:
          		break;
          	}

        	if (d.hasTfu() && d.getNumberOfDimensions() == 1 && d.getDimension1() == 0) {
     	       ErrorStack.add("TFU requires limited record length");
        	}

        	
            SymbolTableEntry sys = this.m_currentSymbolTable.lookup(d.getCreatedOn());;
            if (sys == null) {
            	ErrorStack.add(d.getCreatedOn()+" is not defined");
            	// TFU does not agree with last dimension unlimited  !
            } else if ( (! (sys instanceof VariableEntry))  ||
                 (! (((VariableEntry)sys).getType() instanceof TypeDation))) {
            	ErrorStack.add(d.getCreatedOn()+" is not of type DATION");
            } else {	
               TypeDation sd = (TypeDation)(((VariableEntry)sys).getType());
               
               // check compatibility
               // (1) ALPHIC need !BASIS of the system dation
               if (d.isAlphic() && sd.isBasic()) {
            	   ErrorStack.add("attempt to create PUT/GET dation upon BASIC system dation");
               }
               if (!d.isBasic() && sd.isBasic()) {
            	   ErrorStack.add("attempt to create a READ/WRITE dation upon a BASIC sytem dation");
               }
               if (d.isBasic() && !sd.isBasic()) {
            	   ErrorStack.add("attempt to create a BASIC dation upon an non BASIC system dation");
               }

               // (2) direction must fit (sourceSinkAttribute
               if (d.isIn() && ! sd.isIn()) {
            	   ErrorStack.add("system dation does not provide direction IN");
               }
               if (d.isOut() && ! sd.isOut()) {
            	   ErrorStack.add("system dation does not provide direction OUT");
               }
               
               // (3) TFU must be on userdation if this is set on system dation
               if (!d.hasTfu() && sd.hasTfu()) {
            	   ErrorStack.add("system dation requires TFU for user dation");
               	   if (d.getNumberOfDimensions() == 1 && d.getDimension1() == 0) {
            		   ErrorStack.warn("TFU would require limited record length for user dation");
               	   }
               }
               
               // (4) type of transmission must fit, if not set to ALL in system dation
               if (sd.getTypeOfTransmission() == null) {
            	   if (!sd.isAlphic()) {
            	      // the system dation misses some data -- this should be detected by the
            	      // imc in all compilations
            	     System.out.println("ctx: "+ctx.getText());
            	      throw new InternalCompilerErrorException(sd+" has no typeOfTransmission");
            	   }
               } else if (!sd.getTypeOfTransmission().equals("ALL")) {
            	   if (!sd.getTypeOfTransmission().equals(d.getTypeOfTransmission())) {
            		   ErrorStack.add("type of transmission mismatch (system:"+sd.getTypeOfTransmission()+
            				   " user: "+d.getTypeOfTransmission()+")");
            	   }
               }
               
               // (5) if the system dation specifies a TFU-size; the record length
               //     of the user must not exceed. This is checked during runtime,
               //     since we have no access to the system dations description files
               //     at compile time

            }
            
        	// TFU does not agree with last dimension unlimited  !
        	if (d.hasTfu() && d.getNumberOfDimensions() == 1 && d.getDimension1() == 0) {
        		if (sys == null) {
        	       ErrorStack.add("TFU requires limited record length");
        		}
        	}
        
        	
            ErrorStack.leave();
        }
                
        return null;
    }

    

}