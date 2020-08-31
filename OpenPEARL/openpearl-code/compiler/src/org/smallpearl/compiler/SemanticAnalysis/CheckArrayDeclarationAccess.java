package org.smallpearl.compiler.SemanticAnalysis;


import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.smallpearl.compiler.*;
import org.smallpearl.compiler.SmallPearlParser.ExpressionContext;
import org.smallpearl.compiler.Exception.*;
import org.smallpearl.compiler.SymbolTable.FormalParameter;
import org.smallpearl.compiler.SymbolTable.ModuleEntry;
import org.smallpearl.compiler.SymbolTable.ProcedureEntry;
import org.smallpearl.compiler.SymbolTable.SymbolTable;
import org.smallpearl.compiler.SymbolTable.SymbolTableEntry;
import org.smallpearl.compiler.SymbolTable.VariableEntry;

/**
 * check for array boundaries: lwb <= upb
 * check indices
 *   - type to be FIXED with precision <= 31
 *   - number as required by the array denotation
 *   - values in range if value is constant and array is no formal parameter 
 *   
 *   
 * @author mueller
 *
 */
public class CheckArrayDeclarationAccess extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

	private int m_verbose;
	private boolean m_debug;
	private String m_sourceFileName;
	private ExpressionTypeVisitor m_expressionTypeVisitor;
	private SymbolTableVisitor m_symbolTableVisitor;
	private SymbolTable m_symboltable;
	private SymbolTable m_currentSymbolTable;
	private ModuleEntry m_module;
	private AST m_ast = null;

	public CheckArrayDeclarationAccess(String sourceFileName,
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

		Log.debug( "    Check Array Declaration and Access");
	}

	@Override
	public Void visitModule(SmallPearlParser.ModuleContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check Array Declaration and Access: visitModule");
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
			System.out.println( "Semantic: Check Array Declaration and Access: visitProcedureDeclaration");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
		visitChildren(ctx);
		this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
		return null;
	}

	@Override
	public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check Array Declaration and Access: visitTaskDeclaration");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
		visitChildren(ctx);
		m_currentSymbolTable = m_currentSymbolTable.ascend();
		return null;
	}

	@Override
	public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check Array Declaration and Access: visitBlock_statement");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
		visitChildren(ctx);
		this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
		return null;
	}

	@Override
	public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check Array Declaration and Access: visitLoopStatement");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
		visitChildren(ctx);
		this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
		return null;
	}

	@Override
	public Void visitArrayDenotation(SmallPearlParser.ArrayDenotationContext ctx) {

		if (ctx != null) {

			for (int i = 0; i < ctx.ID().size(); i++) {

				String s = ctx.ID().get(i).toString();
				SymbolTableEntry se = m_currentSymbolTable.lookup(s);
				if (!(se instanceof VariableEntry )) {
					throw new InternalError("expected variable entry");
				}
				VariableEntry ve = (VariableEntry)se;
				if(!(ve.getType() instanceof TypeArray)) {
					throw new InternalError("expected variable entry with TypeArray");
				}

				TypeArray ta = (TypeArray) ve.getType();
				ArrayList<ArrayDimension> dims = ta.getDimensions();

				for (i=0; i<ta.getNoOfDimensions(); i++) {
					ErrorStack.enter(dims.get(i).getCtx(),"array boundary");
					if (dims.get(i).getLowerBoundary() > dims.get(i).getUpperBoundary()) {
						ErrorStack.add("lower boundary must not exceed upper boundary");
					}
					ErrorStack.leave();
				}		

			}
		}


		return null;
	}


	@Override
	public Void visitPrimaryExpression(SmallPearlParser.PrimaryExpressionContext ctx) {
		Log.debug("ExpressionTypeVisitor:visitPrimaryExpression:ctx" + CommonUtils.printContext(ctx));

		ErrorStack.enter(ctx,"array index");
		if (ctx.name() != null && ctx.name().ID() != null) {
			SymbolTableEntry entry = m_currentSymbolTable.lookup(ctx.name().ID().getText());

	//		String s = ctx.toStringTree();
	//		System.out.println(ctx.ID().toString());
			if ( entry == null ) {
				throw  new UnknownIdentifierException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
			}

			if (entry instanceof VariableEntry) {

				VariableEntry variable = (VariableEntry) entry;

				if ( variable.getType() instanceof TypeArray ) {
					Boolean checkIndexValue = true;
					if (variable instanceof FormalParameter) {
						checkIndexValue = false;
					}
					// expressionResult should be TypeArray if no indices are given
					if (ctx.name().listOfExpression() != null && ctx.name().listOfExpression().expression().size() > 0) {
						TypeArray ta = (TypeArray) variable.getType();
						checkIndices(ta,ctx.name().listOfExpression().expression(), checkIndexValue);

					} else {
						// we got no indices -_> complete array as parameter
					}
				} 

			}    
		}


		ErrorStack.leave();


		return null;
	}
	

	@Override
	public Void visitAssignment_statement(SmallPearlParser.Assignment_statementContext ctx) {
		Log.debug("CheckAssignment:visitAssignment_statement:ctx" + CommonUtils.printContext(ctx));

		String id = null;
		ErrorStack.enter(ctx,"array index");

//		if ( ctx.stringSelection() != null ) {
//			if ( ctx.stringSelection().charSelection() != null ) {
//				id = ctx.stringSelection().charSelection().name().getText();
//			}
//			else  if (ctx.stringSelection().bitSelection() != null) {
//				id = ctx.stringSelection().bitSelection().name().getText();
//			} else {
//				throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//			}
//		}
//		else {
//			id = ctx.name().getText();
//		}
		id = ctx.name().ID().toString();


		SymbolTableEntry lhs = m_currentSymbolTable.lookup(id);
		if ( lhs instanceof VariableEntry) {
			VariableEntry variable = (VariableEntry) lhs;
			if (variable.getType() instanceof TypeArray) {
				Boolean checkIndexValue = true;
				if (variable instanceof FormalParameter) {
					checkIndexValue = false;
				}
				TypeArray ta = (TypeArray)(variable.getType());
				
				if (ctx.name().listOfExpression()!= null && 
						ctx.name().listOfExpression().expression().size() > 0 ) {
					checkIndices(ta,ctx.name().listOfExpression().expression(),checkIndexValue);
				}
			}
		}
		ErrorStack.leave();
		visitChildren(ctx);
		return null;
	}

	private void checkIndices(TypeArray ta,	List<ExpressionContext> expression, Boolean checkIndexValue) {
		int arrayDimensions = ta.getNoOfDimensions();
		

		if (arrayDimensions != expression.size()) {
			ErrorStack.add("wrong number of indices: expected "+arrayDimensions+
					" -- got "+expression.size());
		}
		for (int d=0; d<ta.getNoOfDimensions(); d++) {
			ErrorStack.enter(expression.get(d));
					
			if (m_ast.lookupType(expression.get(d)) instanceof TypeFixed) {
				if (checkIndexValue) {
				 
				ASTAttribute attr = m_ast.lookup(expression.get(d));
				if (attr.isReadOnly()) {
					if (attr.getConstantFixedValue()!= null) {
					long val = attr.getConstantFixedValue().getValue();
					
					if (val < ta.getDimensions().get(d).getLowerBoundary() ||
						val > ta.getDimensions().get(d).getUpperBoundary() ) {
						ErrorStack.add("index out of range ["+ta.getDimensions().get(d).getLowerBoundary()+":"+
								ta.getDimensions().get(d).getUpperBoundary()+"]");
					}
					}
				}
				}
				TypeFixed tf = (TypeFixed)(m_ast.lookupType(expression.get(d)));
				int precision = tf.getPrecision().intValue();
				if (precision > 31) {
					ErrorStack.add("type for index must not exceed FIXED(31)");
				}
			} else {
				ErrorStack.add("index must be of type FIXED"+ " -- got " + m_ast.lookupType(expression.get(d)).toString());
			}
			ErrorStack.leave();
		}

	}

}
