/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2016 Marcel Schaible
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

package org.smallpearl.compiler;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.smallpearl.compiler.Exception.InternalCompilerErrorException;
import org.smallpearl.compiler.Exception.NumberOutOfRangeException;
import org.smallpearl.compiler.Exception.UnknownIdentifierException;
import org.smallpearl.compiler.Exception.ValueOutOfBoundsException;
import org.smallpearl.compiler.SymbolTable.ModuleEntry;
import org.smallpearl.compiler.SymbolTable.SymbolTable;
import org.smallpearl.compiler.SymbolTable.SymbolTableEntry;
import org.smallpearl.compiler.SymbolTable.VariableEntry;

public class ConstantPoolVisitor extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {
    private int m_verbose;
    private boolean m_debug;
    private String m_sourceFileName;
    private SymbolTableVisitor m_symbolTableVisitor;
    private SymbolTable m_symboltable;
    private SymbolTable m_currentSymbolTable;
    private ModuleEntry m_module;
    private int m_counter = 0;
    private ConstantPool m_constantPool = null;
    private ParseTreeProperty<TypeDefinition> m_properties = null;
    private Integer m_currFixedLength = null;
    private ExpressionTypeVisitor m_expressionTypeVisitor = null;
    private AST m_ast = null;

    public ConstantPoolVisitor(String sourceFileName,
                               int verbose,
                               boolean debug,
                               SymbolTableVisitor symbolTableVisitor,
                               ConstantPool constantPool,
                               ExpressionTypeVisitor expressionTypeVisitor,
                               AST ast) {


        m_debug = debug;
        m_verbose = verbose;
        m_sourceFileName = sourceFileName;
        m_symbolTableVisitor = symbolTableVisitor;
        m_symboltable = symbolTableVisitor.symbolTable;
        m_ast = ast;
        m_currentSymbolTable = m_symboltable;
        m_constantPool = constantPool;
        m_properties = new ParseTreeProperty<TypeDefinition>();
        m_expressionTypeVisitor = expressionTypeVisitor;

        if (m_verbose > 0) {
            System.out.println("Start ConstantPoolVisitor");
        }

        // Add commonly used constants:
        m_constantPool.add(new ConstantFixedValue(0, Defaults.FIXED_LENGTH));
        m_constantPool.add(new ConstantFixedValue(1, Defaults.FIXED_LENGTH));
        m_constantPool.add(new ConstantFixedValue(-1, Defaults.FIXED_LENGTH));
    }

    public Void add(ConstantValue value) {
        int i;
        int constantBitNo = 0;

        boolean found = false;

        for (i = 0; i < ConstantPool.constantPool.size(); i++) {
            if (value instanceof ConstantFixedValue) {
                if (ConstantPool.constantPool.get(i) instanceof ConstantFixedValue) {
                    ConstantFixedValue a = (ConstantFixedValue) (value);
                    ConstantFixedValue b = (ConstantFixedValue) (ConstantPool.constantPool.get(i));

                    if (a.getValue() == b.getValue() && a.getPrecision() == b.getPrecision()) {
                        found = true;
                        break;
                    }
                }
            } else if (value instanceof ConstantFloatValue) {
                if (ConstantPool.constantPool.get(i) instanceof ConstantFloatValue) {
                    if (Double.compare(((ConstantFloatValue) (value)).getValue(), ((ConstantFloatValue) (ConstantPool.constantPool.get(i))).getValue()) == 0) {
                        found = true;
                        break;
                    }
                }
            } else if (value instanceof ConstantCharacterValue) {
                if (ConstantPool.constantPool.get(i) instanceof ConstantCharacterValue) {
                    String s1 = ((ConstantCharacterValue) (value)).getValue();
                    String s2 = ((ConstantCharacterValue) (ConstantPool.constantPool.get(i))).getValue();

                    if (s1.equals(s2)) {
                        found = true;
                        break;
                    }
                }
            } else if (value instanceof ConstantBitValue) {
                if (ConstantPool.constantPool.get(i) instanceof ConstantBitValue) {
                    constantBitNo = constantBitNo + 1;
                    String s1 = ((ConstantBitValue) (value)).getValue();
                    String s2 = ((ConstantBitValue) (ConstantPool.constantPool.get(i))).getValue();

                    if (s1.equals(s2)) {
                        found = true;
                        break;
                    }
                }
            } else if (value instanceof ConstantDurationValue) {
                if (ConstantPool.constantPool.get(i) instanceof ConstantDurationValue) {
                    ConstantDurationValue other = (ConstantDurationValue) value;
                    ConstantDurationValue constant = ((ConstantDurationValue) (ConstantPool.constantPool.get(i)));

                    if (constant.equals(other)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        if (!found) {
            if (value instanceof ConstantBitValue) {
                constantBitNo = constantBitNo + 1;
                ((ConstantBitValue) (value)).setNo(constantBitNo);
            }

            m_constantPool.add(value);
        }

        return null;

    }

    @Override
    public Void visitBaseExpression(SmallPearlParser.BaseExpressionContext ctx) {
        Log.debug("ConstantPoolVisitor:visitBaseExpression:ctx" + CommonUtils.printContext(ctx));

        if (ctx.primaryExpression() != null) {
            visitPrimaryExpression(ctx.primaryExpression());
        }

        return null;
    }

/*
    @Override
    public Void visitPrimaryExpression(SmallPearlParser.PrimaryExpressionContext ctx) {
        Log.debug("ConstantPoolVisitor:visitBaseExpression:visitPrimaryExpression:ctx" + CommonUtils.printContext(ctx));

        visitChildren(ctx);

        return null;
    }
*/
    
    @Override
    public Void visitLiteral(SmallPearlParser.LiteralContext ctx) {
        Log.debug("ConstantPoolVisitor: visitLiteral");

        ASTAttribute attr = m_ast.lookup(ctx);
        String ss = ctx.getText();
        
        // we should have for all literals an ASTAttribute from the ExpressionTypeVisitor
        if (attr == null) {
          ErrorStack.enter(ctx,"ConstantPoolVisitor::visitLiteral");
          ErrorStack.addInternal("no AST attribute found "+ ctx.getText());
          ErrorStack.leave();
        }

        if (ctx.durationConstant() != null) {
            m_constantPool.add(CommonUtils.getConstantDurationValue(ctx.durationConstant(),1));
        } else if (ctx.timeConstant() != null) {
            int hours = 0;
            int minutes = 0;
            double seconds = 0.0;
            ErrorStack.enter(ctx.timeConstant(),"CLOCK value");
            hours = Integer.valueOf(ctx.timeConstant().IntegerConstant(0).toString());
            hours %= 24;
            
            minutes = Integer.valueOf(ctx.timeConstant().IntegerConstant(1).toString());
            if (minutes <0 || minutes > 59) {
              ErrorStack.add("minutes must be in range 0..59");
            }
            
            if (ctx.timeConstant().IntegerConstant(2) != null) {
                seconds = Double.valueOf(ctx.timeConstant().IntegerConstant(2).toString());
            } else if (ctx.timeConstant().floatingPointConstant() != null) {
                seconds = CommonUtils.getFloatingPointConstantValue(ctx.timeConstant().floatingPointConstant());
            }
            if (seconds < 0.0 || seconds >= 60.0) {
              ErrorStack.add("seconds must be in range 0..59");
            }
            ErrorStack.leave();
            m_constantPool.add(new ConstantClockValue(hours, minutes, seconds));
        } else if (ctx.BitStringLiteral() != null) {
            long value = CommonUtils.convertBitStringToLong(ctx.BitStringLiteral().getText());
            int nb = CommonUtils.getBitStringLength(ctx.BitStringLiteral().getText());
            ConstantBitValue cbv = new ConstantBitValue(value, nb);
            m_constantPool.add(cbv);
            attr.setConstant(cbv);
        } else if (ctx.floatingPointConstant() != null) {
            try {
                double value = 0.0;
                int precision = CommonUtils.getFloatingPointConstantPrecision(ctx.floatingPointConstant(), m_currentSymbolTable.lookupDefaultFloatLength());

                value = CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());

                ConstantFloatValue cfv = new ConstantFloatValue(value, precision);
                m_constantPool.add(cfv);
                attr.setConstant(cfv);
            } catch (NumberFormatException ex) {
                throw new NumberOutOfRangeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

        } else if (ctx.StringLiteral() != null) {
          if (attr.m_constant != null) {
            m_constantPool.add(attr.m_constant);
          } else {
            String s = ctx.StringLiteral().toString();
            //s = CommonUtils.removeQuotes(s);
           // s = CommonUtils.compressPearlString(s);
           // s = CommonUtils.unescapePearlString(CommonUtils.removeQuotes(s));
            m_constantPool.add(new ConstantCharacterValue(s));
            System.out.println("ConstPoolVisitor: should never be called: "+s);
          }
        } else if (ctx.fixedConstant() != null) {
            try {
                long value;
                int precision;
                
                if (m_currFixedLength != null) {
                    precision = m_currFixedLength;
                } 
                
                value = Long.parseLong(ctx.fixedConstant().IntegerConstant().toString());
                int precisionOfLiteral = Long.toBinaryString(Math.abs(value)).length();
                if ( value <  0) {
                    precisionOfLiteral++;
                }
                
                // calculate the precision
                // the language report states:
                //  the precision is ether explicitly given  
                //  or the precision is derived from the length statement
                
                // the constant fixed expression evaluator is not affected by this
                // treatment. ConstantFixedExpressions are calculated only upon the
                // current values
                
                precision = precisionOfLiteral; // = m_currentSymbolTable.lookupDefaultFixedLength();

                if (ctx.fixedConstant().fixedNumberPrecision() != null) {
                    precision = Integer.parseInt(ctx.fixedConstant().fixedNumberPrecision().IntegerConstant().toString());
                }
                
                if (precisionOfLiteral > precision) {
                	ErrorStack.enter(ctx,"constant");
                	ErrorStack.add("value too large for precision "+ precision);
                	ErrorStack.leave();
                }

                ConstantFixedValue cfv = new ConstantFixedValue(value, precision);
                m_constantPool.add(cfv);
                attr.setConstant(cfv);
            } catch (NumberFormatException ex) {
                throw new NumberOutOfRangeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        return null;
    }

    @Override
    public Void visitUnarySubtractiveExpression(SmallPearlParser.UnarySubtractiveExpressionContext ctx) {
        if (m_debug)
            System.out.println("ConstantPoolVisitor: visitUnarySubtractiveExpression");

        if (ctx.getChild(1) instanceof SmallPearlParser.BaseExpressionContext) {
            SmallPearlParser.BaseExpressionContext base_ctx = (SmallPearlParser.BaseExpressionContext) (ctx.getChild(1));

            if (base_ctx.primaryExpression() != null) {
                SmallPearlParser.PrimaryExpressionContext primary_ctx = base_ctx.primaryExpression();

                if (primary_ctx.getChild(0) instanceof SmallPearlParser.LiteralContext) {
                    SmallPearlParser.LiteralContext literal_ctx = (SmallPearlParser.LiteralContext) (primary_ctx.getChild(0));

                    if (literal_ctx.floatingPointConstant() != null) {
                        try {
                            double value = -1 * CommonUtils.getFloatingPointConstantValue(literal_ctx.floatingPointConstant());
                            int precision = CommonUtils.getFloatingPointConstantPrecision(literal_ctx.floatingPointConstant(),m_currentSymbolTable.lookupDefaultFloatLength());
                            add(new ConstantFloatValue(value, precision));
                        } catch (NumberFormatException ex) {
                            throw new NumberOutOfRangeException(ctx.getText(), literal_ctx.start.getLine(), literal_ctx.start.getCharPositionInLine());
                        }
                    } else if (literal_ctx.fixedConstant() != null) {
                        try {
                            Integer value = null;
                            int precision = m_currentSymbolTable.lookupDefaultFixedLength();

                            if (literal_ctx.fixedConstant().fixedNumberPrecision() != null) {
                                precision = Integer.parseInt(literal_ctx.fixedConstant().fixedNumberPrecision().IntegerConstant().toString());
                            }

                            value = -1 * Integer.parseInt(literal_ctx.fixedConstant().IntegerConstant().toString());

                            add(new ConstantFixedValue(value, precision));
                        } catch (NumberFormatException ex) {
                            throw new NumberOutOfRangeException(ctx.getText(), literal_ctx.start.getLine(), literal_ctx.start.getCharPositionInLine());
                        }
                    } else if (literal_ctx.StringLiteral() != null) {
                        System.out.println("string:(" + literal_ctx.StringLiteral().toString() + ")");
                    } else if (literal_ctx.floatingPointConstant() != null) {
                        try {
                            double value = -1 * CommonUtils.getFloatingPointConstantValue(literal_ctx.floatingPointConstant());
                            int precision = CommonUtils.getFloatingPointConstantPrecision(literal_ctx.floatingPointConstant(), m_currentSymbolTable.lookupDefaultFloatLength());
                            add(new ConstantFloatValue(value, precision));
                        } catch (NumberFormatException ex) {
                            throw new NumberOutOfRangeException(ctx.getText(), literal_ctx.start.getLine(), literal_ctx.start.getCharPositionInLine());
                        }
                    } else if (literal_ctx.timeConstant() != null) {
                        System.out.println("time:(" + ")");
                    } else if (literal_ctx.durationConstant() != null) {
                        add(CommonUtils.getConstantDurationValue(literal_ctx.durationConstant(),-1));
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Void visitAdditiveExpression(SmallPearlParser.AdditiveExpressionContext ctx) {
        Log.debug("ConstantPoolVisitor:visitAdditiveExpression:ctx" + CommonUtils.printContext(ctx));

        if( !addFixedConstant(ctx)) {
            visitChildren(ctx);
        }

        return null;
    }

    @Override
    public Void visitSubtractiveExpression(SmallPearlParser.SubtractiveExpressionContext ctx) {
        Log.debug("ConstantPoolVisitor:visitSubtractiveExpression:ctx" + CommonUtils.printContext(ctx));

        if( !addFixedConstant(ctx)) {
            visitChildren(ctx);
        }

        return null;
    }

    @Override
    public Void visitDivideExpression(SmallPearlParser.DivideExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitDivideIntegerExpression(SmallPearlParser.DivideIntegerExpressionContext ctx) {
        Log.debug("ConstantPoolVisitor:visitDivideIntegerExpression:ctx" + CommonUtils.printContext(ctx));

        if( !addFixedConstant(ctx)) {
            visitChildren(ctx);
        }

        return null;
    }

    @Override
    public Void visitConstant(SmallPearlParser.ConstantContext ctx) {
        Log.debug("ConstantPoolVisitor:visitConstant:ctx" + CommonUtils.printContext(ctx));

        int sign = 1;

        if (ctx.sign() != null) {
            if (ctx.sign() instanceof SmallPearlParser.SignMinusContext) {
                sign = -1;
            }
        }

        if (ctx.fixedConstant() != null) {
            try {
                long value;
                int precision = m_currentSymbolTable.lookupDefaultFixedLength();

                value = sign * Long.parseLong(ctx.fixedConstant().IntegerConstant().toString());

                if (ctx.fixedConstant().fixedNumberPrecision() != null) {
                    precision = Integer.parseInt(ctx.fixedConstant().fixedNumberPrecision().IntegerConstant().toString());
                } else {
                    // walk up the AST and get VariableDenotationContext:
                    ParserRuleContext sctx = ctx.getParent();
                    while (sctx != null && !((sctx instanceof SmallPearlParser.VariableDenotationContext) || (sctx instanceof SmallPearlParser.ArrayDenotationContext))) {
                        sctx = sctx.getParent();
                    }

                    if (sctx != null) {
                        if (sctx instanceof SmallPearlParser.VariableDenotationContext) {
                            SmallPearlParser.TypeAttributeContext typeAttributeContext = ((SmallPearlParser.VariableDenotationContext) sctx).typeAttribute();
                            if (typeAttributeContext.simpleType() != null) {
                                SmallPearlParser.SimpleTypeContext simpleTypeContext = typeAttributeContext.simpleType();

                                if (simpleTypeContext.typeInteger() != null) {
                                    SmallPearlParser.TypeIntegerContext typeIntegerContext = simpleTypeContext.typeInteger();

                                    if (typeIntegerContext.mprecision() != null) {
                                        precision = Integer.parseInt(typeIntegerContext.mprecision().integerWithoutPrecision().IntegerConstant().toString());
                                    }
                                }
                            }
                        } else if (sctx instanceof SmallPearlParser.ArrayDenotationContext) {
                        }
                    }
                }

                value = sign * Long.parseLong(ctx.fixedConstant().IntegerConstant().toString());
                m_constantPool.add(new ConstantFixedValue(value, precision));
            } catch (NumberFormatException ex) {
                throw new NumberOutOfRangeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

        } else if (ctx.stringConstant() != null) {
        	String s = ctx.stringConstant().StringLiteral().toString();
        	//s = CommonUtils.removeQuotes(s);
        	//s = CommonUtils.compressPearlString(s);
        	m_constantPool.add(new ConstantCharacterValue(s));
        } else if (ctx.durationConstant() != null) {
            Integer hours = 0;
            Integer minutes = 0;
            Double seconds = 0.0;

            if (ctx.durationConstant().hours() != null) {
                hours = Integer.valueOf(ctx.durationConstant().hours().IntegerConstant().toString());
            }
            if (ctx.durationConstant().minutes() != null) {
                minutes = Integer.valueOf(ctx.durationConstant().minutes().IntegerConstant().toString());
            }
            if (ctx.durationConstant().seconds() != null) {
                if (ctx.durationConstant().seconds().IntegerConstant() != null) {
                    seconds = Double.valueOf(ctx.durationConstant().seconds().IntegerConstant().toString());
                } else if (ctx.durationConstant().seconds().floatingPointConstant() != null) {
                    seconds = CommonUtils.getFloatingPointConstantValue(ctx.durationConstant().seconds().floatingPointConstant());
                }
            }

            ConstantDurationValue durationConst = new ConstantDurationValue(hours, minutes, seconds, sign);
            m_constantPool.add(durationConst);
        } else if (ctx.timeConstant() != null) {
            Integer hours = 0;
            Integer minutes = 0;
            Double seconds = 0.0;

            hours = Integer.valueOf(ctx.timeConstant().IntegerConstant(0).toString());
            minutes = Integer.valueOf(ctx.timeConstant().IntegerConstant(1).toString());

            if (ctx.timeConstant().floatingPointConstant() != null) {
                seconds = CommonUtils.getFloatingPointConstantValue(ctx.timeConstant().floatingPointConstant());
            } else if (ctx.timeConstant().IntegerConstant(2) != null) {
                seconds = Double.valueOf(ctx.timeConstant().IntegerConstant(2).toString());
            } else {
                throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

            ConstantClockValue clockConst = new ConstantClockValue(hours, minutes, seconds);
            m_constantPool.add(clockConst);
        } else if (ctx.floatingPointConstant() != null) {
            try {
                double value = 0.0;
                int precision = m_currentSymbolTable.lookupDefaultFloatLength();

/***
 // walk up the AST and get VariableDenotationContext:
 ParserRuleContext  sctx =  ctx.getParent();
 while ( sctx != null && !(sctx instanceof SmallPearlParser.VariableDenotationContext)) {
 sctx = sctx.getParent();
 }

 if (sctx != null) {
 SmallPearlParser.TypeAttributeContext typeAttributeContext = ((SmallPearlParser.VariableDenotationContext)sctx).typeAttribute();
 if ( typeAttributeContext.simpleType() != null ) {
 SmallPearlParser.SimpleTypeContext simpleTypeContext = typeAttributeContext.simpleType();

 if( simpleTypeContext.typeFloatingPointNumber() != null ) {
 SmallPearlParser.TypeFloatingPointNumberContext typeFloatingPointNumberContext = simpleTypeContext.typeFloatingPointNumber();

 if ( typeFloatingPointNumberContext.IntegerConstant() != null) {
 precision = Integer.valueOf(typeFloatingPointNumberContext.IntegerConstant().toString());
 }
 }
 }

 }
 else {
 throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
 }
 ***/
                value = sign * CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());
                m_constantPool.add(new ConstantFloatValue(value, precision));
            } catch (NumberFormatException ex) {
                throw new NumberOutOfRangeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        } else if (ctx.bitStringConstant() != null) {
            long value = CommonUtils.convertBitStringToLong(ctx.bitStringConstant().BitStringLiteral().getText());
            int nb = CommonUtils.getBitStringLength(ctx.bitStringConstant().BitStringLiteral().getText());
            ConstantBitValue cbv = new ConstantBitValue(value, nb);
            m_constantPool.add(cbv);
        }

        return null;
    }


    @Override
    public Void visitModule(SmallPearlParser.ModuleContext ctx) {
        Log.debug("ConstantPoolVisitor:visitModule:ctx" + CommonUtils.printContext(ctx));

        org.smallpearl.compiler.SymbolTable.SymbolTableEntry symbolTableEntry = m_currentSymbolTable.lookupLocal(ctx.ID().getText());
        m_currentSymbolTable = ((ModuleEntry) symbolTableEntry).scope;
        visitChildren(ctx);
        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
        Log.debug("ConstantPoolVisitor:visitProcedureDeclaration:ctx" + CommonUtils.printContext(ctx));
        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
        Log.debug("ConstantPoolVisitor:visitTaskDeclaration:ctx" + CommonUtils.printContext(ctx));
        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
        Log.debug("ConstantPoolVisitor:visitBlock_statement:ctx" + CommonUtils.printContext(ctx));
        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
        Log.debug("ConstantPoolVisitor:visitLoopStatement:ctx" + CommonUtils.printContext(ctx));
        int precision;

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);

        if (ctx.loopStatement_for() == null) {
            visitChildren(ctx);
        } else {
            SymbolTableEntry entry = m_currentSymbolTable.lookup(ctx.loopStatement_for().ID().getText());
            VariableEntry var = null;

            if (entry != null && entry instanceof VariableEntry) {
                var = (VariableEntry) entry;
            } else {
                throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
            TypeFixed fromType = null;
            TypeFixed toType = null;

            ASTAttribute fromRes = null;
            ASTAttribute toRes = null;
            if (ctx.loopStatement_from() != null) {
                visit(ctx.loopStatement_from().expression());
                fromRes = m_ast.lookup(ctx.loopStatement_from().expression());
                TypeDefinition typ = var.getType();
                if (typ instanceof TypeFixed) {
                    fromType = (TypeFixed) typ;
                } else {
                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                }
            }

            if (ctx.loopStatement_to() != null) {
                toRes = m_ast.lookup(ctx.loopStatement_to().expression());
                TypeDefinition typ = var.getType();

                visit(ctx.loopStatement_to().expression());

                if (typ instanceof TypeFixed) {
                    toType = (TypeFixed) typ; //TODO stimmt nicht!!!!
                } else {
                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                }
            }

            precision = m_currentSymbolTable.lookupDefaultFixedLength();

            if (fromType != null) {
                if (toType != null) {
                    precision = Math.max(((TypeFixed) fromRes.getType()).getPrecision(), ((TypeFixed) toRes.getType()).getPrecision());
                } else {
                    precision = ((TypeFixed) fromRes.getType()).getPrecision();
                }
            } else if (toType != null) {
                precision = ((TypeFixed) toRes.getType()).getPrecision();
            }
        }

        for (int i = 0; i < ctx.loopBody().statement().size(); i++) {
            visit(ctx.loopBody().statement(i));
        }

        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitAssignment_statement(SmallPearlParser.Assignment_statementContext ctx) {
        Log.debug("ConstantPoolVisitor:visitAssignment_statement:ctx" + CommonUtils.printContext(ctx));


        visitChildren(ctx);

        m_currFixedLength = null;
        return null;
    }
    


    @Override
    public Void visitMultiplicativeExpression(SmallPearlParser.MultiplicativeExpressionContext ctx) {
        Log.debug("ConstantPoolVisitor:visitMultiplicativeExpression:ctx" + CommonUtils.printContext(ctx));

        if( !addFixedConstant(ctx)) {
            visitChildren(ctx);
        }

        return null;
    }

    /*
    @Override
    public Void visitRemainderExpression(SmallPearlParser.RemainderExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitExponentiationExpression(SmallPearlParser.ExponentiationExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitFitExpression(SmallPearlParser.FitExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitSqrtExpression(SmallPearlParser.SqrtExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitSinExpression(SmallPearlParser.SinExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitCosExpression(SmallPearlParser.CosExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitExpExpression(SmallPearlParser.ExpExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitLnExpression(SmallPearlParser.LnExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitTanExpression(SmallPearlParser.TanExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitAtanExpression(SmallPearlParser.AtanExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitTanhExpression(SmallPearlParser.TanhExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }


    @Override
    public Void visitTOFIXEDExpression(SmallPearlParser.TOFIXEDExpressionContext ctx) {
        Log.debug("ConstantPoolVisitor:visitTOFIXEDExpression:ctx" + CommonUtils.printContext(ctx));

        visitChildren(ctx);

        return null;
    }

    @Override
    public Void visitTOFLOATExpression(SmallPearlParser.TOFLOATExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitTOBITExpression(SmallPearlParser.TOBITExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitTOCHARExpression(SmallPearlParser.TOCHARExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitEntierExpression(SmallPearlParser.EntierExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitRoundExpression(SmallPearlParser.RoundExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitUnaryExpression(SmallPearlParser.UnaryExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitSemaTry(SmallPearlParser.SemaTryContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitNowFunction(SmallPearlParser.NowFunctionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitSizeofExpression(SmallPearlParser.SizeofExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitEqRelationalExpression(SmallPearlParser.EqRelationalExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitLtRelationalExpression(SmallPearlParser.LtRelationalExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitNeRelationalExpression(SmallPearlParser.NeRelationalExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }


    @Override
    public Void visitLeRelationalExpression(SmallPearlParser.LeRelationalExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitGtRelationalExpression(SmallPearlParser.GtRelationalExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitGeRelationalExpression(SmallPearlParser.GeRelationalExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitStringSelection(SmallPearlParser.StringSelectionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitCshiftExpression(SmallPearlParser.CshiftExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitShiftExpression(SmallPearlParser.ShiftExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitCatExpression(SmallPearlParser.CatExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitAndExpression(SmallPearlParser.AndExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitOrExpression(SmallPearlParser.OrExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitExorExpression(SmallPearlParser.ExorExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitCONTExpression(SmallPearlParser.CONTExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitStringSlice(SmallPearlParser.StringSliceContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitConstantFixedExpressionFit(SmallPearlParser.ConstantFixedExpressionFitContext ctx) {
        visitChildren(ctx);
        return null;
    }
*/

    @Override
    public Void visitInitElement(SmallPearlParser.InitElementContext ctx) {
        // MS: No need to add constant in INIT to the constant pool, because
        // the SymbolTableVisitor takes care of them.
        // visitChildren(ctx);
        return null;
    }
/*
    @Override
    public Void visitConstantExpression(SmallPearlParser.ConstantExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitLwbDyadicExpression(SmallPearlParser.LwbDyadicExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitUpbDyadicExpression(SmallPearlParser.UpbDyadicExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitLwbMonadicExpression(SmallPearlParser.LwbMonadicExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitUpbMonadicExpression(SmallPearlParser.UpbMonadicExpressionContext ctx) {
        visitChildren(ctx);
        return null;
    }
*/

    private boolean addFixedConstant(ParserRuleContext ctx) {
        ASTAttribute attr = null;

        Log.debug("ConstantPoolVisitor:addFixedConstant:ctx" + CommonUtils.printContext(ctx));

        attr = m_ast.lookup(ctx);

        if ( attr != null && attr.isReadOnly() && attr.getType() instanceof TypeFixed ) {
            ConstantFixedValue value = attr.getConstantFixedValue();

            if ( value != null) {
                m_constantPool.add(value);
                Log.debug("ConstantPoolVisitor:addFixedConstant:added value=" + value);
                return true;
            }
        }

        return false;
    }
}
