/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2020 Rainer Mueller & Marcel Schaible
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
 *     derived from this software without specific prior written permissision.
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

package org.smallpearl.compiler;

//import org.smallpearl.compiler.SmallPearlParser.AdditiveExpressionContext;
//import com.sun.org.apache.xpath.internal.operations.Mod;
import org.smallpearl.compiler.SmallPearlParser.ExpressionContext;
//import org.smallpearl.compiler.SmallPearlParser.GeRelationalExpressionContext;
//import org.smallpearl.compiler.SmallPearlParser.ListOfExpressionContext;
import org.smallpearl.compiler.SmallPearlParser.NameContext;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.smallpearl.compiler.Exception.*;
import org.smallpearl.compiler.SymbolTable.*;
//import org.stringtemplate.v4.ST;

import java.util.LinkedList;

/*
 * notes about implicit dereferenciations:
 *  1) there are xxx cases of implicit dereferenciations
 *     the deferenciation inside of the rule name
 *      eq x STRUCT [ a REF STRUCT [ b FIXED,
 *                                   c REF FIXED,
 *                                   d REF PROC(FIXED) RETURNS(FIXED),
 *                                   e REF PROC RETURNS(FIXED),
 *                                   f REF PROC RETURNS(REF FIXED),
 *                                   g REF PROC ]]; 
 *      x.a.b need implicit dereferenciation on lhs and rhs
 *            and is of type FIXED
 *      x.a.c is REF FIXED
 *  2) 1) hold also for intermediated array elements like
 *     x STRUCT [ a(10) REF STRUCT [ b FIXED, .. ]
 *       x.a(2).b
 *  3) x.a.d(3) is a function call with implicit derefenciation
 *  4) x.a.e may be TypeProcedure or TypeFixed. This is decided by the usage in assigment
 *     or procedure parameter
 *  5) x.a.f may be on lhs and rhs!
 *     on lhs: function call with result REF FIXED with may be used with CONT
 *     on rhs: is TypProcedure or function call depending on the usage
 *  6) x.a.g depends on usage: ether REF PROC or procedure call
 *  7) REFs should not become dereferenced it
 *     rhs of assignments if lhs is a REF
 *     as procedure parameters is the formal parameter is a REF
 *     in expressions with IS or ISNT
 *   
 *  current state ( 2020-05-21)  
 *  * STRUCT is not tested
 *  * REF PROC RETURNS(..) is not supported
 *  * REF PROC(...) works        
 */
public  class ExpressionTypeVisitor extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

    private int m_verbose;
    private boolean m_debug;
    private SymbolTableVisitor m_symbolTableVisitor;
    private org.smallpearl.compiler.SymbolTable.SymbolTable m_symboltable;
    private org.smallpearl.compiler.SymbolTable.SymbolTable m_currentSymbolTable;
    private org.smallpearl.compiler.SymbolTable.ModuleEntry m_module;
    private ConstantPool m_constantPool;
    private Integer m_currFixedLength = null;
    private boolean m_calculateRealFixedLength;
    private org.smallpearl.compiler.AST m_ast;
    private SymbolTableEntry m_name = null;
    private TypeDefinition m_type = null;
    private int m_nameDepth = 0;
    private boolean m_autoDereference=false;
    private boolean m_isFunctionCall = false;

    public ExpressionTypeVisitor(int verbose, boolean debug, SymbolTableVisitor symbolTableVisitor, 
        ConstantPool constantPool, org.smallpearl.compiler.AST ast) {

        m_verbose = verbose;
        m_debug = debug;

        m_symbolTableVisitor = symbolTableVisitor;
        m_symboltable = symbolTableVisitor.symbolTable;
        this.m_constantPool = constantPool;
        m_ast = ast;
        m_name = null;
        m_type = null;
        m_nameDepth = 0;

        Log.info("Semantic Check: Attributing parse tree with expression type information");

        LinkedList<ModuleEntry> listOfModules = this.m_symboltable.getModules();

        if (listOfModules.size() > 1) {
            throw new NotYetImplementedException("Multiple modules", 0, 0);
        }

        m_module = listOfModules.get(0);
        m_currentSymbolTable = m_module.scope;
        m_calculateRealFixedLength = false;
    }

    @Override
    public Void visitBaseExpression(SmallPearlParser.BaseExpressionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitBaseExpression:ctx" + CommonUtils.printContext(ctx));

        if (ctx.primaryExpression() != null) {
            visitPrimaryExpression(ctx.primaryExpression());
            ASTAttribute expressionResult = m_ast.lookup(ctx.primaryExpression());
            if (expressionResult != null) {
                m_ast.put(ctx, expressionResult);

                Log.debug("ExpressionTypeVisitor: visitBaseExpression: exp=" + ctx.primaryExpression().getText());
                Log.debug("ExpressionTypeVisitor: visitBaseExpression: res=(" + expressionResult + ")");
            }
        }

        return null;
    }

    @Override
    public Void visitPrimaryExpression(SmallPearlParser.PrimaryExpressionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitPrimaryExpression:ctx" + CommonUtils.printContext(ctx));
        
        if (ctx.literal() != null) {
            visitLiteral(ctx.literal());
            ASTAttribute expressionResult = m_ast.lookup(ctx.literal());
            if (expressionResult != null) {
                m_ast.put(ctx, expressionResult);
            } else {
              ErrorStack.addInternal(ctx,"literal","no AST attribute found");
            }
        } else if (ctx.name() != null) {
            visitName(ctx.name());
            // visitName returns in m_type the type of the complete name -element
            // the attribute is set already in visitName for the given context
            //  or error messages were emitted
            // now: just copy the result type 
            ASTAttribute expressionResult = m_ast.lookup(ctx.name());
            m_ast.put(ctx, expressionResult);

        } else if (ctx.semaTry() != null) {
            visit(ctx.semaTry());
            ASTAttribute expressionResult= m_ast.lookup(ctx.semaTry());
            if (expressionResult != null) {
                m_ast.put(ctx, expressionResult);
            } else {
              ErrorStack.addInternal(ctx,"TRY", "no AST attribute found");
            }

        } else if (ctx.stringSelection() != null) {
          visitStringSelection(ctx.stringSelection());
          ASTAttribute expressionResult = m_ast.lookup(ctx.stringSelection());
          if (expressionResult != null) {
            m_ast.put(ctx, expressionResult);
          }
        } else if (ctx.expression() != null) {
            if ( ctx.expression() !=  null) {
                visit(ctx.expression());

                ASTAttribute expressionResult = m_ast.lookup(ctx.expression());
                if (expressionResult != null) {
                    m_ast.put(ctx, expressionResult);
                }
            }
        }
        
        return null;
    }

    @Override
    public Void visitTaskFunction(SmallPearlParser.TaskFunctionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitTaskFunction");
        Log.debug("ExpressionTypeVisitor:visitTaskFunction:ctx" + CommonUtils.printContext(ctx));
        SymbolTableEntry se = null;
        visitChildren(ctx);
        TypeTask type = new TypeTask();

        if (ctx.expression() != null) {
          ASTAttribute tsk = m_ast.lookup(ctx.expression());
          se = tsk.getSymbolTableEntry();
          if (!(se instanceof TaskEntry ) ) {
            ErrorStack.add(ctx.expression(),"TASK","need task -- got "+se.getClass().getSimpleName());
          }
        }
        ASTAttribute expressionResult = new ASTAttribute(type,se);
        m_ast.put(ctx, expressionResult);

        return null;
    }
    
    @Override
    public Void visitPrioFunction(SmallPearlParser.PrioFunctionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitPrioFunction");
        Log.debug("ExpressionTypeVisitor:visitPrioFunction:ctx" + CommonUtils.printContext(ctx));
        SymbolTableEntry se = null;
        visitChildren(ctx);
        TypeFixed type = new TypeFixed(15);
       
        if (ctx.expression() != null) {
          ASTAttribute tsk = m_ast.lookup(ctx.expression());
          se = tsk.getSymbolTableEntry();
          if (!(se instanceof TaskEntry ) ) {
            ErrorStack.add(ctx.expression(),"PRIO","need task -- got "+se.getClass().getSimpleName());
          }
        }
        ASTAttribute expressionResult = new ASTAttribute(type,se);
        m_ast.put(ctx, expressionResult);

        return null;
    }

    private ASTAttribute treatFixedFloatDyadic(TypeDefinition type1, TypeDefinition type2, Boolean isReadOnly, 
                                               ASTAttribute op1, ASTAttribute op2, String operator) {
           ASTAttribute res = null;
      
      
      Integer precision = Math.max(type1.getPrecision(), type2.getPrecision());
      
      if (type1 instanceof TypeFixed && type2 instanceof TypeFixed) {
        res = new ASTAttribute(new TypeFixed(precision), isReadOnly);
        if (m_debug)
            System.out.println("ExpressionTypeVisitor: AdditiveExpression: rule#1");
    } else if (type1 instanceof TypeFixed && type2 instanceof TypeFloat) {
        res = new ASTAttribute(new TypeFloat(precision), isReadOnly);
        if (m_debug)
            System.out.println("ExpressionTypeVisitor: AdditiveExpression: rule#2");
    } else if (type1 instanceof TypeFloat && type2 instanceof TypeFixed) {
        res = new ASTAttribute(new TypeFloat(precision), isReadOnly);
        if (m_debug)
            System.out.println("ExpressionTypeVisitor: AdditiveExpression: rule#3");
    } else if (type1 instanceof TypeFloat && type2 instanceof TypeFloat) {
        res = new ASTAttribute(new TypeFloat(precision), isReadOnly);
        if (m_debug)
            System.out.println("ExpressionTypeVisitor: AdditiveExpression: rule#4");
    } else if (type1 instanceof TypeDuration && type2 instanceof TypeDuration) {
        res = new ASTAttribute(new TypeDuration(), isReadOnly);

        if (m_debug)
            System.out.println("ExpressionTypeVisitor: AdditiveExpression: rule#5");
    } else if (type1 instanceof TypeDuration && type2 instanceof TypeClock) {
        res = new ASTAttribute(new TypeClock(), isReadOnly);

        if (m_debug)
            System.out.println("ExpressionTypeVisitor: AdditiveExpression: rule#6");
    } else if (type1 instanceof TypeClock && type2 instanceof TypeDuration) {
        res = new ASTAttribute(new TypeClock(), isReadOnly);

        if (m_debug)
            System.out.println("ExpressionTypeVisitor: AdditiveExpression: rule#7");
    } else {
        ErrorStack.add("type mismatch: "+op1.getType().toString() + operator + op2.getType() + " not possible");
        // simulate result type as type of lhs? or return null
        //res = op1; -- let's return null
    }

      return res;
    }
    
    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   | Type of   | Result type | Meaning of operation
    //            | operand 1 | operand 2 |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // op1 + op2  | FIXED(g1) | FIXED(g2) | FIXED(g3)   | addition of the values of
    //            | FIXED(g1) | FLOAT(g2) | FLOAT(g3)   |the operands op1 and op2
    //            | FLOAT(g1) | FIXED(g2) | FLOAT(g3)   |
    //            | FLOAT(g1) | FLOAT(g2) | FLOAT(g3)   | g3 = max (g1, g2)
    //            | DURATION  | DURATION  | DURATION    |
    //            | DURATION  | CLOCK     | CLOCK       |
    //            | CLOCK     | DURATION  | CLOCK       |

    @Override
    public Void visitAdditiveExpression(SmallPearlParser.AdditiveExpressionContext ctx) {
      ASTAttribute op1;
      ASTAttribute op2;
      ASTAttribute res=null;

      Log.debug("ExpressionTypeVisitor:visitAdditiveExpression");
      Log.debug("ExpressionTypeVisitor:visitAdditiveExpression:ctx" + CommonUtils.printContext(ctx));

      visit(ctx.expression(0));
      visit(ctx.expression(1));
      
      op1 = saveGetAttribute(ctx.expression(0), ctx, "add", "no AST attribute found for lhs of operation +");
      op2 = saveGetAttribute(ctx.expression(1), ctx, "add", "no AST attribute found for rhs of operation +");
      
   
      if (op1 != null && op2 != null) {
        Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();

        // implicit dereferences
        TypeDefinition type1 = op1.getType();
        if (type1 instanceof TypeReference) {
          type1 = ((TypeReference) type1).getBaseType();
          isReadOnly = false;   // let's do the evaluation during runtime 
        }

        TypeDefinition type2 = op2.getType();
        if (type2 instanceof TypeReference) {
          // implicit dereference
          type2 = ((TypeReference) type2).getBaseType();
          isReadOnly = false;   // let's do the evaluation during runtime 
        }

        ErrorStack.enter(ctx);

        if (type1 instanceof TypeDuration && type2 instanceof TypeDuration) {
          res = new ASTAttribute(new TypeDuration(), isReadOnly);

          Log.debug("ExpressionTypeVisitor: AdditiveExpression: rule#5");
        } else if (type1 instanceof TypeDuration && type2 instanceof TypeClock) {
          res = new ASTAttribute(new TypeClock(), isReadOnly);

          Log.debug("ExpressionTypeVisitor: AdditiveExpression: rule#6");
        } else if (type1 instanceof TypeClock && type2 instanceof TypeDuration) {
          res = new ASTAttribute(new TypeClock(), isReadOnly);

          Log.debug("ExpressionTypeVisitor: AdditiveExpression: rule#7");
        } else {
          res = treatFixedFloatDyadic(type1, type2, isReadOnly, op1, op2, "+");
        }
        ErrorStack.leave();

      }
      m_ast.put(ctx, res);
      
      
      return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   | Type of   | Result type | Meaning of operation
    //            | operand 1 | operand 2 |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // op1 - op2  | FIXED(g1) | FIXED(g2) | FIXED(g3)   | subtraction of the values of
    //            | FIXED(g1) | FLOAT(g2) | FLOAT(g3)   | the operands op1 and op2
    //            | FLOAT(g1) | FIXED(g2) | FLOAT(g3)   |
    //            | FLOAT(g1) | FLOAT(g2) | FLOAT(g3)   | g3 = max (g1, g2)
    //            | DURATION  | DURATION  | DURATION    |
    //            | CLOCK     | DURATION  | CLOCK       |
    //            | CLOCK     | CLOCK     | DURATION    |



    @Override
    public Void visitSubtractiveExpression(SmallPearlParser.SubtractiveExpressionContext ctx) {
      ASTAttribute op1;
      ASTAttribute op2;
      ASTAttribute res=null;

      Log.debug("ExpressionTypeVisitor:visitSubtractiveExpression");
      Log.debug("ExpressionTypeVisitor:visitSubtractiveExpression:ctx" + CommonUtils.printContext(ctx));

      visit(ctx.expression(0));
      visit(ctx.expression(1));


      
      op1 = saveGetAttribute(ctx.expression(0), ctx, "sub", "no AST attribute found for lhs of operation -");
      op2 = saveGetAttribute(ctx.expression(1), ctx, "sub", "no AST attribute found for rhs of operation -");
      
      if (op1 != null && op2 != null) {
        Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
        
        // implicit dereferences
        TypeDefinition type1 = op1.getType();
        if (type1 instanceof TypeReference) {
          type1 = ((TypeReference) type1).getBaseType();
          isReadOnly = false;   // let's do the evaluation during runtime 
        }

        TypeDefinition type2 = op2.getType();
        if (type2 instanceof TypeReference) {
          // implicit dereference
          type2 = ((TypeReference) type2).getBaseType();
          isReadOnly = false;   // let's do the evaluation during runtime 
        }

        ErrorStack.enter(ctx);

        if (type1 instanceof TypeDuration && type2 instanceof TypeDuration) {
          res = new ASTAttribute(new TypeDuration(), isReadOnly);

          Log.debug("ExpressionTypeVisitor: SubtractiveExpression: rule#5");
        } else if (type1 instanceof TypeClock && type2 instanceof TypeDuration) {
          res = new ASTAttribute(new TypeClock(), isReadOnly);


          Log.debug("ExpressionTypeVisitor: SubtractiveExpression: rule#6");
        } else if (type1 instanceof TypeClock && type2 instanceof TypeClock) {
          res = new ASTAttribute(new TypeDuration(), isReadOnly);

          Log.debug("ExpressionTypeVisitor: SubtractiveExpression: rule#7");
        } else {
          res = treatFixedFloatDyadic(type1, type2, isReadOnly, op1, op2, "-");
        }
        ErrorStack.leave();

      }
      m_ast.put(ctx, res);

      return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Monadic operators for numerical, temporal
    //            and bit values
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   |           | Result type | Meaning of operation
    //            | operand   |           |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // + op       | FIXED(g)  |           | FIXED(g)    | identity
    //            | FLOAT(g)  |           | FLOAT(g)    |
    //            | DURATION  |           | DURATION    |

    @Override
    public Void visitUnaryAdditiveExpression(SmallPearlParser.UnaryAdditiveExpressionContext ctx) {
          ASTAttribute op;
          ASTAttribute res;

          Log.debug("ExpressionTypeVisitor:visitUnaryAdditiveExpression:ctx" + CommonUtils.printContext(ctx));

          
          visit(ctx.expression());

          ErrorStack.enter(ctx,"unary +");
          op = m_ast.lookup(ctx.expression());
          res = enshureFixedFloatDuration(op,"UnaryAdditive" );
          m_ast.put(ctx,res);
          ErrorStack.leave();
          
          return null;
      }      
      
     
    //
    // Reference: OpenPEARL Language Report 6.1 Monadic operators for numerical, temporal
    //            and bit values
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   |           | Result type | Meaning of operation
    //            | operand   |           |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // - op       | FIXED(g)  |           | FIXED(g)    | changing the sign of op
    //            | FLOAT(g)  |           | FLOAT(g)    |
    //            | DURATION  |           | DURATION    |

    @Override
    public Void visitUnarySubtractiveExpression(SmallPearlParser.UnarySubtractiveExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitUnarySubtractiveExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());

        ErrorStack.enter(ctx,"unary -");
        op = m_ast.lookup(ctx.expression());
        res = enshureFixedFloatDuration(op,"UnarySubstractive" );
        m_ast.put(ctx,res);
        ErrorStack.leave();
        return null;
    }  
  

    //
    // Reference: OpenPEARL Language Report 6.1 Monadic operators for numerical, temporal
    //           and bit values
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   |           | Result type | Meaning of operation
    //            | operand   |           |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // NOT op     | BIT(lg)   |           | BIT(lg)     | inverting all bit positions of op

    @Override
    public Void visitNotExpression(SmallPearlParser.NotExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitNotExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = m_ast.lookup(ctx.expression());

        ErrorStack.enter(ctx,"NOT");

        if (op == null) {
            //throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          ErrorStack.addInternal("no AST attribute found");
        } else { 

          if (op.getType() instanceof TypeBit) {
            res = new ASTAttribute(new TypeBit(((TypeBit) op.getType()).getPrecision()), op.isReadOnly());
            m_ast.put(ctx, res);

            if (m_debug)
                System.out.println("ExpressionTypeVisitor: NotExpression: rule#1");
          } else {
            ErrorStack.add("expected type BIT -- got type "+op.getType().toString());
//            throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        }
        
        ErrorStack.leave();
        
        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Monadic operators for numerical, temporal
    //           and bit values
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   |           | Result type | Meaning of operation
    //            | operand   |           |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // ABS op     | FIXED(g)  |           | FIXED(g)    | absolute value of op
    //            | FLOAT(g)  |           | FLOAT(g)    |
    //            | DURATION  |           | DURATION    |

    @Override
    public Void visitAbsExpression(SmallPearlParser.AbsExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitAbsExpression:ctx" + CommonUtils.printContext(ctx));

        
        visit(ctx.expression());

        ErrorStack.enter(ctx,"ABS");

        op = m_ast.lookup(ctx.expression());
        res = enshureFixedFloatDuration(op,"Abs" );
        m_ast.put(ctx,res);
        ErrorStack.leave();
        return null;
    }

    /**
     * check types for operations monadic +, moadic -, ABS and SIGN
     * 
     * @param op the ASTAttribute of the current operand 
     * @param operation sting with the opreation like 'UnaryAdditive' or 'Abs' for the error messages
     * @return an ASTAttribute with the same type if the type in op is FIXED,FLOAT or DURATION 
     *      or null, if a different type is detected 
     */
    private ASTAttribute enshureFixedFloatDuration(ASTAttribute op,String operation) {
      ASTAttribute res;

      if (op == null) {
        ErrorStack.addInternal("no AST attribute found for "+operation);
      }

      if (op.getType() instanceof TypeFixed) {
        res = new ASTAttribute(new TypeFixed(((TypeFixed) op.getType()).getPrecision()), op.isReadOnly());

        if (m_debug)
          System.out.println("ExpressionTypeVisitor: "+operation+"Expression: rule#1");
      } else if (op.getType() instanceof TypeFloat) {
        res = new ASTAttribute(new TypeFloat(((TypeFloat) op.getType()).getPrecision()), op.isReadOnly());

        if (m_debug)
          System.out.println("ExpressionTypeVisitor: "+operation+"Expression: rule#2");
      } else if (op.getType() instanceof TypeDuration) {
        res = new ASTAttribute(new TypeDuration());

        if (m_debug)
          System.out.println("ExpressionTypeVisitor: "+operation+"Expression: rule#3");
      } else {

        ErrorStack.add("type '" + op.getType().getName()+ "' not allowed");
        res = null;
      }
      return res;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Monadic operators for numerical, temporal
    //           and bit values
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   |           | Result type | Meaning of operation
    //            | operand   |           |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // SIGN op    | FIXED(g)  |           | FIXED(1)    | determining the sign of op
    //            | FLOAT(g)  |           |             |  1 for op > 0
    //            | DURATION  |           |             |  0 for op = 0
    //            |           |           |             | -1 for op < 0

    @Override
    public Void visitSignExpression(SmallPearlParser.SignExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitSignsExpression:ctx" + CommonUtils.printContext(ctx));

        
        visit(ctx.expression());
        
        op = m_ast.lookup(ctx.expression());
        ErrorStack.enter(ctx,"SIGN");
        
        // let's use the checking of enshureFixedFloatDuration and replace
        // the result if the type was ok
        res = enshureFixedFloatDuration(op,"Sign" );
        if (res != null) {
          // replace with FIXED(1)
          res = new ASTAttribute(new TypeFixed(1));
        }
        m_ast.put(ctx,res);
        ErrorStack.leave();
        return null;
    }      
    
    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -----------+-----------+-----------+-------------+---------------------------------
    // Expression | Type of   | Type of   | Result type | Meaning of operation
    //            | operand 1 | operand 2 |             |
    // -----------+-----------+-----------+-------------+---------------------------------
    // op1 * op2  | FIXED(g1) | FIXED(g2) | FIXED(g3)   | multiplication of the values of
    //            | FIXED(g1) | FLOAT(g2) | FLOAT(g3)   | the operands op1 and op2
    //            | FLOAT(g1) | FIXED(g2) | FLOAT(g3)   |
    //            | FLOAT(g1) | FLOAT(g2) | FLOAT(g3)   | g3 = max (g1, g2)
    //            | FIXED(g1) | DURATION  | DURATION    |
    //            | DURATION  | FIXED(g2) | DURATION    |
    //            | FLOAT(g1) | DURATION  | DURATION    |
    //            | DURATION  | FLOAT(g2) | DURATION    |
    // -----------+-----------+-----------+-------------+---------------------------------

    @Override
    public Void visitMultiplicativeExpression(SmallPearlParser.MultiplicativeExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitMultiplicativeExpression:ctx" + CommonUtils.printContext(ctx));
        Log.debug("ExpressionTypeVisitor:visitMultiplicativeExpression:ctx.expression(0)" + CommonUtils.printContext(ctx.expression(0)));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        op1 = saveGetAttribute(ctx.expression(0), ctx, "mult", "no AST attribute found for lhs of operation *");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "mult", "no AST attribute found for rhs of operation *");
     

        ErrorStack.enter(ctx);

        if (op1 != null && op2 != null) {
          Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
          
          // implicit dereferences
          TypeDefinition type1 = op1.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          TypeDefinition type2 = op2.getType();
          if (type2 instanceof TypeReference) {
            // implicit dereference
            type2 = ((TypeReference) type2).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          if (op1.getType() instanceof TypeFixed && op2.getType() instanceof TypeDuration) {
            res = new ASTAttribute(new TypeDuration(), op1.isReadOnly() && op2.isReadOnly());

            Log.debug("ExpressionTypeVisitor: visitMultiplicativeExpression: rule#6");

          } else if (op1.getType() instanceof TypeFloat && op2.getType() instanceof TypeDuration) {
            res = new ASTAttribute(new TypeDuration(), op1.isReadOnly() && op2.isReadOnly());

            Log.debug("ExpressionTypeVisitor: visitMultiplicativeExpression: rule#7");
          } else if (op1.getType() instanceof TypeDuration && op2.getType() instanceof TypeFloat) {
            res = new ASTAttribute(new TypeDuration(), op1.isReadOnly() && op2.isReadOnly());
          } else {
            res = treatFixedFloatDyadic(op1.getType(), op2.getType(), isReadOnly, op1, op2, "*");
          }
          
          m_ast.put(ctx, res);
        }
        ErrorStack.leave();
        
        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -------------+-----------+-----------+-------------+---------------------------------
    // Expression   | Type of   | Type of   | Result type | Meaning of operation
    //              | operand 1 | operand 2 |             |
    // -------------+-----------+-----------+-------------+---------------------------------
    // op1 / op2    | FIXED(g1) | FIXED(g2) | FLOAT(g3)   | division of the values of
    //              | FLOAT(g1) | FIXED(g2) | FLOAT(g3)   | the operands op1 and op2,
    //              | FIXED(g1) | FLOAT(g2) | FLOAT(g3)   | if op2 <> 0
    //              | FLOAT(g1) | FLOAT(g2) | FLOAT(g3)   |
    //              | DURATION  | FIXED(g2) | DURATION    | g3 = max (g1, g2)
    //              | DURATION  | FLOAT(g2) | DURATION    | g4 = 31
    //              | DURATION  | DURATION  | FLOAT(g4)   | (dependent on implementation)

    @Override
    public Void visitDivideExpression(SmallPearlParser.DivideExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitDivideExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "/", "no AST attribute found for lhs of operation /");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "/", "no AST attribute found for rhs of operation /");
 
        ErrorStack.enter(ctx);
        if (op1 != null && op2 != null) {
          Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
          
          // implicit dereferences
          TypeDefinition type1 = op1.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          TypeDefinition type2 = op2.getType();
          if (type2 instanceof TypeReference) {
            // implicit dereference
            type2 = ((TypeReference) type2).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          if (type1 instanceof TypeFloat && type2 instanceof TypeDuration) {
            res = new ASTAttribute(new TypeDuration(), isReadOnly);

            Log.debug("ExpressionTypeVisitor: DivideExpression: rule#6");
          } else if (type1 instanceof TypeDuration && type2 instanceof TypeFloat) {
            res = new ASTAttribute(new TypeDuration(), isReadOnly);

            Log.debug("ExpressionTypeVisitor: DivideExpression: rule#7");
          } else if (type1 instanceof TypeDuration && type2 instanceof TypeDuration) {
            res = new ASTAttribute(new TypeFloat(23), isReadOnly);

            Log.debug("ExpressionTypeVisitor: DivideExpression: rule#7");
          } else {
            res = treatFixedFloatDyadic(op1.getType(), op2.getType(), isReadOnly, op1, op2, "/");
          }
          m_ast.put(ctx, res);
        }
        
        ErrorStack.leave();
        return null; 
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -------------+-----------+-----------+-------------+---------------------------------
    // Expression   | Type of   | Type of   | Result type | Meaning of operation
    //              | operand 1 | operand 2 |             |
    // -------------+-----------+-----------+-------------+---------------------------------
    // op1 // op2   | FIXED(g1) | FIXED(g2) | FIXED(g3)   | integer division of the values of
    //              |           |           |             | the operands op1 and op2
    //              |           |           |             | g3 = max (g1, g2)

    @Override
    public Void visitDivideIntegerExpression(SmallPearlParser.DivideIntegerExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitDivideIntegerExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        ErrorStack.enter(ctx,"//");
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "//", "no AST attribute found for lhs of operation //");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "//", "no AST attribute found for rhs of operation //");
     
        if (op1 != null && op2 != null) {
          Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
          
          // implicit dereferences
          TypeDefinition type1 = op1.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          TypeDefinition type2 = op2.getType();
          if (type2 instanceof TypeReference) {
            // implicit dereference
            type2 = ((TypeReference) type2).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          if (type1 instanceof TypeFixed && type2 instanceof TypeFixed) {
            Integer precision = Math.max( type1.getPrecision(), type2.getPrecision());
            res = new ASTAttribute(new TypeFixed(precision), isReadOnly);
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: DivideIntegerExpression: rule#1");
          } else {
             ErrorStack.add("type mismatch: expected FIXED // FIXED -- got "+
                     op1.getType().toString()+"//"+op2.getType().toString());
          }
        }
        
        ErrorStack.leave();
        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -------------+-----------+-----------+-------------+---------------------------------
    // Expression   | Type of   | Type of   | Result type | Meaning of operation
    //              | operand 1 | operand 2 |             |
    // -------------+-----------+-----------+-------------+---------------------------------
    // op1 REM op2  | FIXED(g1) | FIXED(g2) | FIXED(g2)   | remainder of the integer division
    //              | FLOAT(g1) | FLOAT(g2) | FLOAT(g2)   | of the values of the operands op1
    //              |           |           |             | and op2
    // -------------+-----------+-----------+-------------+---------------------------------

    @Override
    public Void visitRemainderExpression(SmallPearlParser.RemainderExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitRemainderExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        ErrorStack.enter(ctx,"REM"); 
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "REM", "no AST attribute found for lhs of operation REM");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "REM", "no AST attribute found for rhs of operation REM");
  
        if (op1 != null && op2 != null) {
          Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
          
          // implicit dereferences
          TypeDefinition type1 = op1.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          TypeDefinition type2 = op2.getType();
          if (type2 instanceof TypeReference) {
            // implicit dereference
            type2 = ((TypeReference) type2).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          if (type1 instanceof TypeFixed && type2 instanceof TypeFixed) {
            Integer precision = Math.max( type1.getPrecision(), type2.getPrecision());
            res = new ASTAttribute(new TypeFixed(precision), isReadOnly);
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: visitRemainderExpression: rule#1");
            // 2020-04-16 rm: only FIXED operands are allowed!
            //        } else if (op1.getType() instanceof TypeFloat && op2.getType() instanceof TypeFloat) {
            //            Integer precision = Math.max(((TypeFloat) op1.getType()).getPrecision(), ((TypeFloat) op2.getType()).getPrecision());
            //            res = new ASTAttribute(new TypeFloat(precision), op1.isReadOnly() && op2.isReadOnly());
            //            m_ast.put(ctx, res);
            //
            //            if (m_debug)
            //                System.out.println("ExpressionTypeVisitor: visitRemainderExpression: rule#1");
          } else {
            ErrorStack.add("type mismatch: expected FIXED REM FIXED -- got "+
                op1.getType().toString()+"//"+op2.getType().toString());
            //            throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        }
        ErrorStack.leave();
        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -------------+-----------+-----------+-------------+---------------------------------
    // Expression   | Type of   | Type of   | Result type | Meaning of operation
    //              | operand 1 | operand 2 |             |
    // -------------+-----------+-----------+-------------+---------------------------------
    // op1 ** op2   | FIXED(g1) | FIXED(g2) | FIXED(g2)   | exponentiation of the values
    //              | FLOAT(g1) | FLOAT(g2) | FLOAT(g2)   | of the operands op1 and op2
    // -------------+-----------+-----------+-------------+---------------------------------

    @Override
    public Void visitExponentiationExpression(SmallPearlParser.ExponentiationExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitExponentiationExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        ErrorStack.enter(ctx,"**"); 

        op1 = saveGetAttribute(ctx.expression(0), ctx, "**", "no AST attribute found for lhs of operation **");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "**", "no AST attribute found for rhs of operation **");

        
        if (op1 != null && op2 != null) {
          Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
          
          // implicit dereferences
          TypeDefinition type1 = op1.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          TypeDefinition type2 = op2.getType();
          if (type2 instanceof TypeReference) {
            // implicit dereference
            type2 = ((TypeReference) type2).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }
          
          if (type1 instanceof TypeFixed && type2 instanceof TypeFixed) {
            Integer precision = type1.getPrecision();
            res = new ASTAttribute(new TypeFixed(precision), isReadOnly);
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: ExponentiationExpression: rule#1");
          } else if (type1 instanceof TypeFloat && type2 instanceof TypeFixed) {
            res = new ASTAttribute(new TypeFloat(((TypeFloat) op1.getType()).getPrecision()), isReadOnly);
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: ExponentiationExpression: rule#1");
          } else {
            ErrorStack.add("type mismatch: expected FIXED ** FIXED or FLOAT ** FLOAT -- got "+
                op1.getType().toString()+"//"+op2.getType().toString());
//            throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        } 
        ErrorStack.leave();
        return null;
    }


    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //
    // -------------+-----------+-----------+-------------+---------------------------------
    // Expression   | Type of   | Type of   | Result type | Meaning of operation
    //              | operand 1 | operand 2 |             |
    // -------------+-----------+-----------+-------------+---------------------------------
    // op1 FIT op2  | FIXED(g1) | FIXED(g2) | FIXED(g2)   | changing the precision of
    //              | FLOAT(g1) | FLOAT(g2) | FLOAT(g2)   | operand op1 into the precision
    //              |           |           |             | of operand op2
    // -------------+-----------+-----------+-------------+---------------------------------

    @Override
    public Void visitFitExpression(SmallPearlParser.FitExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitFitExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        ErrorStack.enter(ctx, "FIT");
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "FIT", "no AST attribute found for lhs of operation FIT");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "FIT", "no AST attribute found for rhs of operation FIT");

        if (op1 != null && op2 != null) {
         Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
          
          // implicit dereferences
          TypeDefinition type1 = op1.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          TypeDefinition type2 = op2.getType();
          if (type2 instanceof TypeReference) {
            // implicit dereference
            type2 = ((TypeReference) type2).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }
          
          if (type1 instanceof TypeFixed && type2 instanceof TypeFixed) {
            Integer precision = ((TypeFixed) op2.getType()).getPrecision();
            res = new ASTAttribute(new TypeFixed(precision), isReadOnly);
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: FitExpression: rule#1");
          } else if (type1 instanceof TypeFloat && type2 instanceof TypeFloat) {
            Integer precision = ((TypeFloat) op2.getType()).getPrecision();
            res = new ASTAttribute(new TypeFloat(precision), isReadOnly);
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: FitExpression: rule#2");
          } else {
            ErrorStack.add("type mismatch: expected FIXED FIT FIXED or FLOAT FIT FLOAT -- got "+
                op1.getType().toString()+"//"+op2.getType().toString());
          }
        }
        ErrorStack.leave();

        return null;
    }

    // FIXED(g) is also possible for the monadic arithmetic operators
    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //            Table 6.3: Monadic arithmetical operators
    // -----------+-------------+-----------+-------------+---------------------------------
    // Expression | Type of     |           | Result type | Meaning of operation
    //            | operand     |           |             |
    // -----------+-------------+-----------+-------------+---------------------------------
    // SQRT op    | FLOAT(g)    |           | FLOAT(g)    | square root of operand
    // SIN op     | FIXED(g)    |           |             | sine of operand
    // COS op     |             |           |             | cosine of operand
    // EXP op     |             |           |             | e^op with e=2.718281828459
    // LN op      |             |           |             | natural loarithm of operand
    // TAN op     |             |           |             | tangent of operand
    // ATAN op    |             |           |             | arcus tangent of operand
    // TANH op    |             |           |             | tangent hyperbolicus of operand
    // -----------+-------------+-----------+-------------+---------------------------------

    @Override
    public Void visitSqrtExpression(SmallPearlParser.SqrtExpressionContext ctx) {
        ASTAttribute op;
        
        Log.debug("ExpressionTypeVisitor:visitSqrtExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "SQRT", "no AST attribute found for SQRT");
  
        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op,"SQRT");

        return null;
    }

    @Override
    public Void visitSinExpression(SmallPearlParser.SinExpressionContext ctx) {
        ASTAttribute op;
        
        Log.debug("ExpressionTypeVisitor:visitSinExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "SIN", "no AST attribute found for SIN");

        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op,"SIN");

        return null;
    }

    @Override
    public Void visitCosExpression(SmallPearlParser.CosExpressionContext ctx) {
        ASTAttribute op;

        Log.debug("ExpressionTypeVisitor:visitCosExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "COS", "no AST attribute found for COS");

        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op,"COS");

        return null;
    }

    @Override
    public Void visitExpExpression(SmallPearlParser.ExpExpressionContext ctx) {
        ASTAttribute op;

        Log.debug("ExpressionTypeVisitor:visitExpExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "EXP", "no AST attribute found for EXP");
        
        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op, "EXP");

        return null;
    }

    @Override
    public Void visitLnExpression(SmallPearlParser.LnExpressionContext ctx) {
        ASTAttribute op;

        Log.debug("ExpressionTypeVisitor:visitLnExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "LN", "no AST attribute found for LN");

        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op,"LN");

        return null;
    }

    @Override
    public Void visitTanExpression(SmallPearlParser.TanExpressionContext ctx) {
        ASTAttribute op;

        Log.debug("ExpressionTypeVisitor:visitTanExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "TAN", "no AST attribute found for TAN");
        
        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op,"TAN");

        return null;
    }

    @Override
    public Void visitAtanExpression(SmallPearlParser.AtanExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitAtanExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "ATAN", "no AST attribute found for ATAN");

        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op,"ATAN");

        return null;
    }

    @Override
    public Void visitTanhExpression(SmallPearlParser.TanhExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitTanhExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "TANH", "no AST attribute found for TANH");

        treatFixedFloatParameterForMonadicArithmeticOperators((ExpressionContext)ctx, op, "TANH");

        return null;
    }

    private Void treatFixedFloatParameterForMonadicArithmeticOperators(ExpressionContext ctx, ASTAttribute op, String operator) {
        ASTAttribute res;

        ErrorStack.enter(ctx, operator);
        
        if (op == null) {
          ErrorStack.addInternal("no AST attribute found for "+operator);
        } else {
          Boolean isReadOnly = op.isReadOnly();
          TypeDefinition type = op.getType();
          
          if (type instanceof TypeReference) {
            type = ((TypeReference)type).getBaseType();
            isReadOnly = false;
          }
    	  if (type instanceof TypeFloat) {
    		res = new ASTAttribute(new TypeFloat(type.getPrecision()), isReadOnly);
    		m_ast.put(ctx, res);
    		Log.debug("ExpressionTypeVisitor: "+operator+"Expression: rule#2");
      	  } else if (type instanceof TypeFixed) {
    		int precision = type.getPrecision();
    		if (precision > Defaults.FLOAT_SHORT_PRECISION) {
    			precision = Defaults.FLOAT_LONG_PRECISION;  // ???  FIXED_MAX_LENGTH;
    		} else {
    			precision = Defaults.FLOAT_SHORT_PRECISION;
    		}
    		res = new ASTAttribute(new TypeFloat(precision), isReadOnly);
    		m_ast.put(ctx, res);
    	  } else {
    		ErrorStack.add("only FIXED and FLOAT are allowed -- got "+op.getType().toString());
    	  }
        }
        ErrorStack.leave();
    	return null;
    }
       
    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //            Table 6.2: Monadic operators for explicit type conversions
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      |           | Result type  | Meaning of operation
    //            | operand      |           |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // TOFIXED op | CHARACTER(1) |           | FIXED(7)     | ASCII code for operand character
    //            | BIT(lg)      |           | FIXED(g)     | interpreting the bit pattern of the
    //            |              |           |              | operand as an integer, with g = lg
    // TOFLOAT op | FIXED(g)     |           | FLOAT(g)     | converting the operand into a
    //            |              |           |              | floating point number
    // TOBIT op   | FIXED(g)     |           | BIT(lg)      | interpreting the operand as bit
    //            |              |           |              | pattern, with lg = g
    // TOCHAR op  | FIXED        |           | CHARRATER(1) | character for the ASCII code of the
    //            |              |           |              | operand
    // ENTIER op  | FLOAT(g)     |           | FIXED(g)     | greatest integer less or equal than
    //            |              |           |              | the operand
    // ROUND op   | FLOAT(g)     |           | FIXED(g)     | next integer according to DIN

    @Override
    public Void visitTOFIXEDExpression(SmallPearlParser.TOFIXEDExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitTOFIXEDExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        
        ErrorStack.enter(ctx,"TOFIXED");
        
        op = saveGetAttribute(ctx.expression(), ctx, "ToFIXED", "no AST attribute found for TOFIXED");

        if (op != null) {
          Boolean isReadOnly = op.isReadOnly();
          
          // implicit dereference
          TypeDefinition type1 = op.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }

          if (type1 instanceof TypeBit) {
            res = new ASTAttribute(new TypeFixed(((TypeBit) op.getType()).getPrecision() - 1), isReadOnly);
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: TOFIXED: rule#1");
            
          } else if (type1 instanceof TypeChar) {
            TypeChar typeChar;

            Log.debug("ExpressionTypeVisitor: TOFIXED: rule#2");
            

            typeChar = (TypeChar) type1;

            if ( typeChar.getPrecision() != 1 ) {
              ErrorStack.add("only single CHAR allowed");
            }

            res = new ASTAttribute(new TypeFixed(1));
            m_ast.put(ctx, res);
          } else {
            ErrorStack.add("only BIT and CHAR are allowed -- got "+op.getType().toString());
          }
        }
        
        ErrorStack.leave();

        return null;
    }

    @Override
    public Void visitTOFLOATExpression(SmallPearlParser.TOFLOATExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitTOFLOATExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        
        ErrorStack.enter(ctx,"TOFLOAT");
        op = saveGetAttribute(ctx.expression(), ctx, "TOFLOAT", "no AST attribute found for TOFLOAT");

        if (op != null) {
          Boolean isReadOnly = op.isReadOnly();
          
          // implicit dereference
          TypeDefinition type1 = op.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }
          
          if (type1 instanceof TypeFixed) {
            TypeFixed fixedValue = (TypeFixed) type1;
            int       precision = 0;

            if ( fixedValue.getPrecision() <= Defaults.FLOAT_SHORT_PRECISION) {
                precision = Defaults.FLOAT_SHORT_PRECISION;
            }
            else {
                precision = Defaults.FLOAT_LONG_PRECISION;
            }

            res = new ASTAttribute(new TypeFloat(precision));
            m_ast.put(ctx, res);

            Log.debug("ExpressionTypeVisitor: TOFLOAT: rule#1");
          } else {
            ErrorStack.add("only type FIXED allowed -- got "+op.getType().toString());
            //throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        }
        ErrorStack.leave();
        return null;
    }

    @Override
    public Void visitTOBITExpression(SmallPearlParser.TOBITExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitTOBITExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        
        ErrorStack.enter(ctx,"TOBIT");
        op = saveGetAttribute(ctx.expression(), ctx, "TOBIT", "no AST attribute found for TOBIT");


        if (op != null) {
          Boolean isReadOnly = op.isReadOnly();
          
          // implicit dereference
          TypeDefinition type1 = op.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }
          
          if (type1 instanceof TypeFixed) {
            res = new ASTAttribute(new TypeBit(type1.getPrecision()), isReadOnly);
            m_ast.put(ctx, res);
            Log.debug("ExpressionTypeVisitor: TOBIT: rule#1");
          } else {
            ErrorStack.add("only type FIXED allowed -- got "+op.getType().toString());
         //   throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        }
        ErrorStack.leave();
        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //            Table 6.2: Monadic operators for explicit type conversions
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      |           | Result type  | Meaning of operation
    //            | operand      |           |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // TOCHAR op  | FIXED        |           | CHARRATER(1) | character for the ASCII code of the
    //            |              |           |              | operand

    @Override
    public Void visitTOCHARExpression(SmallPearlParser.TOCHARExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitTOCHARExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        op = saveGetAttribute(ctx.expression(), ctx, "TOCHAR", "no AST attribute found for TOCHAR");
        ErrorStack.enter(ctx,"TOCHAR");
        if (op != null) {
          Boolean isReadOnly = op.isReadOnly();
          
          // implicit dereference
          TypeDefinition type1 = op.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }
          
          if (type1 instanceof TypeFixed) {
            res = new ASTAttribute(new TypeChar(1));
            m_ast.put(ctx, res);
          } else {
            ErrorStack.add("only type FIXED allowed -- got "+op.getType().toString());
           // throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        }
        ErrorStack.leave();

        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //            Table 6.2: Monadic operators for explicit type conversions
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      |           | Result type  | Meaning of operation
    //            | operand      |           |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // ENTIER op  | FLOAT(g)     |           | FIXED(g)     | greatest integer less or equal than

    @Override
    public Void visitEntierExpression(SmallPearlParser.EntierExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitEntierExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        ErrorStack.enter(ctx,"ENTIER");
        op = saveGetAttribute(ctx.expression(), ctx, "ENTIER", "no AST attribute found for ENTIER");

        if (op != null) {
          Boolean isReadOnly = op.isReadOnly();
          
          // implicit dereference
          TypeDefinition type1 = op.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }
          
          if (type1 instanceof TypeFloat) {
            res = new ASTAttribute(new TypeFixed(type1.getPrecision()), isReadOnly);
            m_ast.put(ctx, res);
            if (m_debug)
                System.out.println("ExpressionTypeVisitor: ENTIER: rule#1");
          } else {
            ErrorStack.add("only type FLOAT allowed -- got "+op.getType().toString());
            //throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        }
        ErrorStack.leave();

        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.1 Expressions
    //            Table 6.2: Monadic operators for explicit type conversions
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      |           | Result type  | Meaning of operation
    //            | operand      |           |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // ROUND op   | FLOAT(g)     |           | FIXED(g)     | next integer according to DIN

    @Override
    public Void visitRoundExpression(SmallPearlParser.RoundExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitRoundExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression());
        ErrorStack.enter(ctx,"ROUND");
        op = saveGetAttribute(ctx.expression(), ctx, "ROUND", "no AST attribute found for ROUND");

        if (op != null) {
          Boolean isReadOnly = op.isReadOnly();
          
          // implicit dereference
          TypeDefinition type1 = op.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
            isReadOnly = false;   // let's do the evaluation during runtime 
          }
          
          if (type1 instanceof TypeFloat) {
            res = new ASTAttribute(new TypeFixed(type1.getPrecision()), isReadOnly);
            m_ast.put(ctx, res);
            Log.debug("ExpressionTypeVisitor: ROUND: rule#1");
          } else {
            ErrorStack.add("only type FLOAT allowed -- got "+op.getType().toString());
            //throw new IllegalExpressionException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
          }
        }
        ErrorStack.leave();
        return null;
    }

    @Override
    public Void visitUnaryExpression(SmallPearlParser.UnaryExpressionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitUnaryExpression:ctx" + CommonUtils.printContext(ctx));
        ErrorStack.addInternal(ctx,"unary expr","not implemented!");
        return null;
        //throw new NotYetImplementedException("ExpressionTypeVisitor:visitUnaryExpression", ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    //
    // Reference: OpenPEARL Language Report
    //            9.3.1 Semaphore Variables (SEMA) and Statements (REQUEST, RELEASE, TRY)
    // -------------+--------------+-----------+--------------+---------------------------------
    // Expression   |              |           | Result type  | Meaning of operation
    //              |              |           |              |
    // -------------+------------- +-----------+--------------+---------------------------------
    // TRY sema     | SEMAPHORE    |           | BIT(1)       | Obtains the state of a semaphore
    //              |              |           |              | variable

    @Override
    public Void visitSemaTry(SmallPearlParser.SemaTryContext ctx) {
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitSemaTry:ctx" + CommonUtils.printContext(ctx));
        
        // ID is mandatory by grammar!       
        //        if (ctx.ID() == null) {
        //            throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        //        }

        res = new ASTAttribute(new TypeBit(1));
        m_ast.put(ctx, res);
        
        // set ast attributes for the names. This makes is easier in the CppCodeGenerator
        visitChildren(ctx);   

        if (m_debug)
            System.out.println("ExpressionTypeVisitor: TRY: rule#1");

        return null;
    }

    @Override
    public Void visitLiteral(SmallPearlParser.LiteralContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitLiteral:ctx" + CommonUtils.printContext(ctx));

        
        if (ctx.durationConstant() != null) {
            ASTAttribute expressionResult = new ASTAttribute(new TypeDuration(), true);
            expressionResult.setConstant(CommonUtils.getConstantDurationValue(ctx.durationConstant(),1));
            m_ast.put(ctx, expressionResult);
        } else if (ctx.floatingPointConstant() != null) {
            
            try {
                double value = CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());
                int precision = CommonUtils.getFloatingPointConstantPrecision(ctx.floatingPointConstant(), m_currentSymbolTable.lookupDefaultFloatLength());

                ASTAttribute expressionResult = new ASTAttribute( new TypeFloat(precision),true);
                m_ast.put(ctx, expressionResult);
            } catch (NumberFormatException ex) {
        //        throw new NumberOutOfRangeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
              ErrorStack.add(ctx,"floating point constant","illegal number");
            }
            
        } else if (ctx.timeConstant() != null) {
            ASTAttribute expressionResult = new ASTAttribute( new TypeClock(),true);
            expressionResult.setConstant(getConstantClockValue(ctx.timeConstant()));
            m_ast.put(ctx, expressionResult);
        } else if (ctx.StringLiteral() != null) {
            ConstantCharacterValue ccv = getConstantStringLiteral(ctx.StringLiteral());
            int length = ccv.getLength();
            if (length == 0) {
               ErrorStack.add(ctx,"char literal","need at least 1 character");
            } 
            // generate AST Attribute for further analysis
        	ASTAttribute expressionResult = new ASTAttribute(new TypeChar(ccv.getLength()), true);
        	ConstantValue cv = m_constantPool.add(ccv);   // add to constant pool; maybe we have it already
        	expressionResult.setConstant(cv);
            m_ast.put(ctx, expressionResult);
            
        } else if (ctx.BitStringLiteral() != null) {
            ASTAttribute expressionResult = new ASTAttribute(  new TypeBit(CommonUtils.getBitStringLength(ctx.BitStringLiteral().getText())), true);
            m_ast.put(ctx, expressionResult);
        } else if (ctx.fixedConstant() != null) {
            long value=0;
            int precision;
            try {
                precision = m_currentSymbolTable.lookupDefaultFixedLength();
                
                if (m_currFixedLength != null ) {
                    precision = m_currFixedLength;
                }

                m_calculateRealFixedLength = true;
                if ( m_calculateRealFixedLength) {
                    value = Long.parseLong(ctx.fixedConstant().IntegerConstant().getText());

                    precision = Long.toBinaryString(Math.abs(value)).length();
                    if ( value <  0) {
                        precision++;
                    }
                }

                m_calculateRealFixedLength = false;

                if ( ctx.fixedConstant().fixedNumberPrecision() != null) {
                    precision = Integer.parseInt(ctx.fixedConstant().fixedNumberPrecision().IntegerConstant().toString());
                }


                ASTAttribute expressionResult = new ASTAttribute(new TypeFixed(precision), true);
                ConstantFixedValue cfv = new ConstantFixedValue(value,precision);
                ConstantValue cv = m_constantPool.add(cfv);   // add to constant pool; maybe we have it already
                expressionResult.setConstant(cv);                
                m_ast.put(ctx, expressionResult);
            } catch (NumberFormatException ex) {
              ErrorStack.add(ctx,"integer literal","illegal number");
            }

        } else if (ctx.referenceConstant() != null) {
          // NIL fits to any type; thus we have NO basetype
          ASTAttribute expressionResult = new ASTAttribute(  new TypeReference());
          ConstantNILReference cnr = new ConstantNILReference();
          ConstantValue cv = m_constantPool.add(cnr);   // add to constant pool; maybe we have it already
          expressionResult.setConstant(cv);
          m_ast.put(ctx, expressionResult);
        }
        return null;
    }

    private ConstantCharacterValue getConstantStringLiteral( TerminalNode terminalNode) {
      String s = terminalNode.toString();
    
      ConstantCharacterValue result = new ConstantCharacterValue(s);
      
      return result;
    }
    

    private ConstantClockValue getConstantClockValue(SmallPearlParser.TimeConstantContext ctx) {
        Integer hours = 0;
        Integer minutes = 0;
        Double seconds = 0.0;

        hours = (Integer.valueOf(ctx.IntegerConstant(0).toString()) % 24);
        minutes = Integer.valueOf(ctx.IntegerConstant(1).toString());

        if (ctx.IntegerConstant().size() == 3) {
            seconds = Double.valueOf(ctx.IntegerConstant(2).toString());
        }

        if ( ctx.floatingPointConstant() != null ) {
            seconds = CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());
        }

        if (hours < 0 || minutes < 0 || minutes > 59) {
          ErrorStack.add(ctx,"clock value", "illegal value");
        }

        return new ConstantClockValue(hours,minutes,seconds);
    }


    @Override
    public Void visitModule(SmallPearlParser.ModuleContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitModule:ctx" + CommonUtils.printContext(ctx));

//        this.m_currentSymbolTable = this.symbolTable.newLevel(moduleEntry);
        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }


    @Override
    public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitTaskDeclaration:ctx" + CommonUtils.printContext(ctx));

        SymbolTableEntry entry = this.m_currentSymbolTable.lookupLocal(ctx.ID().getText());

        if (entry != null) {
            if (entry instanceof TaskEntry) {
                m_currentSymbolTable = ((TaskEntry) entry).scope;
                visitChildren(ctx);
                this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
            } else {
              ErrorStack.addInternal(ctx,"TASK","'"+ctx.ID().getText()+"' is not of type TASK");
            }
        } else {
          ErrorStack.addInternal(ctx,"TASK","'"+ctx.ID().getText()+"' is not in symbol table");
        }

        return null;
    }

    @Override
    public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitProcedureDeclaration:ctx" + CommonUtils.printContext(ctx));

        SymbolTableEntry entry = this.m_currentSymbolTable.lookupLocal(ctx.ID().getText());
        
        if (entry != null) {
            if (entry instanceof ProcedureEntry) {
                m_currentSymbolTable = ((ProcedureEntry) entry).scope;

                visitChildren(ctx);
                this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
            } else {
              ErrorStack.addInternal(ctx,"PROC","'"+ctx.ID().getText()+"' is not of type PROC");
            }
        } else {
          ErrorStack.addInternal(ctx,"PROC","'"+ctx.ID().getText()+"' is not in symbol table");
        }
        
        return null;
    }
    
    // set an ast attribute if we have a CHAR-type as result attribut
    // we need for all result types the AST Attribute 
    @Override
    public Void visitResultAttribute(SmallPearlParser.ResultAttributeContext ctx) {
      if (ctx.resultType().simpleType() != null) {
        if (ctx.resultType().simpleType().typeCharacterString()!=null) {
          int len = Integer.parseInt(ctx.resultType().simpleType().typeCharacterString().IntegerConstant().getText());
          m_ast.put(ctx, new ASTAttribute(new TypeChar(len)));
        }
      }
      return null;
    }

    @Override
    public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitBlock_statement:ctx" + CommonUtils.printContext(ctx));

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitLoopStatement:ctx" + CommonUtils.printContext(ctx));
        
        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);

        // check the precision of loop expressions and loop control variable 
        int precisionFor = 1;  // FROM defaults to 1 which is FIXED(1)

        if (ctx.loopStatement_from() != null) {
           visit(ctx.loopStatement_from());
           ASTAttribute attr = m_ast.lookup(ctx.loopStatement_from().expression());
           if (! (attr.getType() instanceof TypeFixed) ) {
             ErrorStack.add(ctx.loopStatement_from().expression(),"FOR","type must be FIXED - but is "+attr.getType().toString());
           }
           precisionFor = Math.max(precisionFor, attr.getType().getPrecision());
        }
        if (ctx.loopStatement_to() != null) {
          visit(ctx.loopStatement_to());
          ASTAttribute attr = m_ast.lookup(ctx.loopStatement_to().expression());
          if (! (attr.getType() instanceof TypeFixed) ) {
            ErrorStack.add(ctx.loopStatement_to().expression(),"TO","type must be FIXED - but is "+attr.getType().toString());
          } 
          precisionFor = Math.max(precisionFor, attr.getType().getPrecision());
        } else {
            // if no TO is present, the loop is indefinite
            // --> let's use the current fixed length for loop variable as long as
            //     the FROM expression was not larger
            precisionFor =  Math.max(precisionFor, m_currentSymbolTable.lookupDefaultFixedLength());
        }

        if (ctx.loopStatement_by() != null) {
          visit(ctx.loopStatement_by());
          ASTAttribute attr = m_ast.lookup(ctx.loopStatement_by().expression());
          if (! (attr.getType() instanceof TypeFixed) ) {
            ErrorStack.add(ctx.loopStatement_by().expression(),"BY","type must be FIXED - but is "+attr.getType().toString());
          }
          precisionFor = Math.max(precisionFor, attr.getType().getPrecision());
        }
        if (ctx.loopStatement_while()!= null) {
          visit(ctx.loopStatement_while());
          ASTAttribute attr = m_ast.lookup(ctx.loopStatement_while().expression());
          if (! (attr.getType() instanceof TypeBit) || attr.getType().getPrecision()!= 1) {
            ErrorStack.add(ctx.loopStatement_by().expression(),"WHILE","type must be BIT(1) - but is "+attr.getType().toString());
          }
        }
        
        if (ctx.loopStatement_for() != null) {
          // adjust the precision of the loop control variable in the symbol table
          SymbolTableEntry seFor = m_currentSymbolTable.lookup(ctx.loopStatement_for().ID().toString());
          if (seFor != null) {
            VariableEntry ve = (VariableEntry)seFor;
            ((TypeFixed)(ve.getType())).setPrecision(precisionFor);
          }
        }
        
        // now treat the loop body
        visit(ctx.loopBody());

        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitLoopStatement_from(SmallPearlParser.LoopStatement_fromContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitLoopStatement_from:ctx" + CommonUtils.printContext(ctx));

        m_calculateRealFixedLength = true;
        visitChildren(ctx);
        m_calculateRealFixedLength = false;

        return null;
    }

    @Override
    public Void visitLoopStatement_to(SmallPearlParser.LoopStatement_toContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitLoopStatement_to:ctx" + CommonUtils.printContext(ctx));

        m_calculateRealFixedLength = true;
        visitChildren(ctx);
        m_calculateRealFixedLength = false;

        return null;
    }

    @Override
    public Void visitLoopStatement_by(SmallPearlParser.LoopStatement_byContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitLoopStatement_by:ctx" + CommonUtils.printContext(ctx));

        m_calculateRealFixedLength = true;
        visitChildren(ctx);
        m_calculateRealFixedLength = false;

        return null;
    }

 /*
   @Override
    public Void visitAssignment_statement(SmallPearlParser.Assignment_statementContext ctx) {
      Log.debug("ExpressionTypeVisitor:visitAssignment_statement_by:ctx" + CommonUtils.printContext(ctx));
      Log.debug("ExpressionTypeVisitor:visitAssignment_statement:" + ctx.getText());
      
        ASTAttribute attrName=null;
        ASTAttribute selection = null;

        visitChildren(ctx);
        
        Log.debug("ExpressionTypeVisitor:visitAssignment_statement:ctx" + CommonUtils.printContext(ctx));
        ErrorStack.enter(ctx,"assignment");
        
        if ( ctx.stringSelection() != null ) {
            if (ctx.stringSelection().charSelection() != null) {
              attrName = m_ast.lookup(ctx.stringSelection().charSelection().name());
              selection = m_ast.lookup(ctx.stringSelection().charSelection().charSelectionSlice());
              if (!(attrName.getType()  instanceof TypeChar)) {
                ErrorStack.add(".CHAR must be applied on variable of type CHAR -- used with "+attrName.getType());
              }
            }
            else  if (ctx.stringSelection().bitSelection() != null) {
              attrName = m_ast.lookup(ctx.stringSelection().bitSelection().name());
              selection = m_ast.lookup(ctx.stringSelection().bitSelection().bitSelectionSlice());
              if (!(attrName.getType()  instanceof TypeBit)) {
                ErrorStack.add(".BIT must be applied on variable of type BIT -- used with "+attrName.getType());
              }
            } else {
                ErrorStack.addInternal("visitAssignment_statement: missing alternative for stringSelection");
            }

            if (selection.getConstantSelection() != null) {
              long lower = selection.getConstantSelection().getLowerBoundary().getValue();
              long upper = selection.getConstantSelection().getUpperBoundary().getValue();
              if (lower < 1 || upper < 1 
                  || attrName.getType().getPrecision() < lower
                  || attrName.getType().getPrecision() < upper) {
                ErrorStack.add("selection beyond variable size");
              }
              
            }
        } else if (ctx.name() != null) {
            //TODO: MS This looks not correct:
//            visit(ctx.name());
            attrName = m_ast.lookup(ctx.name());
        } else {
          ErrorStack.addInternal("visitAssignment_statement: missing alternative");
        }

        // note that rhs is already visited by visitChildren() at the beginning of this method 
       
        ErrorStack.leave();
        
        return null;
    }
*/

    @Override
    public Void visitAssignment_statement(SmallPearlParser.Assignment_statementContext ctx) {
      Log.debug("ExpressionTypeVisitor:visitAssignment_statement_by:ctx" + CommonUtils.printContext(ctx));
      Log.debug("ExpressionTypeVisitor:visitAssignment_statement:" + ctx.getText());
      String s = ctx.getText();
        ASTAttribute attrName=null;
        ASTAttribute selection = null;

        Log.debug("ExpressionTypeVisitor:visitAssignment_statement:ctx" + CommonUtils.printContext(ctx));
        ErrorStack.enter(ctx,"assignment");
        
        m_autoDereference = false;
        visit(ctx.name());
        
        m_autoDereference = true;
        
        attrName = m_ast.lookup(ctx.name());
        if (attrName==null) {
          // lhs not found --> error already issued --> just leave check
          ErrorStack.leave();
          return null;
        }
        
        if ( ctx.charSelectionSlice() != null ) {
          visit(ctx.charSelectionSlice());
          selection = m_ast.lookup(ctx.charSelectionSlice());
          if (!(attrName.getType()  instanceof TypeChar)) {
            ErrorStack.add(".CHAR must be applied on variable of type CHAR -- used with "+attrName.getType());
          }   
        } else if (ctx.bitSelectionSlice() != null) {
          visit(ctx.bitSelectionSlice());
          selection = m_ast.lookup(ctx.bitSelectionSlice());
          if (!(attrName.getType()  instanceof TypeBit)) {
            ErrorStack.add(".BIT must be applied on variable of type BIT -- used with "+attrName.getType());
          }   
        }
 
        if (selection != null && selection.getConstantSelection() != null) {
           long lower = selection.getConstantSelection().getLowerBoundary().getValue();
           long upper = selection.getConstantSelection().getUpperBoundary().getValue();
           if (lower < 1 || upper < 1 
                  || attrName.getType().getPrecision() < lower
                  || attrName.getType().getPrecision() < upper) {
                ErrorStack.add("selection beyond variable size");
           }
        }
        
        if (attrName.getType() instanceof TypeReference &&
            ((TypeReference)(attrName.getType())).getBaseType() instanceof TypeProcedure) {
           m_autoDereference = false;
        }
        visit(ctx.expression());

        m_autoDereference = false;
        
        ErrorStack.leave();

        return null;
    }

    @Override
    public Void visitNowFunction(SmallPearlParser.NowFunctionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitNowFunction:ctx" + CommonUtils.printContext(ctx));

        TypeClock type = new TypeClock();
        ASTAttribute expressionResult = new ASTAttribute(type);
        m_ast.put(ctx, expressionResult);

        return null;
    }

    @Override
    public Void visitSizeofExpression(SmallPearlParser.SizeofExpressionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitSizeofExpression:ctx" + CommonUtils.printContext(ctx));

        TypeFixed type = new TypeFixed(Defaults.FIXED_LENGTH);
        ASTAttribute expressionResult = new ASTAttribute(type);
        m_ast.put(ctx, expressionResult);
        if (ctx.expression() != null) {
           visit(ctx.expression());
        }
        return null;
    }

    //
    // Reference: OpenPEARL Language Report
    //            Table 6.6: Dyadic comparison operators
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      | Type of   | Result type  | Meaning of operation
    //            | operand 1    | operand 2 |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // op1 == op2 | FIXED(g1)    | FIXED(g2) | BIT(1)       | “equal”
    //    or      | FIXED(g1)    | FLOAT(g2) |              | If op1 is equal op2,
    // op1 EQ op2 | FLOAT(g1)    | FIXED(g1) |              | the result has value ’1’B,
    //            | FLOAT(g1)    | FLOAT(g2) |              | otherwise ’0’B.
    //            | CLOCK        | CLOCK     |              | If lg2 = lg1, the shorter
    //            | DURATION     | DURATION  |              | character or bit string, resp.,
    //            | CHAR(lg1)    | CHAR(lg2) |              | is padded with blanks or zeros,
    //            | BIT(lg1)     | BIT(lg2)  |              | resp., on the right side to match

    @Override
    public Void visitEqRelationalExpression(SmallPearlParser.EqRelationalExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitEqRelationalExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "EQ", "no AST attribute found for lhs of operation EQ");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "EQ", "no AST attribute found for rhs of operation EQ");

        checkUnOrderedCompare(op1, op2, "EQ / ==", ctx);

        return null;
      }
    
    //
    // Reference: OpenPEARL Language Report
    //            Table 6.6: Dyadic comparison operators
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      | Type of   | Result type  | Meaning of operation
    //            | operand 1    | operand 2 |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // op1 /= op2 | FIXED(g1)    | FIXED(g2) | BIT(1)       | “not equal”
    //    or      | FIXED(g1)    | FLOAT(g2) |              | If op1 is not equal op2,
    // op1 NE op2 | FLOAT(g1)    | FIXED(g1) |              | the result has value ’1’B,
    //            | FLOAT(g1)    | FLOAT(g2) |              | otherwise ’0’B.
    //            | CLOCK        | CLOCK     |              | If lg2 = lg1, the shorter
    //            | DURATION     | DURATION  |              | character or bit string, resp.,
    //            | CHAR(lg1)    | CHAR(lg2) |              | is padded with blanks or zeros,
    //            | BIT(lg1)     | BIT(lg2)  |              | resp., on the right side to match

    @Override
    public Void visitNeRelationalExpression(SmallPearlParser.NeRelationalExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitNeRelationalExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "NE", "no AST attribute found for lhs of operation NE");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "NE", "no AST attribute found for rhs of operation NE");

        checkUnOrderedCompare(op1, op2, "NE / /=", ctx);

        return null;
      }
    
    private Void checkUnOrderedCompare(ASTAttribute op1, ASTAttribute op2, String relation,
        SmallPearlParser.ExpressionContext ctx) {
      ASTAttribute res; 
      ErrorStack.enter(ctx);

      if (op1 != null && op2 != null) {
        if (op1.getType() instanceof TypeBit && op2.getType() instanceof TypeBit) {
          res = new ASTAttribute(new TypeBit(1), op1.isReadOnly() && op2.isReadOnly());
          m_ast.put(ctx, res);

          if (m_debug)
            Log.debug("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#7");
        } else {
          checkOrderedCompare(op1, op2, relation, ctx);

        }
      }
      ErrorStack.leave();
      return null;

    }

    //
    // Reference: OpenPEARL Language Report
    //            Table 6.6: Dyadic comparison operators
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      | Type of   | Result type  | Meaning of operation
    //            | operand 1    | operand 2 |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    //  op1 < op2 | FIXED(g1)    | FIXED(g2) | BIT(1)       | “less than”
    //    or      | FIXED(g1)    | FLOAT(g2) |              | If op1 is less than op2,
    // op1 LT op2 | FLOAT(g1)    | FIXED(g1) |              | the result has value ’1’B,
    //            | FLOAT(g1)    | FLOAT(g2) |              | otherwise ’0’B.
    //            | CLOCK        | CLOCK     |              |
    //            | DURATION     | DURATION  |              |
    //            |              |           |              |
    //            | CHAR(lg1)    | CHAR(lg2) |              | character string comparison
    //            |              |           |              | if lg1 <> lg2 the shorter
    //            |              |           |              | character string is padded with
    //            |              |           |              | spaces on the right side to
    //            |              |           |              | match the length. Then the
    //            |              |           |              | internal represenations are
    //            |              |           |              | compared character by character
    //            |              |           |              | from left to right


    @Override
    public Void visitLtRelationalExpression(SmallPearlParser.LtRelationalExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitLtRelationalExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        op1 = saveGetAttribute(ctx.expression(0), ctx, "LT", "no AST attribute found for lhs of operation LT");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "LT", "no AST attribute found for rhs of operation LT");

        checkOrderedCompare(op1, op2, "LT / <", ctx);

        return null;
      }


    //
    // Reference: OpenPEARL Language Report
    //            Table 6.6: Dyadic comparison operators
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      | Type of   | Result type  | Meaning of operation
    //            | operand 1    | operand 2 |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // op1 <= op2 | FIXED(g1)    | FIXED(g2) | BIT(1)       | “less or equal”
    //    or      | FIXED(g1)    | FLOAT(g2) |              | If op1 is less or equal op2,
    // op1 LE op2 | FLOAT(g1)    | FIXED(g1) |              | the result has value ’1’B,
    //            | FLOAT(g1)    | FLOAT(g2) |              | otherwise ’0’B.
    //            | CLOCK        | CLOCK     |              |
    //            | DURATION     | DURATION  |              |
    //            |              |           |              |
    //            | CHAR(lg1)    | CHAR(lg2) |              | character string comparison
    //            |              |           |              | if lg1 <> lg2 the shorter
    //            |              |           |              | character string is padded with
    //            |              |           |              | spaces on the right side to
    //            |              |           |              | match the length. Then the
    //            |              |           |              | internal represenations are
    //            |              |           |              | compared character by character
    //            |              |           |              | from left to right

    @Override
    public Void visitLeRelationalExpression(SmallPearlParser.LeRelationalExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitLeRelationalExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        op1 = saveGetAttribute(ctx.expression(0), ctx, "LE", "no AST attribute found for lhs of operation LE");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "LE", "no AST attribute found for rhs of operation LE");

        checkOrderedCompare(op1, op2, "LE / <=", ctx);

        return null;
      }
        
    //
    // Reference: OpenPEARL Language Report
    //            Table 6.6: Dyadic comparison operators
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      | Type of   | Result type  | Meaning of operation
    //            | operand 1    | operand 2 |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // op1 > op2  | FIXED(g1)    | FIXED(g2) | BIT(1)       | “greater”
    //    or      | FIXED(g1)    | FLOAT(g2) |              | If op1 is greater op2,
    // op1 GT op2 | FLOAT(g1)    | FIXED(g1) |              | the result has value ’1’B,
    //            | FLOAT(g1)    | FLOAT(g2) |              | otherwise ’0’B.
    //            | CLOCK        | CLOCK     |              |
    //            | DURATION     | DURATION  |              |
    //            |              |           |              |
    //            | CHAR(lg1)    | CHAR(lg2) |              | character string comparison
    //            |              |           |              | if lg1 <> lg2 the shorter
    //            |              |           |              | character string is padded with
    //            |              |           |              | spaces on the right side to
    //            |              |           |              | match the length. Then the
    //            |              |           |              | internal represenations are
    //            |              |           |              | compared character by character
    //            |              |           |              | from left to right

    @Override
    public Void visitGtRelationalExpression(SmallPearlParser.GtRelationalExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitGtRelationalExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "GT", "no AST attribute found for lhs of operation GT");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "GT", "no AST attribute found for rhs of operation GT");
        
        checkOrderedCompare(op1, op2, "GT / >", ctx);

        return null;
      }
        
    //
    // Reference: OpenPEARL Language Report
    //            Table 6.6: Dyadic comparison operators
    // -----------+--------------+-----------+--------------+---------------------------------
    // Expression | Type of      | Type of   | Result type  | Meaning of operation
    //            | operand 1    | operand 2 |              |
    // -----------+------------- +-----------+--------------+---------------------------------
    // op1 >= op2 | FIXED(g1)    | FIXED(g2) | BIT(1)       | “greater or equal”
    //    or      | FIXED(g1)    | FLOAT(g2) |              | If op1 is greater or equal op2,
    // op1 GE op2 | FLOAT(g1)    | FIXED(g1) |              | the result has value ’1’B,
    //            | FLOAT(g1)    | FLOAT(g2) |              | otherwise ’0’B.
    //            | CLOCK        | CLOCK     |              |
    //            | DURATION     | DURATION  |              |
    //            |              |           |              |
    //            | CHAR(lg1)    | CHAR(lg2) |              | character string comparison
    //            |              |           |              | if lg1 <> lg2 the shorter
    //            |              |           |              | character string is padded with
    //            |              |           |              | spaces on the right side to
    //            |              |           |              | match the length. Then the
    //            |              |           |              | internal represenations are
    //            |              |           |              | compared character by character
    //            |              |           |              | from left to right

    @Override
    public Void visitGeRelationalExpression(SmallPearlParser.GeRelationalExpressionContext ctx) {
      ASTAttribute op1;
      ASTAttribute op2;

      Log.debug("ExpressionTypeVisitor:visitGeRelationalExpression:ctx" + CommonUtils.printContext(ctx));

      visit(ctx.expression(0));
      visit(ctx.expression(1));
      
      op1 = saveGetAttribute(ctx.expression(0), ctx, "GE", "no AST attribute found for lhs of operation GE");
      op2 = saveGetAttribute(ctx.expression(1), ctx, "GE", "no AST attribute found for rhs of operation GE");

      checkOrderedCompare(op1, op2, "GE / >=", ctx);

      return null;
    }

    private Void checkOrderedCompare(ASTAttribute op1, ASTAttribute op2, String relation,
        SmallPearlParser.ExpressionContext ctx) {
      ASTAttribute res; 
      
      ErrorStack.enter(ctx);
      
      if (op1 != null && op2 != null) {
        Boolean isReadOnly = op1.isReadOnly() && op2.isReadOnly();
        
        // implicit dereferences
        TypeDefinition type1 = op1.getType();
        if (type1 instanceof TypeReference) {
          type1 = ((TypeReference) type1).getBaseType();
          isReadOnly = false;   // let's do the evaluation during runtime 
        }

        TypeDefinition type2 = op2.getType();
        if (type2 instanceof TypeReference) {
          // implicit dereference
          type2 = ((TypeReference) type2).getBaseType();
          isReadOnly = false;   // let's do the evaluation during runtime 
        }

        
        if (type1 instanceof TypeFixed && type2 instanceof TypeFixed) {
          res = new ASTAttribute(new TypeBit(1), isReadOnly);
          m_ast.put(ctx, res);

          if (m_debug)
            Log.debug("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#1");
        } else if (type1 instanceof TypeFixed && type2 instanceof TypeFloat) {
          res = new ASTAttribute(new TypeBit(1), isReadOnly);
          m_ast.put(ctx, res);

          if (m_debug)
            Log.debug("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#2");
        } else if (type1 instanceof TypeFloat && type2 instanceof TypeFixed) {
          res = new ASTAttribute(new TypeBit(1), isReadOnly);
          m_ast.put(ctx, res);

          if (m_debug)
            System.out.println("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#3");
        } else if (type1 instanceof TypeFloat && type2 instanceof TypeFloat) {
          res = new ASTAttribute(new TypeBit(1), isReadOnly);
          m_ast.put(ctx, res);

          if (m_debug)
            Log.debug("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#4");
        } else if (type1 instanceof TypeClock && type2 instanceof TypeClock) {
          res = new ASTAttribute(new TypeBit(1), isReadOnly);
          m_ast.put(ctx, res);

          if (m_debug)
            Log.debug("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#5");
        } else if (type1 instanceof TypeDuration && type2 instanceof TypeDuration) {
          res = new ASTAttribute(new TypeBit(1), isReadOnly);
          m_ast.put(ctx, res);

          if (m_debug)
            Log.debug("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#6");
        } else if ((type1 instanceof TypeChar || type1 instanceof TypeVariableChar ) && 
            (type2 instanceof TypeChar || type2 instanceof TypeVariableChar ) ) {
          res = new ASTAttribute(new TypeBit(1), isReadOnly);
          m_ast.put(ctx, res);

          if (m_debug)
            Log.debug("ExpressionTypeVisitor: visit\"+relation+\"RelationalExpression: rule#7");
        } else {
          ErrorStack.add("type mismatch: '"+ op1.getType().toString() +"' cannot be compared with '"+
                  op2.getType().getName()+"'");

        }
      }
      ErrorStack.leave();
      return null;
    }

    public Void visitIsRelationalExpression(SmallPearlParser.IsRelationalExpressionContext ctx) {
      Boolean old_autoDereference;
      ASTAttribute op1;
      ASTAttribute op2;

      Log.debug("ExpressionTypeVisitor:visitIsRelationalExpression:ctx" + CommonUtils.printContext(ctx));

      old_autoDereference = m_autoDereference;
      m_autoDereference = false;

      visit(ctx.expression(0));
      visit(ctx.expression(1));

      m_autoDereference = old_autoDereference;      

      op1 = saveGetAttribute(ctx.expression(0), ctx, "IS", "no AST attribute found for lhs of operation IS");
      op2 = saveGetAttribute(ctx.expression(1), ctx, "IS", "no AST attribute found for rhs of operation IS");

      checkIsIsntCompare(op1, op2, "IS", ctx);

      return null;
    }

    public Void visitIsntRelationalExpression(SmallPearlParser.IsntRelationalExpressionContext ctx) {
      Boolean old_autoDereference;
      ASTAttribute op1;
      ASTAttribute op2;

      Log.debug("ExpressionTypeVisitor:visitIsntRelationalExpression:ctx" + CommonUtils.printContext(ctx));

      old_autoDereference = m_autoDereference;
      m_autoDereference = false;

      visit(ctx.expression(0));
      visit(ctx.expression(1));
      
      m_autoDereference = old_autoDereference;      

      op1 = saveGetAttribute(ctx.expression(0), ctx, "ISNT", "no AST attribute found for lhs of operation ISNT");
      op2 = saveGetAttribute(ctx.expression(1), ctx, "ISNT", "no AST attribute found for rhs of operation ISNT");

      checkIsIsntCompare(op1, op2, "ISNT", ctx);

      return null;
    }

    private Void checkIsIsntCompare(ASTAttribute op1, ASTAttribute op2, String relation,
        SmallPearlParser.ExpressionContext ctx) {
      ASTAttribute res; 
      
      ErrorStack.enter(ctx);
      
      if (m_debug)
        Log.debug("ExpressionTypeVisitor: visit"+relation+"RelationalExpression: rule#1");
      if (op1 != null && op2 != null) {
        Boolean typeMismatch = true;
        
        // at least one must be references with the same base type
        // but NIL does not count here as reference
        TypeDefinition type1 = op1.getType();
        TypeDefinition type2 = op2.getType();
        
        int nbrOfReferences = 0;
        
        if (type1 instanceof TypeReference || type2 instanceof TypeReference) {
          if (type1 instanceof TypeReference) {
             type1 = ((TypeReference) type1).getBaseType();
             if (type1 != null) {
               nbrOfReferences ++;
             }
          }
          if (type2 instanceof TypeReference) {
            type2 = ((TypeReference) type2).getBaseType();
            if (type2 != null) {
              nbrOfReferences++;
            }
          }
          
          if (nbrOfReferences > 0) {
            // typeX == null means is 'NIL' 
            if (type1 == null || type2 == null || type1.equals(type2)) {
              typeMismatch = false;
            } else 
            if ((type1 instanceof TypeArray && type2 instanceof TypeArraySpecification) ) {
              if (((TypeArray)type1).getBaseType().equals(((TypeArraySpecification)type2).getBaseType())) {
                typeMismatch = false;
              }
            }
            if ((type2 instanceof TypeArray && type1 instanceof TypeArraySpecification) ) {
              if (((TypeArray)type2).getBaseType().equals(((TypeArraySpecification)type1).getBaseType())) {
                typeMismatch = false;
              }
            } else
            if ((type1 == null && type2 instanceof TypeArraySpecification) ||
                (type2 == null && type1 instanceof TypeArraySpecification) ||
                type1.equals(type2)  ){
              typeMismatch = false;
            }
          }
        }
        
        if (typeMismatch) {
          ErrorStack.add("type mismatch: '"+ op1.getType().toString() +"' cannot be compared as "+relation+" with '"+
                  op2.getType().getName()+"'");
        } else {
          res = new ASTAttribute(new TypeBit(1));
          m_ast.put(ctx, res);
        }
      }
      ErrorStack.leave();
      return null;
    }
    
    @Override
    public Void visitStringSelection(SmallPearlParser.StringSelectionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitStringSelection:ctx" + CommonUtils.printContext(ctx));
        
        ASTAttribute attr = null;
//        ASTAttribute attrName = null;
        visitChildren(ctx);
        
        if (ctx.bitSelection()!= null) {
          attr = m_ast.lookup(ctx.bitSelection().bitSelectionSlice());
//          attrName = m_ast.lookup(ctx.bitSelection().name());
          m_ast.put(ctx.bitSelection(), attr);
        } else if (ctx.charSelection() != null) {
          attr = m_ast.lookup(ctx.charSelection().charSelectionSlice());
//          attrName = m_ast.lookup(ctx.charSelection().name());
          m_ast.put(ctx.charSelection(), attr);
        }
        m_ast.put(ctx, attr);
        return null;
    }


    @Override
    public Void visitCshiftExpression(SmallPearlParser.CshiftExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitCshiftExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        op1 = saveGetAttribute(ctx.expression(0), ctx, "CSHIFT", "no AST attribute found for lhs of operation CSHIFT");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "CSHIFT", "no AST attribute found for rhs of operation CSHIFT");

        treatShiftCshift(ctx, op1, op2, "CSHIFT");

        return null;
    }


    @Override
    public Void visitShiftExpression(SmallPearlParser.ShiftExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitShiftExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        op1 = saveGetAttribute(ctx.expression(0), ctx, "SHIFT", "no AST attribute found for lhs of operation SHIFT");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "SHIFT", "no AST attribute found for rhs of operation SHIFT");


        treatShiftCshift(ctx, op1, op2, "SHIFT");

        return null;
    }

    private void treatShiftCshift(ParserRuleContext ctx, ASTAttribute op1, ASTAttribute op2, String operation) {
      ErrorStack.enter(ctx,operation);
      if (op1 != null && op2 != null) {
        // implicit dereferences
        TypeDefinition type1 = op1.getType();
        if (type1 instanceof TypeReference) {
          type1 = ((TypeReference) type1).getBaseType();
        }

        TypeDefinition type2 = op2.getType();
        if (type2 instanceof TypeReference) {
          // implicit dereference
          type2 = ((TypeReference) type2).getBaseType();
        }
        
        if (type1 instanceof TypeBit && type2 instanceof TypeFixed) {
          TypeBit type = new TypeBit(type1.getPrecision());
          ASTAttribute expressionResult = new ASTAttribute(type);
          m_ast.put(ctx, expressionResult);

          Log.debug("ExpressionTypeVisitor: Dyadic Boolean and shift operators");
        
        } else {
          ErrorStack.add("type mismatch: expected BIT "+operation+" FIXED -- got: "+
              op1.getType().toString()+" "+operation + " "+op2.getType().toString());
              //throw new TypeMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }
      }
      
      ErrorStack.leave();
    }
    
    @Override
    public Void visitCatExpression(SmallPearlParser.CatExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitCatExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "CAT", "no AST attribute found for lhs of operation CAT");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "CAT", "no AST attribute found for rhs of operation CAT");
        
        ErrorStack.enter(ctx,"CAT");
        op1 = m_ast.lookup(ctx.expression(0));

        if (op1 != null && op2 != null) {
          // implicit dereferences
          TypeDefinition type1 = op1.getType();
          if (type1 instanceof TypeReference) {
            type1 = ((TypeReference) type1).getBaseType();
          }

          TypeDefinition type2 = op2.getType();
          if (type2 instanceof TypeReference) {
            // implicit dereference
            type2 = ((TypeReference) type2).getBaseType();
          }
          
          if (type1 instanceof TypeBit && type2 instanceof TypeBit) {
            TypeBit type = new TypeBit(type1.getPrecision() + type2.getPrecision());
            ASTAttribute expressionResult = new ASTAttribute(type);
            m_ast.put(ctx, expressionResult);
          } else if (type1 instanceof TypeChar && type2 instanceof TypeChar) {
            TypeChar type = new TypeChar(type1.getPrecision() + type2.getPrecision());
            ASTAttribute expressionResult = new ASTAttribute(type);
            m_ast.put(ctx, expressionResult);
          } else {
            ErrorStack.add("type mismatch: expected BIT CAT BIT or CHAR CAT CHAR -- got "+
                op1.getType().toString()+" CAT "+op2.getType().toString());
          }
        }
        ErrorStack.leave();
        return null;
    }

    @Override
    public Void visitAndExpression(SmallPearlParser.AndExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitAndExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        op1 = saveGetAttribute(ctx.expression(0), ctx, "AND", "no AST attribute found for lhs of operation AND");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "AND", "no AST attribute found for rhs of operation AND");

        treatAndOrExor(ctx, op1, op2, "AND");

        return null;
    }

    @Override
    public Void visitOrExpression(SmallPearlParser.OrExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitOrExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));
        
        op1 = saveGetAttribute(ctx.expression(0), ctx, "OR", "no AST attribute found for lhs of operation OR");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "OR", "no AST attribute found for rhs of operation OR");

        treatAndOrExor(ctx, op1, op2, "OR");

        return null;
    }

    @Override
    public Void visitExorExpression(SmallPearlParser.ExorExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitExorExpression:ctx" + CommonUtils.printContext(ctx));

        visit(ctx.expression(0));
        visit(ctx.expression(1));

        op1 = saveGetAttribute(ctx.expression(0), ctx, "EXOR", "no AST attribute found for lhs of operation EXOR");
        op2 = saveGetAttribute(ctx.expression(1), ctx, "EXOR", "no AST attribute found for rhs of operation EXOR");

        treatAndOrExor(ctx, op1, op2, "EXOR");

        return null;
    }

    private void treatAndOrExor(ParserRuleContext ctx, ASTAttribute op1, ASTAttribute op2, String operation) {
      ErrorStack.enter(ctx,operation);
      
      if (op1 != null && op2 != null) {
        
        // implicit dereferences
        TypeDefinition type1 = op1.getType();
        if (type1 instanceof TypeReference) {
          type1 = ((TypeReference) type1).getBaseType();
        }

        TypeDefinition type2 = op2.getType();
        if (type2 instanceof TypeReference) {
          // implicit dereference
          type2 = ((TypeReference) type2).getBaseType();
        }
        
        if (type1 instanceof TypeBit && type2 instanceof TypeBit) {
            TypeBit type = new TypeBit( Math.max( type1.getPrecision(),type2.getPrecision()));
            ASTAttribute expressionResult = new ASTAttribute(type);
           m_ast.put(ctx, expressionResult);
        }
        else {
          ErrorStack.add("type mismatch: expected BIT +"+operation+" BIT -- got"+
              op1.getType().toString()+" CAT "+op2.getType().toString());
        }
      }
      ErrorStack.leave();
    }
    
    @Override
    public Void visitCONTExpression(SmallPearlParser.CONTExpressionContext ctx) {
        Boolean old_m_autoDereference;
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitCONTExpression:ctx" + CommonUtils.printContext(ctx));

        old_m_autoDereference = m_autoDereference;
        m_autoDereference = false;

        visit(ctx.expression());

        m_autoDereference = old_m_autoDereference;

        op = saveGetAttribute(ctx.expression(), ctx, "CONT", "no AST attribute found for CONT");
        
        ErrorStack.enter(ctx, "CONT");

        if (op != null) {

          if (op.getType() instanceof TypeReference) {
            ASTAttribute expressionResult = new ASTAttribute( ((TypeReference)(op.getType())).getBaseType());
            m_ast.put(ctx, expressionResult);
            Log.debug("ExpressionTypeVisitor: CONT: rule#1");
          } else {
            ErrorStack.add("need type reference -- got "+ op.getType().toString());
          }
        }
        ErrorStack.leave();
        
        return null;
    }

    @Override
    public Void visitStringSlice(SmallPearlParser.StringSliceContext ctx) {
        ASTAttribute res = null;

        Log.debug("ExpressionTypeVisitor:visitStringSlice:ctx" + CommonUtils.printContext(ctx));

        if ( ctx.bitSlice() != null ) {
            int bits = 0;
            if ( ctx.bitSlice() instanceof SmallPearlParser.Case1BitSliceContext) {
                bits = 1;
            } else if ( ctx.bitSlice() instanceof SmallPearlParser.Case2BitSliceContext) {
                SmallPearlParser.Case2BitSliceContext ctx1 = (SmallPearlParser.Case2BitSliceContext)ctx.bitSlice();
                long lowerBoundary;
                long upperBoundary;
                ConstantFixedExpressionEvaluator evaluator = new ConstantFixedExpressionEvaluator(m_verbose, m_debug, m_currentSymbolTable,null, null);

                ConstantValue lower = evaluator.visit(ctx1.constantFixedExpression(0));
                ConstantValue upper = evaluator.visit(ctx1.constantFixedExpression(1));

                if ( !(lower instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                }

                if ( !(upper instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                }

                lowerBoundary = ((ConstantFixedValue) lower).getValue();
                upperBoundary = ((ConstantFixedValue) upper).getValue();


                bits = (int)upperBoundary - (int)lowerBoundary + 1;
            } else if ( ctx.bitSlice() instanceof SmallPearlParser.Case3BitSliceContext) {
              ErrorStack.addInternal(ctx,".BIT(:)","case3 missing");
            }

           res = new ASTAttribute(new TypeBit(bits));
        }
        else if ( ctx.charSlice() != null ) {

            if ( ctx.charSlice() instanceof SmallPearlParser.Case1CharSliceContext) {
                 visitCase1CharSlice((SmallPearlParser.Case1CharSliceContext)ctx.charSlice());
//                if (expressionResult != null) {
//                    m_ast.put(ctx, expressionResult);
//                } else {
//                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//                }
            }
//            else if ( ctx.charSlice() instanceof SmallPearlParser.Case2CharSliceContext) {
//                visitCase2CharSlice((SmallPearlParser.Case2CharSliceContext) ctx.charSlice());
//                ASTAttribute expressionResult = m_ast.lookup(ctx.charSlice());
//                if (expressionResult != null) {
//                    m_ast.put(ctx, expressionResult);
//                } else {
//                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//                }
//            }
            else if ( ctx.charSlice() instanceof SmallPearlParser.Case3CharSliceContext) {
                visitCase3CharSlice((SmallPearlParser.Case3CharSliceContext) ctx.charSlice());
            }
            else if ( ctx.charSlice() instanceof SmallPearlParser.Case4CharSliceContext) {
                visitCase4CharSlice((SmallPearlParser.Case4CharSliceContext) ctx.charSlice());
            }
            res = m_ast.lookup(ctx.charSlice());
        }
        else {
            throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        m_ast.put(ctx, res);

        return null;
    }
    


    @Override
    public Void visitConstantFixedExpression(SmallPearlParser.ConstantFixedExpressionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitConstantFixedExpression:ctx" + CommonUtils.printContext(ctx));
      
        ConstantFixedExpressionEvaluator evaluator = new ConstantFixedExpressionEvaluator(m_verbose, m_debug, m_currentSymbolTable,null, null);

        ConstantFixedValue c = evaluator.visit(ctx);
        
        ASTAttribute attr = new ASTAttribute(new TypeFixed(c.getPrecision()));
        attr.setConstant(c);
        m_ast.put(ctx,attr);
        
        return null;
    }

    @Override
    public Void visitConstantFixedExpressionFit(SmallPearlParser.ConstantFixedExpressionFitContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;

        Log.debug("ExpressionTypeVisitor:visitConstantFixedExpressionFit:ctx" + CommonUtils.printContext(ctx));
        
        ErrorStack.addInternal(ctx, "FIT", "code for constant fixed expression missing");
        
//        visit(ctx.expression(0));
//        op1 = m_ast.lookup(ctx.expression(0));
//
//        if (op1 == null) {
//            throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        visit(ctx.expression(1));
//        op2 = m_ast.lookup(ctx.expression(1));
//
//        if (op2 == null) {
//            throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        if (op1.getType() instanceof TypeBit && op2.getType() instanceof TypeBit) {
//            TypeBit type = new TypeBit( Math.max( ((TypeBit)op1.getType()).getPrecision(),((TypeBit)op2.getType()).getPrecision()));
//            ASTAttribute expressionResult = new ASTAttribute(type);
//            m_ast.put(ctx, expressionResult);
//        }
//        else {
//            throw new TypeMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }

        return null;
    }

    @Override
    public Void visitInitElement(SmallPearlParser.InitElementContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitInitElement:ctx" + CommonUtils.printContext(ctx));

        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitConstantExpression(SmallPearlParser.ConstantExpressionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitConstantExpression:ctx" + CommonUtils.printContext(ctx));

        visitChildren(ctx);
        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.9 Other dyadic operators
    //
    // -------------+-----------+-----------+-------------+---------------------------------
    // Expression   | Type of   | Type of   | Result type | Meaning of operation
    //              | operand 1 | operand 2 |             |
    // -------------+-----------+-----------+-------------+---------------------------------
    // op1 LWB op2  | FIXED(g)  | array     | FIXED(31)   | lower boundary of the dimension
    //              |           |           |             | (given by op1) of the array
    //              |           |           |             | (determined by op2), if existing
    // -------------+-----------+-----------+-------------+---------------------------------
    // op1 UPB op2  | FIXED(g)  | array     | FIXED(31)   | upper boundary of the dimension
    //              |           |           |             | (given by op1) of the array
    //              |           |           |             | (determined by op2), if existing
    // -------------+-----------+-----------+-------------+---------------------------------

    @Override
    public Void visitLwbDyadicExpression(SmallPearlParser.LwbDyadicExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitLwbDyadicExpression:ctx" + CommonUtils.printContext(ctx));

        res = new ASTAttribute(new TypeFixed(31), false);
        m_ast.put(ctx, res);
        visitChildren(ctx);

        return null;
    }

    @Override
    public Void visitUpbDyadicExpression(SmallPearlParser.UpbDyadicExpressionContext ctx) {
        ASTAttribute op1;
        ASTAttribute op2;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitUpbDyadicExpression:ctx" + CommonUtils.printContext(ctx));

        res = new ASTAttribute(new TypeFixed(31), false);
        m_ast.put(ctx, res);
        visitChildren(ctx);
        return null;
    }

    //
    // Reference: OpenPEARL Language Report 6.4 Other monadic perators
    //
    // -------------+---------------+-------------+---------------------------------
    // Expression   | Type of       | Result type | Meaning of operation
    //              | operand       |             |
    // -------------+---------------+-------------+---------------------------------
    // LWB a        | array         | FIXED(31)   | lower boundary of the first
    //              |               |             | dimension of the operand array
    // -------------+---------------+-------------+---------------------------------
    // UPB a        | array         | FIXED(31)   | upper boundary of the first
    //              |               |             | dimension of the operand array
    //              +---------------+-------------+---------------------------------
    //              | CHARACTER(lg) | FIXED(15)   | result := lg
    // -------------+---------------+-------------+---------------------------------

    @Override
    public Void visitLwbMonadicExpression(SmallPearlParser.LwbMonadicExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitLwbMonadicExpression:ctx" + CommonUtils.printContext(ctx));

        res = new ASTAttribute(new TypeFixed(31), false);
        m_ast.put(ctx, res);
        visitChildren(ctx);
        
        return null;
    }

    @Override
    public Void visitUpbMonadicExpression(SmallPearlParser.UpbMonadicExpressionContext ctx) {
        ASTAttribute op;
        ASTAttribute res;

        Log.debug("ExpressionTypeVisitor:visitUpbMonadicExpression:ctx" + CommonUtils.printContext(ctx));

        res = new ASTAttribute(new TypeFixed(31), false);
        m_ast.put(ctx, res);
        visitChildren(ctx);
        
        return null;
    }

    @Override
    public Void visitCase1CharSlice(SmallPearlParser.Case1CharSliceContext ctx) {
        ASTAttribute op;
        ASTAttribute res;
        ASTAttribute attr = m_ast.lookup(ctx);

        Log.debug("ExpressionTypeVisitor:visitCase1CharSlice:ctx" + CommonUtils.printContext(ctx));
        Log.debug("ExpressionTypeVisitor:visitCase1CharSlice:id=" + ctx.ID().getText());

        SymbolTableEntry entry = this.m_currentSymbolTable.lookup(ctx.ID().getText());

        visit(ctx.expression());

        ErrorStack.enter(ctx,".CHAR()");
        ASTAttribute expressionResult = m_ast.lookup(ctx.expression());
        if (expressionResult != null) {
            if ( expressionResult.isReadOnly()) {
                if (expressionResult.getType() instanceof TypeFixed ) {
                    m_ast.put(ctx, new ASTAttribute(new TypeChar(1)));
                }
                else {
                  ErrorStack.addInternal("constant fixed expression expected");
                }
            }
            else {
                if ( entry instanceof VariableEntry) {
                    VariableEntry var = (VariableEntry) entry;
                    if ( var.getType() instanceof TypeChar) {
                        m_ast.put(ctx, new ASTAttribute(new TypeChar(1)));
                    }
                    else {
                      ErrorStack.add("type mismatch: '"+var.getName()+"' must be of type CHAR -- but is "+
                          var.getType().toString());
                    }
                } else {
                  ErrorStack.add("need variable");
                }
            }
        } else {
          ErrorStack.addInternal("expression missing");
        }
        
        ErrorStack.leave();
        return null;
    }

    @Override
    public Void visitCase3CharSlice(SmallPearlParser.Case3CharSliceContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitCase3CharSlice:ctx" + CommonUtils.printContext(ctx));

        ErrorStack.enter(ctx,".CHAR(x:x+y)");
        
        int intConst = Integer.parseInt(ctx.IntegerConstant().toString());
        
        ASTAttribute lwb = m_ast.lookup(ctx.expression(0));
        ASTAttribute upb = m_ast.lookup(ctx.expression(1));


        SymbolTableEntry entry = this.m_currentSymbolTable.lookup(ctx.ID().getText());
        
        VariableEntry var = null;
        if (entry instanceof VariableEntry) {
          var = (VariableEntry) entry;
          if (!( var.getType() instanceof TypeChar)) {
            ErrorStack.add("must be of type CHAR -- but is "+var.getType().toString());
          } 
        } else {
          ErrorStack.addInternal("need variable");
          ErrorStack.leave();
          return null;
        }
        
        String ex0 = ctx.expression(0).getText();
        String ex1 = ctx.expression(1).getText();
        if (ex0.equals(ex1)) {
          visit(ctx.expression(0));
          visit(ctx.expression(1));
          
          // check types -- all must be of type fixed
          for (int i=0; i<2; i++) {
            ASTAttribute attr = m_ast.lookup(ctx.expression(i));
            if (attr == null) {
              ErrorStack.addInternal("no AST attribute found for expression "+i);
            } else {
              if (!(attr.getType() instanceof TypeFixed)) {
                ErrorStack.add(ctx.expression(i),null,"must be of type FIXED");
              }
            }
          }
          m_ast.put(ctx, new ASTAttribute(new TypeChar(intConst)));
        
        } else {
          // we must treat this as case4!
          // set the attribute for smooth further checking
          m_ast.put(ctx, new ASTAttribute(new TypeVariableChar()));
        }

        ErrorStack.leave();
        return null;
   }

    @Override
    public Void visitCase4CharSlice(SmallPearlParser.Case4CharSliceContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitCase4CharSlice:ctx" + CommonUtils.printContext(ctx));
        long size = -1;  // preset with an illegal size
        TypeDefinition td = new TypeChar();  // set a default result
        
        ErrorStack.enter(ctx,".CHAR(:)");
        
        visitChildren(ctx);

        ASTAttribute lwb = saveGetAttribute(ctx.expression(0), ctx, ".CHAR(x:y)", "no AST attribute found for lhs of operation .CHAR(x:y)");
        ASTAttribute upb = saveGetAttribute(ctx.expression(1), ctx, ".CHAR(x:y)", "no AST attribute found for rhs of operation .CHAR(x:y)");
      
        SymbolTableEntry entry = this.m_currentSymbolTable.lookup(ctx.ID().getText());
       
        VariableEntry var = null;
        if (entry instanceof VariableEntry) {
          var = (VariableEntry) entry;
          if (!( var.getType() instanceof TypeChar)) {
            ErrorStack.add("must be of type CHAR -- but is "+var.getType().toString());
          } 
        } else {
          ErrorStack.addInternal("need variable");
          ErrorStack.leave();
          return null;
        }

        if ( lwb.isReadOnly() && upb.isReadOnly()) {
          size = upb.getConstantFixedValue().getValue() - lwb.getConstantFixedValue().getValue() + 1;
          if (size < 1) {
            ErrorStack.add("must select at least 1 character");
          } else if (size > Defaults.CHARACTER_MAX_LENGTH) {
            ErrorStack.add("must select max " + Defaults.CHARACTER_MAX_LENGTH+" characters");
            size = Defaults.CHARACTER_MAX_LENGTH;
          } else {
            td = new TypeChar((int)size);
          }
        } else {
          td = new TypeVariableChar();
        } 
        m_ast.put(ctx, new ASTAttribute(td)); 
        
        ErrorStack.leave();
        
        return null;
    }
    

    @Override
    public Void visitCharSelection(SmallPearlParser.CharSelectionContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitCharSelection:ctx" + CommonUtils.printContext(ctx));
        visitName(ctx.name());
        visitCharSelectionSlice(ctx.charSelectionSlice());
      
        ASTAttribute attrSelection = m_ast.lookup(ctx.charSelectionSlice());
        if (attrSelection.getType() instanceof TypeVariableChar) {
           // in the code generation we need the size of the char variable
           ASTAttribute attrName = m_ast.lookup(ctx.name());
           ((TypeVariableChar)attrSelection.getType()).setBaseType(attrName.getType());
        }

        m_ast.put(ctx.charSelectionSlice(),attrSelection);
        
        return null;
    }


    /*
     bitSelectionSlice:
    '.' 'BIT' '(' 
    (
         expression
       | constantFixedExpression ':' constantFixedExpression
       | expression ':' expression '+' IntegerConstant  
    )  
    ')'
    ;
     */
    @Override
    public Void visitBitSelectionSlice(SmallPearlParser.BitSelectionSliceContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitBitSelectionSlice:ctx" + CommonUtils.printContext(ctx));
        
        ASTAttribute attr0 = null;
        ASTAttribute attr1 = null;
        ASTAttribute result = null;
        
        String expr0 = null;
        String expr1 = null;
        int intValue = -1;    // impossible value, since the Fixed-const is always >= 0
                
        visitChildren(ctx);

        ErrorStack.enter(ctx,".BIT()");

        // we must consider 3 cases for .BIT(expr0:expr1+CONST_FIXED)
        //   expr0 present; expr1 missing (implied CONST_FIXED missing)
        //     --> result: BIT(1); 
        //         ConstantSlice will become set if expr1 is constant
        //   expr0 and expr1 present; CONST_FIXED missing
        //     --> both expressions must be constant
        //         --> result is calculated from the constants
        //             ConstantSlice will be set
        //   all 3 present
        //     --> both expressions must be equal
        //        --> result is derived form the CONST_FIXED
        //           ConstantSlice may be set if expr0/1 are constant, but 
        //           difficult to calculate
        
        
        if (ctx.expression(0) != null) {
          attr0 = m_ast.lookup(ctx.expression(0));
          expr0 = ctx.expression(0).getText();
        
        } else {
          ErrorStack.addInternal("visitBitSelectionSlice: missing first expression"); 
        }

        if (ctx.expression(1) != null) {
           attr1 = m_ast.lookup(ctx.expression(1));
           expr1 = ctx.expression(1).getText();
        }
        
        if (ctx.IntegerConstant() != null) {
          intValue = Integer.parseInt(ctx.IntegerConstant().getText());
        }

        if (expr1 == null) {
          result = new ASTAttribute(new TypeBit(1));
          if (attr0.getConstant() != null) {
            ConstantSelection slice = new ConstantSelection(attr0.getConstantFixedValue(),attr0.getConstantFixedValue());
            result.setConstantSelection(slice);
          }
        } else {
          if (intValue == -1) {
            if (attr0.getConstant() != null && attr1.getConstant() != null) {
              long start = attr0.getConstantFixedValue().getValue();
              long end   = attr1.getConstantFixedValue().getValue();
              
              long size = end - start + 1 ;
              if (size <= 0) {
                ErrorStack.add("must select at least 1 bit");
                size =1; // for easy method completion
              }
              result = new ASTAttribute(new TypeBit((int)size));
              
              ConstantSelection slice = new ConstantSelection(attr0.getConstantFixedValue(),attr1.getConstantFixedValue());
              result.setConstantSelection(slice);
            } else {
              if (expr0.equals(expr1)) {
                result = new ASTAttribute(new TypeBit(1));
              }
            }
          } else {
            if (!expr0.equals(expr1)) {
              ErrorStack.add(".BIT(expr1:expr2+FIXED_CONST need identical expressions");
              result = new ASTAttribute(new TypeBit(1)); // dummy value for easy method completion
            } else {
              result = new ASTAttribute(new TypeBit(intValue+1));
            }
          }
        }
        m_ast.put(ctx, result);
                 
        ErrorStack.leave();
        return null;
    }
    
    @Override
    public Void visitCharSelectionSlice(SmallPearlParser.CharSelectionSliceContext ctx) {

        Log.debug("ExpressionTypeVisitor:visitCharSelectionSlice:ctx" + CommonUtils.printContext(ctx));

        ASTAttribute attr0 = null;
        ASTAttribute attr1 = null;
        String expr0 = null;
        String expr1 = null;
        int intValue = -1;    // impossible value, since the Fixed-const is always >= 0
                
        visitChildren(ctx);

        ErrorStack.enter(ctx,".CHAR()");

        if (ctx.IntegerConstant() != null) {
          intValue = Integer.parseInt(ctx.IntegerConstant().getText());
        }
        
        if (ctx.expression(0) != null) {
          attr0 = m_ast.lookup(ctx.expression(0));
          expr0 = ctx.expression(0).getText();
        
        } else {
          ErrorStack.addInternal("visitCharSelectionSlice: missing alternative"); 
        }
        
        if (ctx.expression(1) != null) {
           attr1 = m_ast.lookup(ctx.expression(1));
           expr1 = ctx.expression(1).getText();
        } else {
          ASTAttribute attr = new ASTAttribute(new TypeChar(1));
         
          if (attr0.getConstant() != null) {
            ConstantSelection slice = new ConstantSelection(attr0.getConstantFixedValue(),attr0.getConstantFixedValue());
            attr.setConstantSelection(slice);
          }
          
          m_ast.put(ctx, attr);
        } 
        
        if (attr1 != null) {
          // check if we have 2 constants
          if (attr0.getConstant() != null && attr1.getConstant() != null) {
            
            long start = attr0.getConstantFixedValue().getValue();
            long end   = attr1.getConstantFixedValue().getValue();
            
            long size = end - start + 1 ;
            if (intValue >= 0) {
              size += intValue;
            }
            if (size <= 0) {
              ErrorStack.add("must select at least 1 character");
            }

            ConstantSelection slice = new ConstantSelection(attr0.getConstantFixedValue(),
                                                            attr1.getConstantFixedValue());
  
            ASTAttribute attr = new ASTAttribute(new TypeChar((int)size));
            attr.setConstantSelection(slice);
            m_ast.put(ctx, attr);
          } else {
            // we have 2 expressions + intValue
            if (expr0.equals(expr1) && intValue >= 0) {
              m_ast.put(ctx, new ASTAttribute(new TypeChar(intValue+1)));
            } else {
              m_ast.put(ctx, new ASTAttribute(new TypeVariableChar()));  
            }
          }
        }
        ErrorStack.leave();

        return null;
    }


    @Override
    public Void visitIndices(SmallPearlParser.IndicesContext ctx) {
        return visitChildren(ctx);
    }
    
    private ConstantValue getConstantExpression(SmallPearlParser.ConstantExpressionContext ctx) {
        ConstantFixedExpressionEvaluator evaluator = new ConstantFixedExpressionEvaluator(m_verbose, m_debug, m_currentSymbolTable,null, null);
        ConstantValue constant = evaluator.visit(ctx.constantFixedExpression());

        return constant;
    }

    @Override
    public Void visitName(SmallPearlParser.NameContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitName:ctx=" + CommonUtils.printContext(ctx));
        Log.debug("ExpressionTypeVisitor:visitName:id=" + ctx.ID().toString());

        m_nameDepth = 0;
        m_isFunctionCall = false;

        ErrorStack.enter(ctx, ctx.ID().toString());

        SymbolTableEntry entry = m_currentSymbolTable.lookup(ctx.ID().getText());

        if (entry != null) {
          if (entry instanceof VariableEntry) {
             VariableEntry var = (VariableEntry) entry;
             TypeDefinition typ = var.getType();
             m_type = typ;

             if (ctx.name() != null || ctx.listOfExpression() != null) {
               // we must have a more detailed look if there is 
               // another name (--> struct component)
               // or a listOfExpression (-->array or procedure call)
                reVisitName(ctx);
             }

		     ASTAttribute attr = new ASTAttribute(m_type, var.getAssigmentProtection(), var);
		     m_ast.put(ctx, attr);
		    } else {
			if (treatTaskSemaBoltSignalInterruptDation(ctx, entry)) {
			    ASTAttribute attr = new ASTAttribute(m_type,entry);
			    m_ast.put(ctx, attr);
			} else {
			    if (entry instanceof ModuleEntry) {
				ErrorStack.add("illegal usage of module name");
			    } else if (entry instanceof TypeEntry) {
				ErrorStack.add("illegal usage of type name");
			    } else if (entry instanceof ProcedureEntry) {
			       ProcedureEntry pe = (ProcedureEntry) entry;
			       if (ctx.listOfExpression() != null) {
				 m_isFunctionCall = true;
				 visitChildren(ctx.listOfExpression());
				 m_type = ((ProcedureEntry) entry).getResultType();
			       } else {
				 // maybe it is a function call, or not
				 // this depends if it is on rhs of an assignment
				 // this is checked in the semantic analysis CheckAssignment
				 m_type = new TypeProcedure(((ProcedureEntry) entry).getFormalParameters(),
				     ((ProcedureEntry) entry).getResultType());
			      }
			      m_ast.put(ctx,  new ASTAttribute(m_type,pe)); 
			      if (m_type != null) {
	//                       m_ast.put(ctx, new ASTAttribute(m_type, entry));
			      } else {
				 ErrorStack.add("procedure '" + ((ProcedureEntry) entry).getName() + "' does not return a value");
			      }
			      //if (ctx.listOfExpression() != null) {
			      //      visitChildren(ctx.listOfExpression());
			      //  }
			    } else {
				ErrorStack.addInternal("illegal usage of ???");
			    }
			}
		    }
		} else {
		    ErrorStack.add("'" + ctx.ID().getText() + "' is not defined");
		}

		ErrorStack.leave();
		return null;
	    }

	    private boolean treatTaskSemaBoltSignalInterruptDation(NameContext ctx,
		SymbolTableEntry entry) {
	      TypeDefinition typ = null;
	    
	      if (entry instanceof TaskEntry) {
		 typ = new TypeTask();  
	      } else if (entry instanceof SemaphoreEntry) {
		typ = new TypeSemaphore();
	      } else if (entry instanceof BoltEntry) {
		typ = new TypeBolt();
	      } else if (entry instanceof InterruptEntry) {
		typ = new TypeInterrupt();
	    
	// the following elements are not supported yet        
	 //     } else if (entry instanceof DationEntry) {
	 //       typ = new TypeDation();
	 //     } else if (entry instanceof SignalEntry) {
	 //       typ = new TypeSignal();
	      } 
	      
	      if (typ != null) {
		  m_type = typ;
		  return true;
	      }
	      return false;
	      
	    }

	    /**
	     * iterate over name recursion levels
	     * 
	     */

	/*
	    private  Void reVisitName(SmallPearlParser.NameContext ctx) {
      TypeDefinition currentType = m_type;
      
String s = ctx.getText();
      if (currentType instanceof TypeReference) {
          currentType = ((TypeReference)currentType).getBaseType();
      }

      if (currentType instanceof TypeArraySpecification) {
        // REF () anyType
        if (ctx.listOfExpression()!= null) {
          // array indices given -> m_type is baseType() and iterate on next levels
          // see next if with ctx.name() != null
          visit(ctx.listOfExpression());
          m_type = ((TypeArraySpecification) currentType).getBaseType();
        }
      } else 
      if ( m_type instanceof TypeArray) {
          // resolve the index list if given for the array
          if (ctx.listOfExpression()!= null) {
            // array indices given -> m_type is baseType() and iterate on next levels
            // see next if with ctx.name() != null
            visit(ctx.listOfExpression());
            m_type = ((TypeArray) currentType).getBaseType();
          } else {
            // no array indices given --> no name may by given
            if (ctx.name() != null) {
              ErrorStack.add("need array element for next struct component");
              return null; // abort type resolving
            }
          }
      } else if (m_type instanceof TypeStructure) {
        TypeStructure typ = (TypeStructure)m_type;
        String ss = ctx.ID().getText();
        if (m_nameDepth == 0 && ctx.name() != null) {
          m_nameDepth++;
          reVisitName(ctx.name());
          m_nameDepth--;
        } else {
          StructureComponent component = typ.lookup(ctx.ID().getText());

          if (component != null) {
            if ( component.m_type instanceof TypeArray) {
                m_type = component.m_type;
                if (ctx.listOfExpression() != null) {
                  visit(ctx.listOfExpression());
                  m_type = ((TypeArray) component.m_type).getBaseType();
                  if (m_type instanceof TypeStructure  && ctx.name() != null) {
                     m_nameDepth++;
                     reVisitName(ctx.name());
                     m_nameDepth--;
                  } else if (!(m_type instanceof TypeStructure) && ctx.name() != null) {
                    ErrorStack.add(ctx,null,"need struct for component selection");
                  }
                  
                } else if (ctx.name() != null) {
                  ErrorStack.add(ctx,null,"need struct array element for component selection");
                }
            } else if ( component.m_type instanceof TypeStructure) {
                m_type = component.m_type;
                m_nameDepth++;
                reVisitName(ctx.name());
                m_nameDepth--;
            } else {
              if (ctx.listOfExpression() != null) {
                ErrorStack.add(ctx,null,"need array for index list");
              }
               m_type = component.m_type;
            }
          } else {
            ErrorStack.add("'"+ctx.ID().getText()+"' is no STRUCT component");
          }
        }
      }

      return null;
      
    }
   */

 /**
     * iterate over name recursion levels
     */
    private Void reVisitName(SmallPearlParser.NameContext ctx) {
        Log.debug("ExpressionTypeVisitor:reVisitName:ctx" + CommonUtils.printContext(ctx));
        TypeDefinition currentType = m_type;

        if ( ctx == null) {
            return null;
        }

        
        // auto dereference on rhs
        // do this only on intermediate elements
        // do not dereference of no listOfExpressions or no name is present
        if (m_type instanceof TypeReference && m_autoDereference == true &&
            (ctx.listOfExpression() != null || ctx.name() != null)) {
          m_type = ((TypeReference)m_type).getBaseType(); 
          currentType = m_type;
        } 
        if (m_type instanceof TypeProcedure) {
          if (ctx.listOfExpression() != null) {
            m_isFunctionCall = true;
            // array indices given -> m_type is baseType() and iterate on next levels
            // see next if with ctx.name() != null
            visit(ctx.listOfExpression());
            m_type = ((TypeProcedure) m_type).getResultType();
            reVisitName(ctx);
          } else {
            //if (!m_beOnLhs) {
            //  m_type = ((TypeProcedure) m_type).getResultType();
            //}
          }
        } else 
        if (m_type instanceof TypeArray) {
            // resolve the index list if given for the array
            if (ctx.listOfExpression() != null) {
                // array indices given -> m_type is baseType() and iterate on next levels
                // see next if with ctx.name() != null
                visit(ctx.listOfExpression());
                m_type = ((TypeArray) currentType).getBaseType();
                // maybe we have also an name
                reVisitName(ctx.name());
            } else {
                // no array indices given --> no name may by given
                if (ctx.name() != null) {
                    ErrorStack.add("need array element for next struct component");
                    return null; // abort type resolving
                }
            }
        } else
        if (m_type instanceof TypeArraySpecification) {
          // resolve the index list if given for the array
          if (ctx.listOfExpression() != null) {
              // array indices given -> m_type is baseType() and iterate on next levels
              // see next if with ctx.name() != null
              visit(ctx.listOfExpression());
              m_type = ((TypeArraySpecification) currentType).getBaseType();
              // maybe we have also an name
              reVisitName(ctx.name());
          } else {
              // no array indices given --> no name may by given
              if (ctx.name() != null) {
                  ErrorStack.add("need array element for next struct component");
                  return null; // abort type resolving
              }
          }
        } else
        if (m_type instanceof TypeStructure) {
          if (ctx.name()!= null) {

            String s = ctx.name().ID().getText();
            //StructureComponent component = ((TypeStructure) m_type).lookup(ctx.name().getText());
            StructureComponent component = ((TypeStructure) m_type).lookup(s);
            
            if (component == null) {
                ErrorStack.add("unknown struct component");
                return null; // abort type resolving
            }

            m_type = component.m_type;
          
            reVisitName(ctx.name());
          }
        }

        return null;
    } 
//  /**
//     * Check if rule is using array or structure subscription
//     *
//     * @param ctx  Context of a name rule
//     * @param entry SymboltableEntry of the variable
//     * @return null
//     */
//    private void checkForArrayOrStructureUsage(SmallPearlParser.NameContext ctx, SymbolTableEntry entry) {
//        if (entry instanceof TaskEntry ||
//                entry instanceof SemaphoreEntry ||
//                entry instanceof BoltEntry ||
//                entry instanceof ModuleEntry ||
//                entry instanceof TypeEntry) {
//            if ( ctx.listOfExpression() != null) {
//                ErrorStack.add("cannot be  used as an array");
//            }
//
//            if ( ctx.name() != null ) {
//                ErrorStack.add("cannot be  used as a structure");
//            }
//        }
//    }

//    /**
//     * Get the type of name
//     *
//     * @param entry SymboltableEntry of the variable
//     * @return TypeDefinition of the name
//     */
//    private TypeDefinition getTypeDefintion(SymbolTableEntry entry) {
//        if ( entry != null ) {
//            if (entry instanceof VariableEntry) {
//                VariableEntry var = (VariableEntry)entry;
//
//                if (var.getType() instanceof TypeStructure ) {
//                }
//                else if (var.getType() instanceof TypeArray ) {
//                    TypeArray typeArray = (TypeArray)var.getType();
//                }
//            }
//        }
//
//        return null;
//    }
    
    @Override
    public Void visitArraySlice(SmallPearlParser.ArraySliceContext ctx) {
        Log.debug("ExpressionTypeVisitor:visitArraySlice:ctx" + CommonUtils.printContext(ctx));
 
        visitChildren(ctx);

        ErrorStack.enter(ctx,"array slice");

        TypeArraySlice t = new TypeArraySlice();
        ASTAttribute nameAttr = m_ast.lookup(ctx.name());
        if (nameAttr.getType() instanceof TypeArray) {
          t.setBaseType(nameAttr.getType());
        } else {
          ErrorStack.add("must be applied to an array");
        }

        int lastElementInList = ctx.startIndex().listOfExpression().expression().size()-1;
        ASTAttribute startIndex = m_ast.lookup(ctx.startIndex().listOfExpression().expression(lastElementInList));
        ASTAttribute endIndex = m_ast.lookup(ctx.endIndex().expression());
        if (startIndex.getConstant() != null && endIndex.getConstant()!=null) {
          t.setStartIndex(startIndex.getConstantFixedValue());
          t.setEndIndex(endIndex.getConstantFixedValue());
          if (t.getTotalNoOfElements()<1) {
            ErrorStack.add("must select at lease 1 element");
          }
        }
        ErrorStack.leave();
        m_ast.put(ctx, new ASTAttribute(t));
        
        return null;
    }
    
    /**
     * return the ast-attribute of the expression context
     * 
     * if no attribute is found, an error message is emitted and null returned
     * 
     * @param expression the desired context 
     * @param ctx         the current context of the expression
     * @param prefix     the error prefix (may be null)
     * @param message    the error message
     * @return           the AST attribute or null
     */
    private ASTAttribute saveGetAttribute(ExpressionContext expression,
        ParserRuleContext ctx, String prefix, String message) {
      ASTAttribute op = m_ast.lookup(expression);
      if (op == null) {
        ErrorStack.addInternal(ctx, prefix, message );
      }
      return op;
    }
}

