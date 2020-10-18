package org.smallpearl.compiler.SemanticAnalysis;

import java.util.LinkedList;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.smallpearl.compiler.*;
import org.smallpearl.compiler.SmallPearlParser.ExpressionContext;
import org.smallpearl.compiler.SmallPearlParser.FormatPositionContext;
import org.smallpearl.compiler.SmallPearlParser.ProcedureBodyContext;
import org.smallpearl.compiler.SmallPearlParser.ProcedureDeclarationContext;
import org.smallpearl.compiler.SmallPearlParser.ResultAttributeContext;
import org.smallpearl.compiler.SmallPearlParser.TaskDeclarationContext;
import org.smallpearl.compiler.Exception.*;
import org.smallpearl.compiler.SymbolTable.FormalParameter;
import org.smallpearl.compiler.SymbolTable.ModuleEntry;
import org.smallpearl.compiler.SymbolTable.ProcedureEntry;
import org.smallpearl.compiler.SymbolTable.SymbolTable;
import org.smallpearl.compiler.SymbolTable.SymbolTableEntry;
import org.smallpearl.compiler.SymbolTable.VariableEntry;


public class CheckProcedureDeclaration extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

    private int m_verbose;
    private boolean m_debug;
    private String m_sourceFileName;
    private ExpressionTypeVisitor m_expressionTypeVisitor;
    private SymbolTableVisitor m_symbolTableVisitor;
    private SymbolTable m_symboltable;
    private SymbolTable m_currentSymbolTable;
    private ModuleEntry m_module;
    private AST m_ast = null;
    private TypeDefinition m_typeOfReturns;
    //private TypeDefinition m_typeOfReturnExpression;

    private ParseTreeProperty<SymbolTable> m_symboltablePerContext = null;

    public CheckProcedureDeclaration(String sourceFileName,
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
            System.out.println( "Semantic: CheckProcedureDeclaration: visitModule");
        }

        org.smallpearl.compiler.SymbolTable.SymbolTableEntry symbolTableEntry = m_currentSymbolTable.lookupLocal(ctx.ID().getText());
        m_currentSymbolTable = ((ModuleEntry)symbolTableEntry).scope;
        visitChildren(ctx);
        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    /* @Override
     public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
         if (m_debug) {
             System.out.println( "Semantic: CheckProcedureDeclaration: visitProcedureDeclaration");
         }

         this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
         visitChildren(ctx);
         this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
         return null;
     }
 */
    @Override
    public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: CheckProcedureDeclaration: visitTaskDeclaration");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: CheckProcedureDeclaration: visitBlock_statement");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: CheckProcedureDeclaration: visitLoopStatement");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
        String globalId = null;
        LinkedList<FormalParameter> formalParameters = null;
        ASTAttribute resultType = null;

        if (m_verbose > 0) {
            System.out.println("SymbolTableVisitor: visitProcedureDeclaration");
        }
        ErrorStack.enter(ctx,"PROC");

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);

        SymbolTableEntry entry = this.m_currentSymbolTable.lookup(ctx.ID().toString());
        if (entry == null) {
            throw new InternalCompilerErrorException("PROC "+ctx.ID().toString()+" not found", ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        ProcedureEntry procedureEntry = (ProcedureEntry)entry;

        //TODO: MS This caanot be corrected, because semancti check should not alter the symboltable
        //this.m_currentSymbolTable = this.m_currentSymbolTable.newLevel(procedureEntry);

        if ( procedureEntry.getFormalParameters() != null && procedureEntry.getFormalParameters().size() > 0) {
            /* check formal parameters of this procedure */


            for (FormalParameter formalParameter : procedureEntry.getFormalParameters()) {
                checkFormalParameter(formalParameter);
            }
        }

        // reset the attribute before visitChildren()
        // m_typeOfReturnExpression contains the type of the last RETURN statement
        // in the procedure body
        m_typeOfReturns = procedureEntry.getResultType();
//		m_typeOfReturnExpression = null;

        visitChildren(ctx);

        if (m_typeOfReturns != null) {
            // check last statement of function to be RETURN
            // this is easier to implement as to enshure that all paths of control
            // meet a RETURN(..) statemen
            ProcedureBodyContext b = ctx.procedureBody();
            int last = b.statement().size();
            if (last == 0) {
                ErrorStack.add("must end with RETURN ("+m_typeOfReturns.toString()+")");
            } else {
                SmallPearlParser.StatementContext lastStmnt = b.statement(last-1);
                if (lastStmnt.unlabeled_statement() != null) {
                    if (lastStmnt.unlabeled_statement().returnStatement() == null) {
                        ErrorStack.add("must end with RETURN ("+m_typeOfReturns.toString()+")");

                    }
                }
            }
        }
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        ErrorStack.leave();
        return null;
    }


    @Override
    public void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
        String globalId = null;
        LinkedList<FormalParameter> formalParameters = null;
        ASTAttribute resultType = null;

        ProcedureBodyContext b = ctx.procedureBody();

        int first = 0;
        int nextStmnt = b.statement();
        int last = b.statement().size();
        for (first;first<last;first++){
            if (nextStmnt.unlabeled_statement().retrunStatement !==null) {
                SmallPearlParser.StatementContext nextStmnt = b.statement(first + 1);
            }

        }



//    obsolete - resultType is already in the symbol table
//    @Override
//    public Void visitResultAttribute (SmallPearlParser.ResultAttributeContext ctx) {
//      	ErrorStack.enter(ctx.resultType(),"RETURNS");
//    	m_typeOfReturns = null;
//    	if (ctx.resultType().simpleType() != null) {
//        	m_typeOfReturns = CommonUtils.getTypeDefinitionForSimpleType(ctx.resultType().simpleType());
//    	} else if (ctx.resultType().typeReference() != null) {
//    	  TypeDefinition baseType = CommonUtils.getBaseTypeForReferenceType(ctx.resultType().typeReference());
//    	  if (ctx.resultType().typeReference().assignmentProtection()!= null) {
//    	    baseType.setHasAssignmentProtection(true);
//    	    m_typeOfReturns = baseType;
//    	  }
//    	  if (ctx.resultType().typeReference().virtualDimensionList() != null) {
//    	    int dimensions =  ctx.resultType().typeReference().virtualDimensionList().commas().getChildCount();
//    	    TypeArraySpecification array = new TypeArraySpecification(baseType,dimensions);
//    	    m_typeOfReturns = array;
//    	  }
//    	} else if (ctx.resultType().ID() != null) {
//    		ErrorStack.add("TYPE not supported");
//    	}
//    	ErrorStack.leave();
//    	return null;
//    }

    @Override
    public Void visitCallStatement(SmallPearlParser.CallStatementContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check ProcedureCall: visitCallStatement");
        }

        ErrorStack.enter(ctx,"CALL");
        String procName = ctx.ID().getText();
        ProcedureEntry proc = null;

        SymbolTableEntry entry = m_currentSymbolTable.lookup(procName);

        if (entry != null) {
            if (entry instanceof ProcedureEntry) {
                if (m_debug)
                    System.out.println("Semantic: Check ProcedureCall: found call in expression");
                proc = (ProcedureEntry) entry;
                TypeDefinition resultType = proc.getResultType();

                if ( resultType != null ) {
                    ErrorStack.add("result discarded");
                }
            } else {
                ErrorStack.add("'"+ procName+"' is not of type PROC  -- is of type "+ CommonUtils.getTypeOf(entry));
            }
        } else {
            ErrorStack.add("'"+ procName+"' is not defined");
        }

        if (proc != null && ctx.listOfActualParameters() != null ) {

            int nbrActualParameters = ctx.listOfActualParameters().expression().size();
            int nbrFormatParameters = proc.getFormalParameters().size();

            if (nbrActualParameters != nbrFormatParameters) {
                ErrorStack.add("number of parameters mismatch: expected "+nbrFormatParameters + " -- got "+nbrActualParameters);
            } else {
                // check each parameter type
                for (int i=0; i<nbrActualParameters; i++) {
                    ErrorStack.enter(ctx.listOfActualParameters().expression(i),"param");
                    checkParameter(proc, ctx.listOfActualParameters().expression(i), proc.getFormalParameters().get(i));
                    ErrorStack.leave();
                }
            }

            // really necessary?
            //      visitListOfActualParameters(ctx.listOfActualParameters());
        }

        ErrorStack.leave();

        return null;
    }


    @Override
    public Void visitReturnStatement(SmallPearlParser.ReturnStatementContext ctx) {
        RuleContext parent = ctx;
        ErrorStack.enter(ctx,"RETURN");


        if (m_typeOfReturns == null && ctx.expression() != null) {
            ErrorStack.add("illegal without RETURNS in declaration");
        } else if (ctx.expression() != null) {
            // we have an expression at RETURN
            TypeDefinition exprType = m_ast.lookupType(ctx.expression());

            // can be removed if all possible types are detected
            Boolean typeIsCompatible = true;

            TypeDefinition tmpTypeOfResult = m_typeOfReturns;
            TypeDefinition tmpExprType = exprType;

            if (m_typeOfReturns != null) {
                // check for implicit dereference /reference possibilities
                // --> base types must be compatible
                if (tmpTypeOfResult instanceof TypeReference) {
                    tmpTypeOfResult = ((TypeReference)tmpTypeOfResult).getBaseType();
                }
                if (tmpExprType instanceof TypeReference) {
                    tmpExprType = ((TypeReference)tmpExprType).getBaseType();
                }

                // check compatibility of baseTypes
                if (tmpTypeOfResult instanceof TypeFixed && tmpExprType instanceof TypeFixed) {
                    if (((TypeFixed)tmpTypeOfResult).getPrecision() < ((TypeFixed) tmpExprType).getPrecision()) {
                        typeIsCompatible = false;
                    }
                } else if (tmpTypeOfResult instanceof TypeFloat && tmpExprType instanceof TypeFloat) {
                    if (((TypeFloat)tmpTypeOfResult).getPrecision() < ((TypeFloat) tmpExprType).getPrecision()) {
                        typeIsCompatible = false;
                    }
                } else if (tmpTypeOfResult instanceof TypeBit && tmpExprType instanceof TypeBit) {
                    if (((TypeBit)tmpTypeOfResult).getPrecision() < ((TypeBit) tmpExprType).getPrecision()) {
                        typeIsCompatible = false;
                    }
                } else if (tmpTypeOfResult instanceof TypeChar && tmpExprType instanceof TypeChar) {
                    if (((TypeChar)tmpTypeOfResult).getSize() < ((TypeChar) tmpExprType).getSize()) {
                        typeIsCompatible = false;
                    }
                } else if (tmpTypeOfResult instanceof TypeChar && tmpExprType instanceof TypeVariableChar) {
                    // this must be checked during runtime
                } else {
                    if (!tmpTypeOfResult.equals(tmpExprType)) {
                        typeIsCompatible = false;
                    }
                }

                if (!typeIsCompatible) {
                    ErrorStack.add("expression does not fit to RETURN type: expected "+m_typeOfReturns.toString()+" -- got "+exprType.toString());
                }

                if (m_typeOfReturns instanceof TypeReference) {
                    // check lifeCyle required
                    ASTAttribute attrRhs = m_ast.lookup(ctx.expression());
                    if (attrRhs.getVariable() != null) {
                        int level = attrRhs.getVariable().getLevel();
                        if (level >1) {
                            ErrorStack.add("life cycle of '"+attrRhs.getVariable().getName()+"' is too short");
                        }
                    } else if (attrRhs.getType() instanceof TypeProcedure) {
                        // ok - we have a procedure name
                    }
                }

            }
        }

        ErrorStack.leave();
        return null;
    }


    private void checkParameter(ProcedureEntry proc,
                                ExpressionContext expression, FormalParameter formalParameter) {
        // check types - must be equal if IDENT is set
        //               formalParameter may be larger if IDENT ist NOT SET
        //
        // check INV  on actual parameter enforces INV on formal parameter
        // check IDENT must be set for array

        // analyse expression
        TypeDefinition actualType = null;
        boolean actualIsInv = false;
        boolean actualIsArray = false;
        int actualArrayDimensions = 0;
        boolean passByIdent = false;
        TypeDefinition formalBaseType = null;
        TypeDefinition actualBaseType = null;

        VariableEntry   actualVariableEntry = null;
        ASTAttribute attr = m_ast.lookup(expression);

        if (attr != null) {
            actualType = attr.getType();
            actualIsInv = attr.isReadOnly();

            actualVariableEntry = attr.getVariable();

            if (actualType instanceof TypeArray) {
                actualArrayDimensions = ((TypeArray) actualType).getNoOfDimensions();
                actualBaseType = ((TypeArray) actualType).getBaseType();
                actualIsArray = true;
            }
        }

        if (formalParameter.passIdentical) {
            // actual parameter must be LValue
            if (actualVariableEntry == null) {
                ErrorStack.add("only variables may be passed by IDENT");
                return;  // do no further checks on this parameter
            } else {
                if( actualIsInv && !formalParameter.getAssigmentProtection()) {
                    ErrorStack.add("pass INV data as non INV parameter");
                }
            }
        }
        if (formalParameter.getType() instanceof TypeArray) {
            formalBaseType = ((TypeArray)formalParameter.getType()).getBaseType();
        } else {
            formalBaseType = formalParameter.getType();
        }

        // compare array parameters
        if (actualIsArray) {
            if (formalParameter.getType() instanceof TypeArray) {
                // both are arrays --> nbr of dimensions and baseTypes must fit
                TypeArray ta = ((TypeArray)formalParameter.getType());
                if (actualArrayDimensions != ta.getNoOfDimensions()) {
                    ErrorStack.add("dimension mismatch: expect "+ta.getNoOfDimensions()+
                            " -- got "+actualArrayDimensions);
                } else 	if (!actualBaseType.equals(ta.getBaseType())) {
                    String s = actualType.getName();
                    s = actualType.toString();
                    ErrorStack.add("type mismatch: expect ARRAY of " +((TypeArray)formalParameter.getType()).getBaseType() +
                            " -- got ARRAY of "+actualType.toString());
                }
            } else {
                ErrorStack.add("expected scalar type");
            }
        } else {
            if (formalParameter.getType() instanceof TypeArray) {
                ErrorStack.add("expected array type");
            } else {
                // treat both scalar types

                // easy stuff first -- check base types
                // if they fit we must check length for FIXED,FLOAT,CHAR,BIT
                if (formalBaseType instanceof TypeChar &&
                        actualType instanceof TypeVariableChar) {
                    // this is ok -- mark the actual parameter in the AST Attribute
                    // this will be used by the CppCodeGeneratorVisitor to instanciate
                    // a temporary variable
                    ((TypeVariableChar)attr.getType()).setBaseType(formalBaseType);

                } else if (!formalBaseType.getName().equals(actualType.getName())) {
                    ErrorStack.add("type mismatch: expected "+formalBaseType.toString()+
                            "  -- got "+actualType.toString());
                } else if (formalParameter.getType() instanceof TypeFixed) {

                    TypeFixed fp = (TypeFixed)formalParameter.getType();
                    TypeFixed ap = (TypeFixed)actualType;

                    if (formalParameter.passIdentical == false && fp.getPrecision().intValue() < ap.getPrecision().intValue()) {
                        ErrorStack.add("type mismatch: expected (not larger than) " + formalParameter.toString() + " -- got "+actualType.toString());
                    }
                    if (formalParameter.passIdentical && fp.getPrecision().intValue() != ap.getPrecision().intValue()) {
                        ErrorStack.add("type mismatch: expected " + formalParameter.toString() + " -- got "+actualType.toString());
                    }
                } else if (formalParameter.getType() instanceof TypeFloat && actualType instanceof TypeFixed) {
                    // implicit FIXED->FLOAT conversion is ok if NOT IDENT
                    TypeFloat fp = (TypeFloat)formalParameter.getType();
                    TypeFixed ap = (TypeFixed)actualType;

                    if (formalParameter.passIdentical == false && fp.getPrecision().intValue() < ap.getPrecision().intValue()) {
                        ErrorStack.add("type mismatch: expected (not larger than) " + formalParameter.toString() + " -- got "+actualType.toString());
                    }
                    if (formalParameter.passIdentical) {
                        ErrorStack.add("type mismatch: expected " + formalParameter.toString() + " -- got "+actualType.toString());
                    }
                } else if (formalParameter.getType() instanceof TypeBit) {

                    TypeBit fp = (TypeBit)formalParameter.getType();
                    TypeBit ap = (TypeBit)actualType;

                    if (formalParameter.passIdentical == false && fp.getPrecision().intValue() < ap.getPrecision().intValue()) {
                        ErrorStack.add("type mismatch: expected (not larger than) " + formalParameter.toString() + " -- got "+actualType.toString());
                    }
                    if (formalParameter.passIdentical && fp.getPrecision().intValue() != ap.getPrecision().intValue()) {
                        ErrorStack.add("type mismatch: expected " + formalParameter.toString() + " -- got "+actualType.toString());
                    }









}
