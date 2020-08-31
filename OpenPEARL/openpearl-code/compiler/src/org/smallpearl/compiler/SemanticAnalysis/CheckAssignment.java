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

import org.smallpearl.compiler.*;
import org.smallpearl.compiler.Exception.InternalCompilerErrorException;
import org.smallpearl.compiler.Exception.TypeMismatchException;
import org.smallpearl.compiler.SymbolTable.ModuleEntry;
import org.smallpearl.compiler.SymbolTable.ProcedureEntry;
import org.smallpearl.compiler.SymbolTable.SymbolTable;
import org.smallpearl.compiler.SymbolTable.SymbolTableEntry;
import org.smallpearl.compiler.SymbolTable.VariableEntry;
import org.smallpearl.compiler.SmallPearlParser.*;

public class CheckAssignment extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

  private int m_verbose;
  private boolean m_debug;
  private String m_sourceFileName;
  private ExpressionTypeVisitor m_expressionTypeVisitor;
  private SymbolTableVisitor m_symbolTableVisitor;
  private SymbolTable m_symboltable;
  private SymbolTable m_currentSymbolTable;
  private ModuleEntry m_module;
  private AST m_ast = null;

  public CheckAssignment(String sourceFileName,
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

    Log.info("Semantic Check: Check assignment statements");    }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // The type of the variable given to the left of the assignment sign has to match the type of the  value of the
  // expression, with the following exceptions:
  //  (1) The value of a FIXED variable or an integer, resp., may be assigned to a FLOAT variable.
  //  (2) The precision of a numeric variable to the left of an assignment sign may be greater than the precision of
  //      the value of the expression.
  //  (3) A bit or character string, resp., to the left may have a greater length than the value to be assigned; if
  //      needed, the latter is extended by zeros or spaces, resp., on the right.
  //  (4) A variable (no expression) may be assigned to a REF. In this case the type must match exactly
  //  (5) A reference may be assigned to a variable, implicit dereferencing occurs
  //  (6) special for assignments to references: rhs must live longer or equal as lhs
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  @Override
  public Void visitAssignment_statement(SmallPearlParser.Assignment_statementContext ctx) {
    Log.debug("CheckAssignment:visitAssignment_statement:ctx" + CommonUtils.printContext(ctx));
    String id = null;

    ErrorStack.enter(ctx,"assignment");
    ASTAttribute lhsAttr = null; 
    TypeDefinition lhsType = null;

    SmallPearlParser.NameContext ctxName = ctx.name();
//    if ( ctx.charSelectionSlice() != null ) {
//      lhsAttr = m_ast.lookup(ctx.charSelectionSlice())); 
//    else  if (ctx.bitSelectionSlice() != null) {
//      lhsAttr = m_ast.lookup(ctx.bitSelectionSlice()); 
//    } else {
//      // no selection; is  ('CONT')? name  
      lhsAttr = m_ast.lookup(ctx.name()); 
      ctxName = ctx.name();
//    }
    lhsType = lhsAttr.getType();
    id = ctxName.ID().getText();
    SymbolTableEntry lhs = m_currentSymbolTable.lookup(id);
    VariableEntry lhsVariable = null;
    if (lhs != null && lhs instanceof VariableEntry) {
      lhsVariable = (VariableEntry)lhs;
    } else {
      ErrorStack.addInternal(id+" not in symbol table or is no variable");
    }

    Log.debug("CheckAssignment:visitAssignment_statement:ctx.expression" + CommonUtils.printContext(ctx.expression()));

    if (!(lhsType instanceof TypeStructure ||
        lhsType instanceof TypeReference ||
        lhsType instanceof TypeVariableChar ||
        isSimpleType(lhsType))) {
      ErrorStack.add(lhsAttr.getType().toString() +" not allowed on lhs"); 
    } else { 


      TypeDefinition rhsType = m_ast.lookupType(ctx.expression());
      ASTAttribute rhsAttr = m_ast.lookup(ctx.expression());
      VariableEntry rhsVariable = rhsAttr.getVariable();
      SymbolTableEntry rhsSymbol = rhsAttr.getSymbolTableEntry();
      
      // problem with procedure call or procedure address
      // if the ASTAttributes indicate TypeProcedure with parameters
      // and no parameters are given, then we must treat the TypeProcedure, else
      // we may easily use the result type
      //
      // the difficult situation is a procedure without parameters.
      // in this case we must use the result type, if the left hand side is
      // not of the REF PROC
      
      if (rhsVariable == null && rhsSymbol != null) {
        // we have no variable, but a symbol exists --> may be a procedure
        if (rhsSymbol instanceof ProcedureEntry) {
          // let's have a look if we have actual parameters for procedure 
          
          ProcedureEntry pe = (ProcedureEntry )rhsAttr.getSymbolTableEntry();
          if (pe.getFormalParameters() != null) {
            // we must look in the context for parameters!
            // if it is not a nameContext with listOfExpression we get a 
            // nullPointerException - if not we have the result type
            try {
              BaseExpressionContext bctx = ((BaseExpressionContext) (ctx.expression()));
              if (bctx.primaryExpression().name().listOfExpression() != null) {
                rhsType = pe.getResultType();
                rhsAttr.setIsFunctionCall(true);
              }
            } catch (NullPointerException e) {};
           
          } else {
            rhsType = rhsAttr.getType();  // work with complete type
          }
        }
      }
      
//      // treat second case; lhs is not REF PROC and rhs is PROC or REF PROC without formal parameters
//      if ( (! (lhsType instanceof TypeReference)) || 
//           ((!(((TypeReference)lhsType).getBaseType() instanceof TypeProcedure)))) {
//        if (rhsType instanceof TypeReference) {
//          rhsType = ((TypeReference)rhsType).getBaseType();
//        }
//        if (rhsType instanceof TypeProcedure && ((TypeProcedure)rhsType).getFormalParameters()==null) {
//          rhsType = ((TypeProcedure)rhsType).getResultType();
//          rhsAttr.setIsFunctionCall(true);
//        }
//      }
      

      if (lhsType instanceof TypeReference && ctx.dereference() == null &&
          rhsType instanceof TypeReference) {
        // pointer assignment refVar1 := refVar2;
        // base types must be identical
        // rhs must live longer or equal to lhs!
        checkLifeCycle(lhsVariable,rhsVariable);
        
        lhsType = ((TypeReference)lhsType).getBaseType();
        rhsType = ((TypeReference)rhsType).getBaseType();
        if (rhsType != null) {
          checkTypes(lhsType, lhsAttr, rhsType, rhsAttr, true); // match exactly
        } else {
          // assignment of NIL to any reference is ok
        }
      } else if (lhsType instanceof TypeReference && ctx.dereference() != null &&   
          !(rhsType instanceof TypeReference)) {
        // CONT refVar = expr
        // types must match relaxed
        lhsType = ((TypeReference)lhsType).getBaseType();
        checkTypes(lhsType, lhsAttr, rhsType, rhsAttr, false); // lhs may be larger
      } else if (lhsType instanceof TypeReference &&
          !(rhsType instanceof TypeReference)) {
        // refVar = var; no expression allowed on rhs!
        // need variable or TASK,SEMA,...INTERRUPT
        if  (isReferableType(rhsType) ||
            rhsAttr.getVariable() != null) {
          lhsType = ((TypeReference)lhsType).getBaseType();
          checkTypes(lhsType, lhsAttr, rhsType, rhsAttr, true); // match exactly
        } else {
          ErrorStack.add("reference must point to a variable or TASK,SEMA,BOLT,INTERRUPT or SIGNAL");
        } 
        // rhs must live longer or equal to lhs!
        checkLifeCycle(lhsVariable,rhsVariable);
        
      } else if (!(lhsType instanceof TypeReference) &&
          !(rhsType instanceof TypeReference)) {
        // simple assignment var:= expr
        if (rhsType instanceof TypeProcedure) {
          // the type procedure does not work
          // let's try with the result type
          // we mark this in the ASTAttribute, if the type does not fit
          // we abort the compilation after the semantic check anf the attempt does not bother 
          rhsAttr.setIsFunctionCall(true);
          rhsType = ((TypeProcedure)rhsType).getResultType();
          
        }
        checkTypes(lhsType, lhsAttr, rhsType, rhsAttr, false); // lhs may be larger
      } else if (!(lhsType instanceof TypeReference) &&
          rhsType instanceof TypeReference) {
        // assignment var:= refVar
        // ok auto dereference
        rhsType = ((TypeReference)rhsType).getBaseType();
        checkTypes(lhsType, lhsAttr, rhsType, rhsAttr, false); // lhs may be larger
      } else {
        ErrorStack.add("type mismatch: "+lhsAttr.getType().toString()+":="+rhsAttr.getType().toString());
      }


    }

    ErrorStack.leave();
    return null;
  }

  
  private void checkLifeCycle(VariableEntry lhsVariable, VariableEntry rhsVariable) {
    if (rhsVariable != null) {
      if (lhsVariable.getLevel() < rhsVariable.getLevel()) {
        ErrorStack.add("life cycle of '" + rhsVariable.getName()+ "' is shorter than '"+ lhsVariable.getName()+"'");
      }
    } else {
      // rhs is NIL, TASK or PROC
    }
    
  }

  private void checkTypes(TypeDefinition lhsType, ASTAttribute lhsAttr,
      TypeDefinition rhsType, ASTAttribute rhsAttr,
      boolean matchExact) {
    
    boolean typeMismatch = false;


    
    if (matchExact) {
      if (lhsType instanceof TypeArraySpecification && rhsType instanceof TypeArray) {
        int lhsDim = ((TypeArraySpecification)lhsType).getNoOfDimensions();
        lhsType = ((TypeArraySpecification)lhsType).getBaseType();
        int rhsDim = ((TypeArray)rhsType).getNoOfDimensions();
        rhsType = ((TypeArray)rhsType).getBaseType();
        if (lhsDim != rhsDim || !lhsType.equals(rhsType)) {
          typeMismatch = true;
        } 
      }
      if (!lhsType.equals(rhsType)) {
        typeMismatch = true; 
      }
    } else {
      // 1) assignment to float must accept also fixed on the rhs
      // 2) TypeVariableChar on lhs or rhs without ConstantSelection fit to char of any length
      //    the check is done during runtime
      if (getTypeWithoutPrecision(lhsType) instanceof TypeFloat) {
        if (getTypeWithoutPrecision(rhsType) instanceof TypeFloat ||
            getTypeWithoutPrecision(rhsType) instanceof TypeFixed) {
          if (lhsType.getPrecision() < rhsType.getPrecision()) {
             typeMismatch = true; 
          }          
        } else {
          typeMismatch = true;
        }
      } else if (lhsType instanceof TypeVariableChar) {
        if (!(rhsType instanceof TypeVariableChar || rhsType instanceof TypeChar)) {
          typeMismatch = true; 
        }
      } else if (rhsType instanceof TypeVariableChar) {
        if (!(lhsType instanceof TypeVariableChar || lhsType instanceof TypeChar)) {
          typeMismatch = true; 
        }
      } else if (!getTypeWithoutPrecision(lhsType).equals(getTypeWithoutPrecision(rhsType))) {
          typeMismatch = true; 
      } else if (lhsType.getPrecision() < rhsType.getPrecision()) {
        typeMismatch = true; 
      }
    
    }
    if (typeMismatch) {
      ErrorStack.add("type mismatch: " +lhsAttr.getType().toString() +" := " + rhsAttr.getType().toString());
    }
    return;
  }
  
  private TypeDefinition getTypeWithoutPrecision(TypeDefinition t) {
    if (t instanceof TypeFixed) {
      return new TypeFixed();
    }
    if (t instanceof TypeFloat) {
      return new TypeFloat();
    }
    if (t instanceof TypeChar) {
      return new TypeChar();
    }
    if (t instanceof TypeBit) {
      return new TypeBit();
    }
    return t;
  }
  
  private boolean isSimpleType(TypeDefinition type) {
    boolean result = false;

    if (type instanceof TypeFixed    ||
        type instanceof TypeFloat    ||
        type instanceof TypeBit      ||
        type instanceof TypeChar     ||
        type instanceof TypeDuration ||
        type instanceof TypeClock ) {
      result = true;
    }
    return result;
  }
  
  // naming not good, since simple types are also referable
  private boolean isReferableType(TypeDefinition rhsType) {
    boolean result = false;

    if (rhsType instanceof TypeTask      ||
        rhsType instanceof TypeProcedure ||
        rhsType instanceof TypeSemaphore ||
        rhsType instanceof TypeBolt      ||
        rhsType instanceof TypeSignal    ||
        rhsType instanceof TypeDation    ||
        rhsType instanceof TypeInterrupt) {
      result = true;
    }
    return result;
  }

  @Override
  public Void visitModule(SmallPearlParser.ModuleContext ctx) {
    org.smallpearl.compiler.SymbolTable.SymbolTableEntry symbolTableEntry = m_currentSymbolTable.lookupLocal(ctx.ID().getText());
    m_currentSymbolTable = ((org.smallpearl.compiler.SymbolTable.ModuleEntry)symbolTableEntry).scope;
    visitChildren(ctx);
    m_currentSymbolTable = m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    m_currentSymbolTable = m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
    return null;
  }
}