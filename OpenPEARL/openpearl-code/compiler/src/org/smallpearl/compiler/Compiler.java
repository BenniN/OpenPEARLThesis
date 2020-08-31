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

package org.smallpearl.compiler;

import org.antlr.v4.runtime.*;
import org.stringtemplate.v4.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

import static org.smallpearl.compiler.Log.LEVEL_INFO;
import static org.smallpearl.compiler.Log.LEVEL_NONE;
import static org.smallpearl.compiler.Log.LEVEL_WARN;
import static org.smallpearl.compiler.Log.LEVEL_DEBUG;
import static org.smallpearl.compiler.Log.LEVEL_ERROR;

public class Compiler {
    static String version = "v0.8.9.40";
    static String grammarName;
    static String startRuleName;
    static List<String> inputFiles = new ArrayList<String>();
    static String m_sourceFilename = "";
    static boolean printAST = false;
    static String psFile = null;
    static String outputFilename = null;
    static boolean showTokens = false;
    static boolean trace = true;
    static boolean diagnostics = false;
    static String encoding = null;
    static boolean SLL = false;
    static boolean nosemantic = false;
    static boolean constantfolding = true;
    static int verbose = 0;
    static String groupFile = "SmallPearlCpp.stg";
    static boolean lineSeparatorHasToBeModified = true;
    static boolean dumpDFA = false;
    static boolean dumpSymbolTable = false;
    static boolean dumpConstantPool = false;
    static boolean debug = false;
    static boolean debugSTG = false;
    static boolean stacktrace = false;
    static boolean imc = true;
    static boolean printSysInfo = false;
    static int     noOfErrors = 0;
    static int     noOfWarnings = 0;
    static int     warninglevel = 255;
    static int     lineWidth = 80;
    static boolean coloured = false;

    public static void main(String[] args) {
        int i, j;
        if (args.length < 1) {
            printHelp();
            return;
        }

        long startTime = System.nanoTime();

        Runtime runtime = Runtime.getRuntime();

        ConstantPool constantPool = new ConstantPool();
        SymbolTableVisitor symbolTableVisitor = new SymbolTableVisitor(verbose, constantPool);

        if (!checkAndProcessArguments(args)) {
            return;
        }

        ErrorStack.useColors(coloured);

        // Setup logger
        Log.Logger logger = new Log.Logger();
        Log.setLogger(logger);
        //Log.set(LEVEL_INFO);

        for (i = 0; i < inputFiles.size(); i++) {
            SmallPearlLexer lexer = null;
            AST ast = new AST();

            m_sourceFilename = inputFiles.get(i);

            logger.setLogFilename(getBaseName(m_sourceFilename) + ".log");
            Log.info("OpenPEARL compiler version " + version);
            Log.info("Start compiling of:" + m_sourceFilename);
            Log.debug("Performing syntax check");

            try {
                lexer = new SmallPearlLexer(new ANTLRFileStream(m_sourceFilename));
            }
            catch(IOException ex) {
                System.out.println("Error:" + ex.getMessage());
                System.exit(-2);
            }

            lexer.removeErrorListeners();
            lexer.addErrorListener(DescriptiveErrorListener.INSTANCE);

            CommonTokenStream tokens = new CommonTokenStream(lexer);

            SmallPearlParser parser = new SmallPearlParser(tokens);

            parser.removeErrorListeners();
            parser.addErrorListener(DescriptiveErrorListener.INSTANCE);

            parser.setBuildParseTree(true);

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // Start Analysis
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            ParserRuleContext tree = parser.program();

            if (printAST) {
                System.out.println("Parse tree:");
                System.out.println(tree.toStringTree(parser));
            }

            if (dumpDFA) {
                parser.dumpDFA();
            }

            try {
                if (parser.getNumberOfSyntaxErrors() <= 0) {
                    symbolTableVisitor.visit(tree);

                    if (dumpSymbolTable) {
                        symbolTableVisitor.symbolTable.dump();
                    }
                }

                ExpressionTypeVisitor expressionTypeVisitor = new ExpressionTypeVisitor(verbose, debug, symbolTableVisitor, constantPool, ast);
                if (ErrorStack.getTotalErrorCount()<=0) {
                       expressionTypeVisitor.visit(tree);
                }

                if (ErrorStack.getTotalErrorCount()<=0) {
                    ConstantFoldingVisitor constantFoldingVisitor = new ConstantFoldingVisitor(symbolTableVisitor, ast);
                    constantFoldingVisitor.visit(tree);
                }
                    
                ConstantPoolVisitor constantPoolVisitor = new ConstantPoolVisitor(lexer.getSourceName(),
                        verbose,
                        debug,
                        symbolTableVisitor,
                        constantPool,
                        expressionTypeVisitor,
                        ast);

                if (ErrorStack.getTotalErrorCount()<=0) {
                    constantPoolVisitor.visit(tree);
                }

                ConstantExpressionEvaluatorVisitor constantExpressionVisitor = new ConstantExpressionEvaluatorVisitor(verbose, debug, symbolTableVisitor, constantPoolVisitor);
                if (ErrorStack.getTotalErrorCount()<=0) {
                    	constantExpressionVisitor.visit(tree);
                }

                if (ErrorStack.getTotalErrorCount()<=0) {
                    FixUpSymbolTableVisitor fixUpSymbolTableVisitor = new FixUpSymbolTableVisitor(verbose,debug,symbolTableVisitor,expressionTypeVisitor,constantPoolVisitor,ast);
                    fixUpSymbolTableVisitor.visit(tree);
                }
                    
                // expressionTypeVisitor.visit(tree);

                if (dumpConstantPool) {
                        ConstantPool.dump();
                }

                if (ErrorStack.getTotalErrorCount() <= 0 && !nosemantic) {
                        SemanticCheck semanticCheck = new SemanticCheck(lexer.getSourceName(), verbose, debug, tree, symbolTableVisitor, expressionTypeVisitor, ast);
                }
                if (ErrorStack.getTotalErrorCount() <= 0 && imc) {
                    	SystemPartExport(lexer.getSourceName(), tree);
                }
                if (ErrorStack.getTotalErrorCount() <= 0) {
                       CppGenerate(lexer.getSourceName(), tree, symbolTableVisitor, expressionTypeVisitor, constantExpressionVisitor, ast);
                }
            }
            catch(Exception ex) {
                System.err.println(ex.getMessage());
                ex.printStackTrace();
                if (debug) {
                  ex.printStackTrace();
                }
                
                if ( verbose > 0 ) {
                    System.err.println("Compilation aborted.");
                }

                if (dumpSymbolTable) {
                    symbolTableVisitor.symbolTable.dump();
                }

                if (dumpConstantPool) {
                    ConstantPool.dump();
                }

                if ( stacktrace )  {
                    System.err.println( getStackTrace(ex));
                }

                System.exit(-1);
            }

            if (ErrorStack.getTotalErrorCount()>0) {
            	System.out.println("compilation aborted with errors");
                
                if ( verbose > 0 ) {
                    System.err.println("Compilation aborted.");
                }

                if (dumpSymbolTable) {
                    symbolTableVisitor.symbolTable.dump();
                }

                if (dumpConstantPool) {
                    ConstantPool.dump();
                }
            	
                System.exit(-1);
            	
            }
            
            noOfErrors = parser.getNumberOfSyntaxErrors();

            System.out.flush();
            System.out.println();
            System.out.println("Number of errors in " + inputFiles.get(i) + " encountered: " + noOfErrors);

            if ( printSysInfo) {
                String lines;
                long difference = System.nanoTime() - startTime;

                lines = "System Information:\n";
                lines += "Total execution time: " +
                        String.format("%d.%d sec",
                                TimeUnit.NANOSECONDS.toSeconds(difference),
                                TimeUnit.NANOSECONDS.toMillis(difference) - TimeUnit.NANOSECONDS.toSeconds(difference) * 1000);

                SystemInformation sysinfo = new SystemInformation();
                lines += "\n" + sysinfo.Info();
                Log.info(lines);
                System.out.println(lines);
            }

            if ( noOfErrors == 0 ) {
                System.exit(0);
            } else {
                System.exit(1);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void printHelp() {
        System.err.println("java org.smallpearl.compiler                             \n" +
                " Options:                                                           \n" +
                "  --help                      Print this help message               \n" +
                "  --version                   Print version information             \n" +
                "  --verbose                   Print more information                \n" +
                "  --quiet                     Be quiet                              \n" +
                "  --trace                                                           \n" +
                "  --nosemantic                Disable semantic checker              \n" +
                "  --noconstantfolding         Disable constant folding              \n" +
                "  --printAST                  Print Abtract Syntax Tree             \n" +
                "  --dumpDFA                   Print DFA                             \n" +
                "  --dumpSymbolTable           Print the SymbolTable                 \n" +
                "  --dumpConstantPool          Print the constant pool               \n" +
                "  --debug                     Generate debug information            \n" +
                "  --stacktrace                Print stacktrace in case of an        \n" +
                "                              exception                             \n" +
                "  --warninglevel <m_level>      Set the warning m_level             \n" +
                "                              Level   0: no warning                 \n" +
                "                              Level 255: all warnings (default)     \n" +
                " --imc                        Enable Inter Module Checker           \n" +
                "                              file                                  \n" +
                "  --sysinfo                   Print system information              \n" +
                "  --coloured                  mark errors with colour               \n" +
                "  --output <filename>         Filename of the generated code        \n" +
                "  infile ...                                                        \n");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean checkAndProcessArguments(String[] args) {
        int i = 0;

        while (i < args.length) {
            String arg = args[i];
            i++;
            if (arg.charAt(0) != '-') { // input file name
                inputFiles.add(arg);
                continue;
            }

            if (arg.equals("--help")) {
                printHelp();
                System.exit(0);
            } else if (arg.equals("--printAST")) {
                printAST = true;
            } else if (arg.equals("--tokens")) {
                showTokens = true;
            } else if (arg.equals("--trace")) {
                trace = true;
            } else if (arg.equals("--SLL")) {
                SLL = true;
            } else if (arg.equals("--sysinfo")) {
                printSysInfo = true;
            } else if (arg.equals("--nosemantic")) {
                nosemantic = true;
            } else if (arg.equals("--noconstantfolding")) {
                constantfolding = false;
            } else if (arg.equals("--diagnostics")) {
                diagnostics = true;
            } else if (arg.equals("--dumpDFA")) {
                dumpDFA = true;
            } else if (arg.equals("--dumpSymbolTable")) {
                dumpSymbolTable = true;
            } else if (arg.equals("--dumpConstantPool")) {
                dumpConstantPool = true;
            } else if (arg.equals("--debug")) {
                debug = true;
                Log.set(LEVEL_DEBUG);
            } else if (arg.equals("--debugSTG")) {
                debugSTG = true;
            } else if (arg.equals("--stacktrace")) {
                stacktrace = true;
            } else if (arg.equals("--imc")) {
                imc = true;
            } else if (arg.equals("--coloured")) {
            	coloured = true;
            } else if (arg.equals("--output")) {
                if (i >= args.length) {
                    System.err.println("missing filename on --output");
                    return false;
                }
                outputFilename = args[i];
                i++;
                continue;
            } else if (arg.equals("-encoding")) {
                if (i >= args.length) {
                    System.err.println("missing encoding on -encoding");
                    return false;
                }
                encoding = args[i];
                i++;
            } else if (arg.equals("--ps")) {
                if (i >= args.length) {
                    System.err.println("missing filename on --ps");
                    return false;
                }
                psFile = args[i];
                i++;
            } else if (arg.equals("--version")) {
                    System.out.println("OpenPEARL compiler version "+version);
                i++;
            } else if (arg.equals("--warninglevel")) {
                if (i >= args.length) {
                    System.err.println("missing warning m_level on --warninglevel");
                    return false;
                }
                warninglevel = Integer.parseInt(args[i]);
                i++;
            } else {
                System.out.println("Unknown command line argument:" + arg);
                return false;
            }
        }

        return true;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static Void CppGenerate(String sourceFileName,
                                    ParserRuleContext tree,
                                    SymbolTableVisitor symbolTableVisitor,
                                    ExpressionTypeVisitor expressionTypeVisitor,
                                    ConstantExpressionEvaluatorVisitor constantExpressionEvaluatorVisitor,
                                    AST ast) {

        CppCodeGeneratorVisitor cppCodeGenerator = new CppCodeGeneratorVisitor( sourceFileName,
                                                                                groupFile,
                                                                                verbose,
                                                                                debug,
                                                                                symbolTableVisitor,
                                                                                expressionTypeVisitor,
                                                                                constantExpressionEvaluatorVisitor,
                                                                                ast);

        ST code = cppCodeGenerator.visit(tree);

        if ( debugSTG ) {
            System.out.println( "Press a key to continue" );
            code.inspect();
            try {
                int ch = System.in.read();
            }
            catch (IOException ex) {
            }
        }

        if (outputFilename != null) {
            try {
                if ( outputFilename.lastIndexOf(".") == - 1 ) {
                    outputFilename += ".cc";
                }

                if (verbose>0) {
                    System.out.println("Generating output file "+outputFilename);
                }

                PrintWriter writer = new PrintWriter(outputFilename, "UTF-8");
                writer.println(code.render(lineWidth));
                writer.close();
            } catch (IOException e) {
                System.err.println("Problem writing to the file " + outputFilename);
            }
        } else {
            if(verbose>0) {
                System.out.println("Generated output:");
            }

            System.out.println(code.render(lineWidth));
        }

        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static Void SystemPartExport(String sourceFileName, ParserRuleContext tree) {
        String outputFileName = sourceFileName;

        SystemPartExporter  systemPartExporter = new SystemPartExporter(sourceFileName,verbose, debug);
        ST systemPart = systemPartExporter.visit(tree);

        if ( debugSTG ) {
            System.out.println( "Press a key to continue" );
            systemPart.inspect();
            try {
                int ch = System.in.read();
            }
            catch (IOException ex) {
            }
        }

        outputFileName = getBaseName(outputFileName).concat(".xml");

        try {

            if (verbose>0) {
                System.out.println("Generating IMC file "+outputFileName);
            }

            PrintWriter writer = new PrintWriter(outputFileName, "UTF-8");
            writer.println(systemPart.render(lineWidth));
            writer.close();
        } catch (IOException e) {
            System.err.println("Problem writing the IMC file " + outputFileName);
        }

        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        t.printStackTrace(pw);
        pw.flush();
        sw.flush();
        return sw.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static String  getBaseName(String filename) {
        String basename = "";

        if (filename != null) {
            basename = filename.substring(0, filename.lastIndexOf('.'));
        }

        return basename;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static String getSourceFilename() {
        return m_sourceFilename;
    }

}
