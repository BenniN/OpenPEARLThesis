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

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.util.Collections;
import java.util.LinkedList;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.smallpearl.compiler.*;
import org.smallpearl.compiler.Exception.ArgumentMismatchException;
import org.smallpearl.compiler.Exception.InternalCompilerErrorException;
import org.smallpearl.compiler.Exception.NotYetImplementedException;
import org.smallpearl.compiler.SmallPearlParser.*;
import org.smallpearl.compiler.SymbolTable.*;
import org.stringtemplate.v4.ST;

/**
 * check all real time statements
 * 
 * <ul>
 * <li> task-dcl + operations
 * <li> semaphore operations 
 * <li> bolt operations 
 * <li> interrupt operation
 * </ul>
 * 
 * 
 * @author mueller
 * 
 * Attention:
 *  visitName() sets the attributes 'm_type'.
 *  This is the reason why visitChildren(..) may only be used, if only 1 'name' is 
 *  possible in the current context. If more than 1 'name' is possible like in
 *    WHEN name§ofInterrupt ACTIVATE name§task;
 *  we must iterate this manually
 */
public class CheckRealTimeStatements extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

	private int m_verbose;
	private boolean m_debug;
	private String m_sourceFileName;
	private ExpressionTypeVisitor m_expressionTypeVisitor;
	private SymbolTableVisitor m_symbolTableVisitor;
	private SymbolTable m_symboltable;
	private SymbolTable m_currentSymbolTable;
	private ModuleEntry m_module;
	private AST m_ast = null;
    private TypeDefinition m_type=null;

    
	public CheckRealTimeStatements(String sourceFileName,
			int verbose,
			boolean debug,
			SymbolTableVisitor symbolTableVisitor,
			ExpressionTypeVisitor expressionTypeVisitor,
			AST ast){

		m_debug = debug;
		m_verbose = verbose;
		m_sourceFileName = sourceFileName;
		m_symbolTableVisitor = symbolTableVisitor;
		m_expressionTypeVisitor = expressionTypeVisitor;
		m_symboltable = symbolTableVisitor.symbolTable;
		m_currentSymbolTable = m_symboltable;
		m_ast = ast;

		Log.debug( "    Check Template");
	}

	@Override
	public Void visitModule(SmallPearlParser.ModuleContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check RT-statements: visitModule");
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
			System.out.println( "Semantic: Check RT-statements: visitProcedureDeclaration");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
		visitChildren(ctx);
		this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
		return null;
	}


	@Override
	public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check RT-statements: visitBlock_statement");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
		visitChildren(ctx);
		this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
		return null;
	}

	@Override
	public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check RT-statements: visitLoopStatement");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
		visitChildren(ctx);
		this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
		return null;
	}

	/* ----------------------------------------------------------------------- */
	/* class specify stuff starts here                                         */
	/* ----------------------------------------------------------------------- */
	@Override
	public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
		if (m_debug) {
			System.out.println( "Semantic: Check RT-statements: visitTaskDeclaration");
		}

		this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);

		// taskDeclaration :
		//ID ':' 'TASK' priority? task_main? ';' taskBody 'END' ';' cpp_inline?
		//	    ;
		// ID is already in the symbol table

		// let's check priority
		if (ctx.priority() != null) {
			checkPriority(ctx.priority().expression());
		}


		visitChildren(ctx);
		m_currentSymbolTable = m_currentSymbolTable.ascend();


		return null;
	}

	@Override
    public Void visitName(SmallPearlParser.NameContext ctx) {
	  // we must check if name is a literal --> task name
	  //                            variable -> task ref, REF() TASK

	  m_type = null;
	  
	  if (ctx != null) {
	    ASTAttribute attr = m_ast.lookup(ctx);
	    
	    //attr may be null for TRY operation
	    if (attr != null) {
	      m_type = attr.getType();

	      if (attr.getType() instanceof TypeReference) {
	        // fine, we have the current variable in the ast attributes
	        m_type = ((TypeReference)attr.getType()).getBaseType();
	        if (m_type instanceof TypeArraySpecification) {
	          m_type = ((TypeArraySpecification)m_type).getBaseType();
	        }
	      }
	    }
	  }
	  return null;
	}

	private Void checkListOfNames(SmallPearlParser.ListOfNamesContext ctx, TypeDefinition expectedType) {
      for (int i = 0; i < ctx.name().size(); i++) {
        visitName(ctx.name(i));
        if (m_type != null) {
          checkName(ctx.name(i),expectedType);
        }
      }
	  
	  return null;
	}
	
    @Override
    public Void visitSemaTry(SmallPearlParser.SemaTryContext ctx) {
      Log.debug("CheckRealtimeStatements::visitSemaphoreTry " + CommonUtils.printContext(ctx));

      ErrorStack.enter(ctx,"TRY");
      checkListOfNames(ctx.listOfNames(), new TypeSemaphore());
     
      ErrorStack.leave();

      return null;
    }

    @Override
    public Void visitSemaRequest(SmallPearlParser.SemaRequestContext ctx) {
      Log.debug("CheckRealtimeStatements:visitSemaphoreRequest " + CommonUtils.printContext(ctx));

      ErrorStack.enter(ctx,"REQUEST");
      checkListOfNames(ctx.listOfNames(), new TypeSemaphore());
      ErrorStack.leave();

      return null;
    }

    @Override
    public Void visitSemaRelease(SmallPearlParser.SemaReleaseContext ctx) {
        Log.debug("CheckRealtimeStatements:visitSemaphoreRelease " + CommonUtils.printContext(ctx));

        ErrorStack.enter(ctx,"RELEASE");
        checkListOfNames(ctx.listOfNames(), new TypeSemaphore());
        ErrorStack.leave();

        return null;
    }
    

    @Override
    public Void visitBoltReserve(SmallPearlParser.BoltReserveContext ctx) {
      Log.debug("CheckRealtimeStatements:visitBoltReserve " + CommonUtils.printContext(ctx));

      ErrorStack.enter(ctx,"RESERVE");
      checkListOfNames(ctx.listOfNames(), new TypeBolt());
      ErrorStack.leave();
      return null;
    }

    @Override
    public Void visitBoltFree(SmallPearlParser.BoltFreeContext ctx) {
      Log.debug("CheckRealtimeStatements:visitBoltFree " + CommonUtils.printContext(ctx));

      ErrorStack.enter(ctx,"FREE");
      checkListOfNames(ctx.listOfNames(), new TypeBolt());
      ErrorStack.leave();

      return null;
    }

    @Override
    public Void visitBoltEnter(SmallPearlParser.BoltEnterContext ctx) {
      Log.debug("CheckRealtimeStatements:visitBoltEnter " + CommonUtils.printContext(ctx));

      ErrorStack.enter(ctx,"ENTER");
      checkListOfNames(ctx.listOfNames(), new TypeBolt());
      ErrorStack.leave();
      return null;
    }

    @Override
    public Void visitBoltLeave(SmallPearlParser.BoltLeaveContext ctx) {
        Log.debug("CheckRealtimeStatements:visitBoltLeave " + CommonUtils.printContext(ctx));

        ErrorStack.enter(ctx,"LEAVE");
        checkListOfNames(ctx.listOfNames(), new TypeBolt());
        ErrorStack.leave();
        return null;
    }


	/**
     taskStart: startCondition? frequency? 'ACTIVATE' ID  priority? ';'   ;

     ID must be of type task
     types in startCondition and frequency must fit
     priority must be of correct type and range
	 */
	@Override
	public Void visitTaskStart(SmallPearlParser.TaskStartContext ctx) {
		ErrorStack.enter(ctx,"ACTIVATE");

       // visitChildren(ctx);
		// visitChildren() is not applicapable, since there may be more than one 
		// 'name' in the context
		
		visitName(ctx.name());        
        checkName(ctx.name(), new TypeTask());
        
    
        visitStartCondition(ctx.startCondition());

   
//		if (ctx.startCondition() instanceof SmallPearlParser.StartConditionATContext) {
//			SmallPearlParser.StartConditionATContext c = (SmallPearlParser.StartConditionATContext) ctx
//					.startCondition();
//			checkClockValue(c.expression(),"AT");
//		} else 		if (ctx.startCondition() instanceof SmallPearlParser.StartConditionAFTERContext) {
//			SmallPearlParser.StartConditionAFTERContext c = (SmallPearlParser.StartConditionAFTERContext) ctx
//					.startCondition();
//			checkDurationValue(c.expression(),"AFTER");
//		} else if (ctx.startCondition() instanceof SmallPearlParser.StartConditionWHENContext) {
//			SmallPearlParser.StartConditionWHENContext c = (SmallPearlParser.StartConditionWHENContext) ctx
//					.startCondition();
//			checkInterrupt(c,"WHEN");
//			if (c.expression()!= null) {
//				checkDurationValue(c.expression(), "AFTER");
//			}
//		}

		if (ctx.frequency() != null) {
			SmallPearlParser.FrequencyContext c = ctx.frequency();
			checkDurationValue(c.expression(0),"ALL");
		
		
			for (int i = 0; i < c.getChildCount(); i++) {
				if (c.getChild(i) instanceof TerminalNodeImpl) {
					if (((TerminalNodeImpl) c.getChild(i)).getSymbol()
							.getText().equals("ALL")) {
						// ALL is mandatory!
					} else if (((TerminalNodeImpl) c.getChild(i)).getSymbol()
							.getText().equals("UNTIL")) {
						checkClockValue(c.expression(1), "UNTIL");
					} else if (((TerminalNodeImpl) c.getChild(i)).getSymbol()
							.getText().equals("DURING")) {
					    checkDurationValue(c.expression(1), "DURING");
					} else {
						ErrorStack.addInternal("untreated alternative: "+((TerminalNodeImpl) c.getChild(i)).getSymbol()
							.getText());
					}
				}
			}
		}

//		if (ctx.priority()!= null) {
//			checkPriority(ctx.priority().expression());
//		}

		ErrorStack.leave();
		return null;
	}

	/**
      task_terminating: 'TERMINATE' name? ';' ;
	 */
	@Override 
	public Void visitTask_terminating(SmallPearlParser.Task_terminatingContext ctx) {
		ErrorStack.enter(ctx,"TERMINATE");
        visitName(ctx.name());
        if (m_type != null) {
          checkName(ctx.name(), new TypeTask());
        }
		ErrorStack.leave();

		return null;
	}

	/* 
      task_suspending : 'SUSPEND' ID? ';' ;
	 */
	@Override 
	public Void visitTask_suspending(SmallPearlParser.Task_suspendingContext ctx) {
		ErrorStack.enter(ctx,"SUSPEND");
        visitName(ctx.name());
        if (m_type != null) {
          checkName(ctx.name(), new TypeTask());
        }
		ErrorStack.leave();

		return null;
	}

	/* 
    task_preventing: 'PREVENT' ID? ';' ;
	 */
	@Override 
	public Void visitTask_preventing(SmallPearlParser.Task_preventingContext ctx) {
		ErrorStack.enter(ctx,"PREVENT");
        visitName(ctx.name());
        if (m_type != null) {
          checkName(ctx.name(), new TypeTask());
        }
		ErrorStack.leave();

		return null;
	}

	/* 
  taskResume : startCondition 'RESUME' ';'     ;
	 */
	@Override 
	public Void visitTaskResume(SmallPearlParser.TaskResumeContext ctx) {
		ErrorStack.enter(ctx,"RESUME");
		visitChildren(ctx);
//
//		// copied from startTask 
//		if (ctx.startCondition() instanceof SmallPearlParser.StartConditionATContext) {
//			SmallPearlParser.StartConditionATContext c = (SmallPearlParser.StartConditionATContext) ctx
//					.startCondition();
//			checkClockValue(c.expression(),"AT");
//		} else 		if (ctx.startCondition() instanceof SmallPearlParser.StartConditionAFTERContext) {
//			SmallPearlParser.StartConditionAFTERContext c = (SmallPearlParser.StartConditionAFTERContext) ctx
//					.startCondition();
//			checkDurationValue(c.expression(),"AFTER");
//		} else if (ctx.startCondition() instanceof SmallPearlParser.StartConditionWHENContext) {
//			SmallPearlParser.StartConditionWHENContext c = (SmallPearlParser.StartConditionWHENContext) ctx
//					.startCondition();
//			checkInterrupt(c,"WHEN");
//		}
		ErrorStack.leave();

		return null;
	}

	/* 
    taskContinuation : startCondition? 'CONTINUE' ID? priority? ';' ;
	 */
	@Override 
	public Void visitTaskContinuation(SmallPearlParser.TaskContinuationContext ctx) {
		ErrorStack.enter(ctx,"CONTINUE");
		visitName(ctx.name());
		if (m_type != null) {
          checkName(ctx.name(), new TypeTask());
		}
		
		visitStartCondition(ctx.startCondition());
		ErrorStack.leave();
		
		visitPriority(ctx.priority());

		return null;
	}


    @Override 
    public Void visitPriority(SmallPearlParser.PriorityContext ctx) {
      if (ctx != null) {
        ErrorStack.enter(ctx,"PRIO");
        checkPriority(ctx.expression());
        ErrorStack.leave();
      }
      return null;
    }
    
    

    @Override 
    public Void visitStartCondition(SmallPearlParser.StartConditionContext ctx) {
      if (ctx != null) {
         visitChildren(ctx);
      }
      return null;
    }

    

    @Override 
    public Void visitStartConditionAT(SmallPearlParser.StartConditionATContext ctx) {
        checkClockValue(ctx.expression(),"AT");
  
        return null;
    }
    
    
    @Override 
    public Void visitStartConditionAFTER(SmallPearlParser.StartConditionAFTERContext ctx) {
          checkDurationValue(ctx.expression(),"AFTER");
     
        return null;
    }
    
    @Override 
    public Void visitStartConditionWHEN(SmallPearlParser.StartConditionWHENContext ctx) {
        checkInterrupt(ctx.name(),"WHEN");
        return null;
    }
    	
   
	/*
     disableStatement : 'DISABLE' ID ';'     ;
	 */
	@Override
	public Void visitDisableStatement(DisableStatementContext ctx) {
        checkInterrupt(ctx.name(),"DISABLE");
		return null;
	}


	/*
    enableStatement : 'ENABLE' ID ';'     ;
	 */
	@Override
	public Void visitEnableStatement(EnableStatementContext ctx) {
		checkInterrupt(ctx.name(),"ENABLE");
		return null;
	}

	/*
   triggerStatement : 'TRIGGER' ID ';'     ;
	 */
	@Override
	public Void visitTriggerStatement(TriggerStatementContext ctx) {
		checkInterrupt(ctx.name(),"TRIGGER");
		return null;
	}


	private void checkPriority(SmallPearlParser.ExpressionContext ctx) {
		ErrorStack.enter(ctx);
		ASTAttribute attr = m_ast.lookup(ctx);
		TypeDefinition t = m_ast.lookupType(ctx);

		if (t instanceof TypeFixed) {
			if (attr.isReadOnly()) {
				long p = attr.getConstantFixedValue().getValue();
				if (p< Defaults.BEST_PRIORITY || p > Defaults.LOWEST_PRIORITY) {
					ErrorStack.add("must be in ["+Defaults.BEST_PRIORITY+","+Defaults.LOWEST_PRIORITY+"]");
				}
			}
		} else {
			ErrorStack.add("must be of type FIXED");
		}
		ErrorStack.leave();

	}

	private void checkClockValue(ExpressionContext ctx, String prefix) {
		TypeDefinition t = m_ast.lookupType(ctx);
		

		ErrorStack.enter(ctx,prefix);
		if (!(t instanceof TypeClock)) {
			ErrorStack.add("must be of type CLOCK  -- but is of "+t.toString());
		}
		ErrorStack.leave();

	}


	private void checkDurationValue(ExpressionContext ctx, String prefix) {
		ASTAttribute attr = m_ast.lookup(ctx);
		TypeDefinition t = getEffectiveType(ctx);
		
		ErrorStack.enter(ctx,prefix);
		if (!(t instanceof TypeDuration)) {
			ErrorStack.add("must be of type DURATION  -- but is of "+t.toString());
		} else {
			if (attr.isReadOnly()) {
				ConstantDurationValue cd = attr.getConstantDurationValue();
				if (cd.getValue() <= 0) {
					ErrorStack.add("must be > 0");
				}
			}
		}
		ErrorStack.leave();
	}

	private void checkInterrupt(NameContext ctx, String prefix) {
		ErrorStack.enter(ctx,prefix);
	    visitName(ctx);
	    checkName(ctx, new TypeInterrupt());
		ErrorStack.leave();
	}

	private void checkName(SmallPearlParser.NameContext ctx, TypeDefinition expectedType) {
	  if (!m_type.equals(expectedType)) {
	    ErrorStack.add(ctx,null,"expected type '"+expectedType.toString()+ "' -- got '"+ 
	       m_type.toString()+ "'");
	    }
	}
	

//	private void checkInterrupt(NameContext iName) {
//	  visitName(iName);
//	  checkName(iName, new TypeInterrupt());
//	  
////		if (iName != null) {
////			String intName = iName.toString();
////			SymbolTableEntry se = m_currentSymbolTable.lookup(intName);	
////			if (se == null) {
////				ErrorStack.add("'"+ intName+"' is not defined");
////			} else {
////				if (!(se instanceof InterruptEntry)) {
////					if (se instanceof VariableEntry) {
////						ErrorStack.add("'"+intName+"' must be INTERRUPT -- but is "+ ((VariableEntry)se).getType().toString());
////					} else if (se instanceof TaskEntry) {
////						ErrorStack.add("'"+intName+"' must be INTERRUPT -- but is TASK");
////					} else {
////						ErrorStack.add("'"+intName+"' must be INTERRUPT -- but is "+ se.getClass().getTypeName());
////					}
////				}
////			}
////		}
//	}
	
	private TypeDefinition getEffectiveType(ParserRuleContext ctx) {
	  
	  ASTAttribute attr = m_ast.lookup(ctx);
	  TypeDefinition t = attr.getType();
	  
	  VariableEntry ve = attr.getVariable();
      if (ve != null) {
        if (ve.getType() instanceof TypeReference) {
          t =((TypeReference)t).getBaseType();
          if ( ( (TypeReference)(ve.getType())  ).getBaseType() instanceof TypeArraySpecification) {
            if (ctx instanceof BaseExpressionContext) {
              BaseExpressionContext bc = (BaseExpressionContext)ctx;
              if (bc.primaryExpression() != null && bc.primaryExpression().name() != null) {
                NameContext n =  bc.primaryExpression().name() ;
                if (n.listOfExpression() != null) {
                   t = ((TypeArraySpecification)t).getBaseType();
                }
              }
            }
          }
        }
      }

       
	  if (t instanceof TypeReference) {
	    t = ((TypeReference)t).getBaseType();
	  }

	  return t;
	}


}