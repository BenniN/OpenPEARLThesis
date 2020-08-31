/*
 * [The "BSD license"]
 *  Copyright (c) 2017 Marcel Schaible
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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.smallpearl.compiler.SymbolTable.*;

import java.util.LinkedList;

public class ConstantExpressionEvaluatorVisitor extends SmallPearlBaseVisitor<Void>
    implements SmallPearlVisitor<Void> {

  private int m_verbose;
  private boolean m_debug;
  private String m_sourceFileName;
  private SymbolTableVisitor m_symbolTableVisitor;
  private SymbolTable m_symboltable;
  private SymbolTable m_currentSymbolTable;
  private ModuleEntry m_module;
  private int m_value;
  private int m_accumulator;
  private ParseTreeProperty<ConstantValue> m_properties = null;
  private ConstantPoolVisitor m_constantPoolVisitor;
  private boolean m_enterResultinConstantPool = true;

  public ConstantExpressionEvaluatorVisitor(
      int verbose,
      boolean debug,
      SymbolTableVisitor symbolTableVisitor,
      ConstantPoolVisitor constantPoolVisitor) {

    m_debug = debug;
    m_verbose = verbose;
    m_symbolTableVisitor = symbolTableVisitor;
    m_symboltable = symbolTableVisitor.symbolTable;
    m_currentSymbolTable = m_symboltable;
    m_constantPoolVisitor = constantPoolVisitor;
    m_properties = new ParseTreeProperty<ConstantValue>();
    m_enterResultinConstantPool = true;
    m_debug = true;
  }

  public ConstantValue lookup(ParserRuleContext ctx) {
    return m_properties.get(ctx);
  }

  public boolean getEnterResultinConstantPool() {
    return m_enterResultinConstantPool;
  }

  public Void setEnterResultinConstantPool(boolean flag) {
    m_enterResultinConstantPool = flag;
    return null;
  }

  @Override
  public Void visitConstantExpression(SmallPearlParser.ConstantExpressionContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitConstantExpression:ctx"
            + CommonUtils.printContext(ctx));

    if (ctx.constantFixedExpression() != null) {
      ConstantFixedExpressionEvaluator evaluator =
          new ConstantFixedExpressionEvaluator(
              m_verbose, m_debug, m_currentSymbolTable, this, m_constantPoolVisitor);
      ConstantFixedValue result = evaluator.visit(ctx.constantFixedExpression());
      m_properties.put(ctx, result);

      if (m_enterResultinConstantPool) {
        m_constantPoolVisitor.add(result);
      }

      Log.debug("ConstantExpressionEvaluatorVisitor:visitConstantExpression:result=" + result);
    } else {
      visitChildren(ctx);
    }

    return null;
  }

  @Override
  public Void visitConstantFixedExpression(SmallPearlParser.ConstantFixedExpressionContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitConstantFixedExpression:ctx"
            + CommonUtils.printContext(ctx));
    ConstantFixedExpressionEvaluator evaluator =
        new ConstantFixedExpressionEvaluator(
            m_verbose, m_debug, m_currentSymbolTable, this, m_constantPoolVisitor);
    ConstantFixedValue result = evaluator.visit(ctx);
    m_properties.put(ctx, result);

    if (m_enterResultinConstantPool) {
      m_constantPoolVisitor.add(result);
    }

    Log.debug("ConstantExpressionEvaluatorVisitor:visitConstantFixedExpression:result=" + result);

    return null;
  }

  @Override
  public Void visitConstant(SmallPearlParser.ConstantContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitConstant:ctx" + CommonUtils.printContext(ctx));

    ConstantValue value = null;
    int sign = 1;
    int precision = 0;
    long curval = 0;

    if (ctx.sign() != null) {
      if (ctx.sign() instanceof SmallPearlParser.SignMinusContext) {
        sign = -1;
      }
    }

    if (ctx.fixedConstant() != null) {
      curval = sign * Long.parseLong(ctx.fixedConstant().IntegerConstant().toString());
      precision = m_currentSymbolTable.lookupDefaultFixedLength();

      if (ctx.fixedConstant().fixedNumberPrecision() != null) {
        precision =
            Integer.parseInt(
                ctx.fixedConstant().fixedNumberPrecision().IntegerConstant().toString());
      } else {
        // walk up the AST and get VariableDenotationContext:
        ParserRuleContext sctx = ctx.getParent();
        while (sctx != null
            && !((sctx instanceof SmallPearlParser.VariableDenotationContext)
                || (sctx instanceof SmallPearlParser.ArrayDenotationContext))) {
          sctx = sctx.getParent();
        }

        if (sctx != null) {
          if (sctx instanceof SmallPearlParser.VariableDenotationContext) {
            SmallPearlParser.TypeAttributeContext typeAttributeContext =
                ((SmallPearlParser.VariableDenotationContext) sctx).typeAttribute();
            if (typeAttributeContext.simpleType() != null) {
                  SmallPearlParser.SimpleTypeContext simpleTypeContext =
                  typeAttributeContext.simpleType();

              if (simpleTypeContext.typeInteger() != null) {
                SmallPearlParser.TypeIntegerContext typeIntegerContext =
                    simpleTypeContext.typeInteger();

                if (typeIntegerContext.mprecision() != null) {
                  precision =
                      Integer.parseInt(
                          typeIntegerContext
                              .mprecision()
                              .integerWithoutPrecision()
                              .IntegerConstant()
                              .toString());
                }
              }
            }
          } else if (sctx instanceof SmallPearlParser.ArrayDenotationContext) {
          }
        }
      }
    }
    value = new ConstantFixedValue(curval, precision);

    if (m_enterResultinConstantPool) {
      m_constantPoolVisitor.add(value);
    }

    return null;
  }

  @Override
  public Void visitModule(SmallPearlParser.ModuleContext ctx) {
    Log.debug("ConstantExpressionEvaluatorVisitor:visitModule:ctx" + CommonUtils.printContext(ctx));
    org.smallpearl.compiler.SymbolTable.SymbolTableEntry symbolTableEntry =
        m_currentSymbolTable.lookupLocal(ctx.ID().getText());
    m_currentSymbolTable = ((ModuleEntry) symbolTableEntry).scope;
    visitChildren(ctx);
    m_currentSymbolTable = m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitProcedureDeclaration:ctx"
            + CommonUtils.printContext(ctx));
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitTaskDeclaration:ctx"
            + CommonUtils.printContext(ctx));
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    m_currentSymbolTable = m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitBlock_statement:ctx"
            + CommonUtils.printContext(ctx));
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitLoopStatement:ctx"
            + CommonUtils.printContext(ctx));
    this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
    visitChildren(ctx);
    this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
    return null;
  }

  @Override
  public Void visitCase_list(SmallPearlParser.Case_listContext ctx) {
    Log.debug(
        "ConstantExpressionEvaluatorVisitor:visitCase_list:ctx" + CommonUtils.printContext(ctx));
    boolean old_enterResultinConstantPool = m_enterResultinConstantPool;
    m_enterResultinConstantPool = false;
    visitChildren(ctx);
    m_enterResultinConstantPool = old_enterResultinConstantPool;
    return null;
  }
}
