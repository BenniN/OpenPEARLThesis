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
import org.smallpearl.compiler.Exception.DuplicateAltValueException;
import org.smallpearl.compiler.Exception.InternalCompilerErrorException;
import org.smallpearl.compiler.SymbolTable.ModuleEntry;
import org.smallpearl.compiler.SymbolTable.SymbolTable;

import java.util.ArrayList;

public class CheckSwitchCase extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

    private int m_verbose;
    private boolean m_debug;
    private String m_sourceFileName;
    private ExpressionTypeVisitor m_expressionTypeVisitor;
    private SymbolTableVisitor m_symbolTableVisitor;
    private SymbolTable m_symboltable;
    private SymbolTable m_currentSymbolTable;
    private ModuleEntry m_module;
    private ArrayList<FixedRange> m_listOfAlternatives = null;
    private AST m_ast = null;

    public CheckSwitchCase(String sourceFileName,
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

        m_listOfAlternatives = new ArrayList<FixedRange>();

        Log.debug( "    Check Case");
    }

    @Override
    public Void visitModule(SmallPearlParser.ModuleContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check Case: visitModule");
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
            System.out.println( "Semantic: Check Case: visitProcedureDeclaration");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check Case: visitTaskDeclaration");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check Case: visitBlock_statement");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
        if (m_debug) {
            System.out.println( "Semantic: Check Case: visitLoopStatement");
        }

        this.m_currentSymbolTable = m_symbolTableVisitor.getSymbolTablePerContext(ctx);
        visitChildren(ctx);
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitCase_statement(SmallPearlParser.Case_statementContext ctx) {
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitCase_statement_selection2(SmallPearlParser.Case_statement_selection2Context ctx) {
        visitChildren(ctx);
        return null;
    }


    @Override
    public Void visitCase_statement_selection2_alt(SmallPearlParser.Case_statement_selection2_altContext ctx) {
        long lowerBoundary;
        long upperBoundary;
        ConstantFixedExpressionEvaluator evaluator = new ConstantFixedExpressionEvaluator(m_verbose, m_debug, m_currentSymbolTable,null, null);

        for ( int i = 0; i < ctx.case_list().index_section().size(); i++) {
            SmallPearlParser.Index_sectionContext index = ctx.case_list().index_section(i);

            if ( index.constantFixedExpression().size() == 1) {
                ConstantValue alt = evaluator.visit(index.constantFixedExpression(0));

                if ( !(alt instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                }

                lowerBoundary = ((ConstantFixedValue) alt).getValue();
                upperBoundary = lowerBoundary;

                for ( int j = 0; j < m_listOfAlternatives.size(); j++) {
                    if ( m_listOfAlternatives.get(j).isContained(lowerBoundary)) {
                        throw new DuplicateAltValueException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                    }
                }

                m_listOfAlternatives.add(new FixedRange(lowerBoundary,upperBoundary));
            }
            else {
                ConstantValue lower = evaluator.visit(index.constantFixedExpression(0));
                ConstantValue upper = evaluator.visit(index.constantFixedExpression(1));

                if ( !(lower instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                }

                if ( !(upper instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                }

                lowerBoundary = ((ConstantFixedValue) lower).getValue();
                upperBoundary = ((ConstantFixedValue) upper).getValue();

                for ( int j = 0; j < m_listOfAlternatives.size(); j++) {
                    if ( m_listOfAlternatives.get(j).isContained(lowerBoundary,upperBoundary)) {
                        throw new DuplicateAltValueException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                    }
                }

                m_listOfAlternatives.add(new FixedRange(lowerBoundary,upperBoundary));
            }
        }

        return null;
    }
}