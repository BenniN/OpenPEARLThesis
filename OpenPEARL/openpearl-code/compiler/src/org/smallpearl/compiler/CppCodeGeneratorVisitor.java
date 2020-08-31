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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.smallpearl.compiler.Exception.*;
import org.smallpearl.compiler.SmallPearlParser.*;
import org.smallpearl.compiler.SymbolTable.*;
import org.smallpearl.compiler.Graph.Graph;
import org.smallpearl.compiler.Graph.Node;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.*;

public class CppCodeGeneratorVisitor extends SmallPearlBaseVisitor<ST>
        implements SmallPearlVisitor<ST> {

    private STGroup m_group;
    private int m_verbose;
    private boolean m_debug;
    private String m_sourceFileName;
    private ExpressionTypeVisitor m_expressionTypeVisitor;
    private ConstantExpressionEvaluatorVisitor m_constantExpressionEvaluatorVisitor;
    private SymbolTableVisitor m_symbolTableVisitor;
    private boolean m_map_to_const = true;
    private SymbolTable m_symboltable;
    private SymbolTable m_currentSymbolTable;
    private ModuleEntry m_module;
    private Integer m_currFixedLength = null;
    private int m_sign = 1;
    private AST m_ast = null;
    private boolean m_isNonStatic;   // flag to check if an format list contains non static elements
    private int m_tfuRecord;         // length of the TFU record, or -1
    private TypeDefinition m_resultType;     // result type of a PROC; required if a variable character string is returned
    private Vector<ST> m_tempVariableList;  // variable character values must be assigned
    private Vector<Integer> m_tempVariableNbr;  // to a temporary variable if use as proc parameter
    private LinkedList<LinkedList<String>> m_listOfSemaphoreArrays;
    private ST constantSemaphoreArrays;
    private LinkedList<LinkedList<String>> m_listOfBoltArrays;
    private ST constantBoltArrays;

    public enum Type {
        BIT, CHAR, FIXED
    }

    public static final double pi = java.lang.Math.PI;

    public CppCodeGeneratorVisitor(
            String sourceFileName,
            String filename,
            int verbose,
            boolean debug,
            SymbolTableVisitor symbolTableVisitor,
            ExpressionTypeVisitor expressionTypeVisitor,
            ConstantExpressionEvaluatorVisitor constantExpressionEvaluatorVisitor,
            AST ast) {

        m_debug = debug;
        m_verbose = verbose;
        m_sourceFileName = sourceFileName;
        m_symbolTableVisitor = symbolTableVisitor;
        m_expressionTypeVisitor = expressionTypeVisitor;
        m_constantExpressionEvaluatorVisitor = constantExpressionEvaluatorVisitor;
        m_symboltable = symbolTableVisitor.symbolTable;
        m_currentSymbolTable = m_symboltable;
        m_listOfSemaphoreArrays = new LinkedList<LinkedList<String>>();
        m_listOfBoltArrays = new LinkedList<LinkedList<String>>();
        m_ast = ast;
        m_tempVariableList = new Vector<ST>();
        m_tempVariableNbr = new Vector<Integer>();


        LinkedList<ModuleEntry> listOfModules = this.m_currentSymbolTable
                .getModules();

        if (listOfModules.size() > 1) {
            throw new NotYetImplementedException("Multiple modules", 0, 0);
        }

        m_module = listOfModules.get(0);

        if (m_verbose > 0) {
            System.out.println("Generating Cpp code");
        }

        this.ReadTemplate(filename);
        constantSemaphoreArrays =  m_group.getInstanceOf("SemaphoreArrays");
        constantBoltArrays =  m_group.getInstanceOf("BoltArrays");

        // generateProlog is invoked via visitModule!!
        generatePrologue();
    }

    private Void ReadTemplate(String filename) {
        if (m_verbose > 0) {
            System.out.println("Read StringTemplate Group File: " + filename);
        }

        this.m_group = new STGroupFile(filename);

        return null;
    }

    private ST generatePrologue() {
        ST prologue = m_group.getInstanceOf("Prologue");

        prologue.add("src", this.m_sourceFileName);

        ST taskspec = m_group.getInstanceOf("TaskSpecifier");

        LinkedList<TaskEntry> taskEntries = this.m_module.scope
                .getTaskDeclarations();
        ArrayList<String> listOfTaskNames = new ArrayList<String>();

        for (int i = 0; i < taskEntries.size(); i++) {
            listOfTaskNames.add(taskEntries.get(i).getName());
        }

        taskspec.add("taskname", listOfTaskNames);
        prologue.add("taskSpecifierList", taskspec);
        prologue.add("ConstantPoolList", generateConstantPool());

        if (m_module.scope.usesSystemElements()) {
            prologue.add("useSystemElements", true);
        }

        prologue.add("StructureForwardDeclarationList", generateStructureForwardDeclarationList());
        prologue.add("StructureDeclarationList", generateStructureDeclarationList());

        return prologue;
    }

    private ST generateConstantPool() {
        Log.debug("CppCodeGeneratorVisitor:generateConstantPool:");

        ST pool = m_group.getInstanceOf("ConstantPoolList");

        for (int i = 0; i < ConstantPool.constantPool.size(); i++) {
            if (ConstantPool.constantPool.get(i) instanceof ConstantFixedValue) {
                ST entry = m_group.getInstanceOf("ConstantPoolEntry");
                entry.add("name", ConstantPool.constantPool.get(i).toString());
                entry.add("type",
                        ((ConstantFixedValue) ConstantPool.constantPool.get(i))
                                .getBaseType());
                entry.add("precision",
                        ((ConstantFixedValue) ConstantPool.constantPool.get(i))
                                .getPrecision());
                entry.add("value",
                        ((ConstantFixedValue) ConstantPool.constantPool.get(i))
                                .getValue());
                pool.add("constants", entry);
            }
        }

        for (int i = 0; i < ConstantPool.constantPool.size(); i++) {
            if (ConstantPool.constantPool.get(i) instanceof ConstantFloatValue) {
                ST entry = m_group.getInstanceOf("ConstantPoolEntry");
                entry.add("name", ConstantPool.constantPool.get(i).toString());
                entry.add("type",
                        ((ConstantFloatValue) ConstantPool.constantPool.get(i))
                                .getBaseType());
                entry.add("precision",
                        ((ConstantFloatValue) ConstantPool.constantPool.get(i))
                                .getPrecision());
                entry.add("value",
                        ((ConstantFloatValue) ConstantPool.constantPool.get(i))
                                .getValue());
                pool.add("constants", entry);
            }
        }

        for (int i = 0; i < ConstantPool.constantPool.size(); i++) {
            if (ConstantPool.constantPool.get(i) instanceof ConstantCharacterValue) {
                ConstantCharacterValue value = (ConstantCharacterValue) ConstantPool.constantPool
                        .get(i);
                ST entry = m_group.getInstanceOf("ConstantPoolCharacterEntry");
                entry.add("name", value.toString());
                entry.add("type", value.getBaseType());
                entry.add("length", value.getLength());

                // we must quote the backslash and the double quote
                String s = value.getValue();
                s = CommonUtils.convertPearl2CString(s);
                entry.add("value", s);

                pool.add("constants", entry);
            }
        }

        for (int i = 0; i < ConstantPool.constantPool.size(); i++) {
            if (ConstantPool.constantPool.get(i) instanceof ConstantBitValue) {
                ConstantBitValue value = (ConstantBitValue) ConstantPool.constantPool
                        .get(i);
                ST entry = m_group.getInstanceOf("ConstantPoolBitEntry");
                entry.add("name", value.toString());
                entry.add("type", value.getBaseType());
                entry.add("length", value.getLength());
                entry.add("value", value.getValue());
                pool.add("constants", entry);
            }
        }

        for (int i = 0; i < ConstantPool.constantPool.size(); i++) {
            if (ConstantPool.constantPool.get(i) instanceof ConstantDurationValue) {
                ConstantDurationValue value = (ConstantDurationValue) ConstantPool.constantPool
                        .get(i);
                ST entry = m_group.getInstanceOf("ConstantPoolDurationEntry");
                entry.add("name", value.toString());
                entry.add("type", value.getBaseType());
                entry.add("valueSec", value.getValueSec());
                entry.add("valueUsec", value.getValueUsec());
                entry.add("valueSign", value.getSign());
                pool.add("constants", entry);
            }
        }

        for (int i = 0; i < ConstantPool.constantPool.size(); i++) {
            if (ConstantPool.constantPool.get(i) instanceof ConstantClockValue) {
                ConstantClockValue value = (ConstantClockValue) ConstantPool.constantPool
                        .get(i);
                ST entry = m_group.getInstanceOf("ConstantPoolClockEntry");
                entry.add("name", value.toString());
                entry.add("type", value.getBaseType());
                entry.add("value", value.getValue());

                pool.add("constants", entry);
            }
        }
        for (int i = 0; i < ConstantPool.constantPool.size(); i++) {
          if (ConstantPool.constantPool.get(i) instanceof ConstantNILReference) {
            ConstantNILReference value = (ConstantNILReference) ConstantPool.constantPool
                      .get(i);
              ST entry = m_group.getInstanceOf("ConstantPoolNILReferenceEntry");
              entry.add("name", value.toString());
              pool.add("constants", entry);
          }
      }

        return pool;
    }


    private ST generateStructureForwardDeclarationList() {
        Log.debug("CppCodeGeneratorVisitor:generateStructureForwardDeclarationList:");

        ST decls = m_group.getInstanceOf("StructureForwardDeclarationList");

        HashMap<String, TypeStructure> structureDecls = m_symboltable.getStructureDeclarations();

        for (String name : structureDecls.keySet()) {
            ST decl = m_group.getInstanceOf("StructureForwardDeclaration");
            decl.add("name", name);
            decls.add("declarations", decl);
        }

        return decls;
    }

    private ST generateStructureDeclarationList() {
        Log.debug("CppCodeGeneratorVisitor:generateStructureDeclarationList:");
        ST decls = m_group.getInstanceOf("StructureDeclarationList");

        HashMap<String, TypeStructure> structureDecls = m_symboltable.getStructureDeclarations();

        // First create a dependency graph for the structure definitions:
        Graph<String> graph = new Graph<>();

        for (String name : structureDecls.keySet()) {
            TypeStructure struct = structureDecls.get(name);
            graph.addDependency(name, null);

            for (int i = 0; i < struct.m_listOfComponents.size(); i++) {
                StructureComponent component = struct.m_listOfComponents.get(i);
                if (component.m_type instanceof TypeStructure) {
                    TypeStructure innerStruct = (TypeStructure) component.m_type;
                    graph.addDependency(name, innerStruct.getStructureName());
                } else if (component.m_type instanceof TypeArray) {
                    TypeArray array = (TypeArray) component.m_type;

                    if (array.getBaseType() instanceof TypeStructure) {
                        TypeStructure innerStruct = (TypeStructure) array.getBaseType();
                        graph.addDependency(name, innerStruct.getStructureName());
                    }
                }
            }
        }

        // Calculate the dependencies:
        List<Node<String>> nodeList = graph.generateDependencies();

        if (nodeList != null) {
            for (int i = 0; i < nodeList.size(); i++) {
                Node<String> node = nodeList.get(i);

                if (node.m_value != null) {
                    TypeStructure struct = structureDecls.get(node.m_value);
                    ST decl = m_group.getInstanceOf("StructureDefinition");
                    decl.add("name", struct.getStructureName());

                    for (int j = 0; j < struct.m_listOfComponents.size(); j++) {
                        ST stComponent = m_group.getInstanceOf("StructComponentDeclaration");

                        StructureComponent component = struct.m_listOfComponents.get(j);

                        if (component.m_type instanceof TypeStructure) {
                            stComponent.add("name", component.m_alias);
                            stComponent.add("TypeAttribute", ((TypeStructure) component.m_type).getStructureName());
                            decl.add("components", stComponent);
                        } else  if (component.m_type instanceof TypeArray) {
                            TypeArray array = (TypeArray)component.m_type;

                            if ( array.getBaseType() instanceof TypeStructure) {
                                TypeStructure arraystruct = (TypeStructure) array.getBaseType();
                                ST declaration = m_group.getInstanceOf("StructureArrayComponentDeclaration");

                                declaration.add("name", component.m_alias);
                                declaration.add("type", arraystruct.getStructureName());
                                declaration.add("totalNoOfElements", array.getTotalNoOfElements());

                                stComponent.add("TypeAttribute", declaration);
                                decl.add("components", stComponent);
                            }
                        } else {
                            stComponent.add("name", component.m_alias);
                            stComponent.add("TypeAttribute", component.m_type.toST(m_group));
                            decl.add("components", stComponent);
                        }

                    }

                    decls.add("declarations", decl);
                }
            }
        }

        return decls;
    }


// obsolete 2020-02-26 (rm)    
//    private double getDuration(SmallPearlParser.DurationConstantContext ctx) {
//        Integer hours = 0;
//        Integer minutes = 0;
//        Double seconds = 0.0;
//
//        if (ctx.hours() != null) {
//            hours = Integer.valueOf(ctx.hours().IntegerConstant().toString()) * 3600;
//        }
//        if (ctx.minutes() != null) {
//            minutes = Integer.valueOf(ctx.minutes().IntegerConstant()
//                    .toString()) * 60;
//        }
//        if (ctx.seconds() != null) {
//            if (ctx.seconds().IntegerConstant() != null) {
//                seconds = Double.valueOf(ctx.seconds().IntegerConstant()
//                        .toString());
//            } else if (ctx.seconds().floatingPointConstant() != null) {
//                seconds = Double.valueOf(ctx.seconds().floatingPointConstant().FloatingPointNumber().getText());
//            }
//        }
//
//        return (hours + minutes + seconds);
//    }

    /*
     * Time/Clock example: 11:30:00 means 11.30 15:45:3.5 means 15.45 and 3.5
     * seconds 25:00:00 means 1.00
     */
    private Double getTime(SmallPearlParser.TimeConstantContext ctx) {
        Integer hours = 0;
        Integer minutes = 0;
        Double seconds = 0.0;

        hours = (Integer.valueOf(ctx.IntegerConstant(0).toString()) % 24);
        minutes = Integer.valueOf(ctx.IntegerConstant(1).toString());

        if (ctx.IntegerConstant().size() == 3) {
            seconds = Double.valueOf(ctx.IntegerConstant(2).toString());
        }

        if (ctx.floatingPointConstant() != null) {
            seconds = CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());
        }

        if (hours < 0 || minutes < 0 || minutes > 59) {
            throw new NotSupportedTypeException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        return hours * 3600 + minutes * 60 + seconds;
    }

    @Override
    public ST visitModule(SmallPearlParser.ModuleContext ctx) {
        ST module = m_group.getInstanceOf("module");

        module.add("src", this.m_sourceFileName);
        module.add("name", ctx.ID().getText());
        module.add("prologue", generatePrologue());

        org.smallpearl.compiler.SymbolTable.SymbolTableEntry symbolTableEntry = m_currentSymbolTable
                .lookupLocal(ctx.ID().getText());
        m_currentSymbolTable = ((org.smallpearl.compiler.SymbolTable.ModuleEntry) symbolTableEntry).scope;

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.System_partContext) {
                    module.add(
                            "SystemPart",
                            visitSystem_part((SmallPearlParser.System_partContext) c));
                } else if (c instanceof SmallPearlParser.Problem_partContext) {
                    module.add(
                            "ProblemPart",
                            visitProblem_part((SmallPearlParser.Problem_partContext) c));
                } else if (c instanceof SmallPearlParser.Cpp_inlineContext) {
                    ST decl = m_group.getInstanceOf("cpp_inline");
                    module.add(
                            "cpp_inlines",
                            visitCpp_inline((SmallPearlParser.Cpp_inlineContext) c));
                }
            }
        }

        m_currentSymbolTable = m_currentSymbolTable.ascend();

        return module;
    }

    @Override
    public ST visitSystem_part(SmallPearlParser.System_partContext ctx) {
        ST st = m_group.getInstanceOf("SystemPart");

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.Cpp_inlineContext) {
                    ST decl = m_group.getInstanceOf("cpp_inline");
                    st.add("cpp_inlines",
                            visitCpp_inline((SmallPearlParser.Cpp_inlineContext) c));
                } else if (c instanceof SmallPearlParser.Username_declarationContext) {
                    visitUsername_declaration((SmallPearlParser.Username_declarationContext) c);
                } else if (c instanceof SmallPearlParser.UserConfigurationWithoutAssociationContext) {
                    visitUserConfigurationWithoutAssociation((SmallPearlParser.UserConfigurationWithoutAssociationContext) c);
                } else if (c instanceof SmallPearlParser.UserConfigurationWithAssociationContext) {
                    visitUserConfigurationWithAssociation((SmallPearlParser.UserConfigurationWithAssociationContext) c);
                }
            }
        }

        return st;
    }
//
//    @Override
//    public ST visitType(SmallPearlParser.TypeContext ctx) {
//        ST type = m_group.getInstanceOf("type");
//
//        if (ctx.simple_type() != null) {
//            type.add("simple_type", visitSimple_type(ctx.simple_type()));
//        } else if (ctx.typeTime() != null) {
//            type.add("TypeTime", visitTypeTime(ctx.typeTime()));
//        }
//
//        return type;
//    }
//
//    @Override
//    public ST visitSimple_type(SmallPearlParser.Simple_typeContext ctx) {
//        ST simple_type = m_group.getInstanceOf("simple_type");
//
//        if (ctx.type_fixed() != null) {
//            simple_type.add("type_fixed", visitType_fixed(ctx.type_fixed()));
//        } else if (ctx.type_char() != null) {
//            simple_type.add("type_char", visitType_char(ctx.type_char()));
//        } else if (ctx.type_float() != null) {
//            simple_type.add("type_float", visitType_float(ctx.type_float()));
//        }
//
//        return simple_type;
//    }
//
//    @Override
//    public ST visitTypeReferenceTaskType(
//            SmallPearlParser.TypeReferenceTaskTypeContext ctx) {
//        ST st = m_group.getInstanceOf("TypeReferenceTaskType");
//        return st;
//    }

    @Override
    public ST visitTypeReference(SmallPearlParser.TypeReferenceContext ctx) {
        // must become more sophisticated! (2020-04-07 rm)
      ST st;
      ST baseType = visitChildren(ctx);
      if (ctx.virtualDimensionList() == null) {
        st =  m_group.getInstanceOf("TypeReferenceSimpleType");
        st.add("BaseType",baseType);
      } else {
        st =  m_group.getInstanceOf("TypeReferenceArray");
        st.add("basetype",baseType);
      }
      if (ctx.assignmentProtection() != null) {
        ErrorStack.addInternal(ctx, "INV", "visitTypeReference: not treated yet");
      }

      
      return st;
    }


//    @Override
//    public ST visitTypeReferenceSimpleType(
//            SmallPearlParser.TypeReferenceSimpleTypeContext ctx) {
//        ST st = m_group.getInstanceOf("TypeReferenceSimpleType");
//
//        if (ctx.simpleType() != null) {
//            st.add("BaseType", visitSimpleType(ctx.simpleType()));
//        }
//
//        return st;
//    }
/*
 * really needed???? 
    @Override
    public ST visitTypeReferenceProcedureType(
            SmallPearlParser.TypeReferenceProcedureTypeContext ctx) {
        // ST st = m_group.getInstanceOf("TypeReferenceProcedure");
        // return st;
        throw new NotYetImplementedException("REF PROC", ctx.start.getLine(),
                ctx.start.getCharPositionInLine());
    }

    @Override
    public ST visitTypeReferenceSemaType(
            SmallPearlParser.TypeReferenceSemaTypeContext ctx) {
        // ST st = m_group.getInstanceOf("TypeReferenceSema");
        // return st;
        throw new NotYetImplementedException("REF SEMA", ctx.start.getLine(),
                ctx.start.getCharPositionInLine());
    }

    @Override
    public ST visitTypeReferenceBoltType(
            SmallPearlParser.TypeReferenceBoltTypeContext ctx) {
        // ST st = m_group.getInstanceOf("TypeReferenceBolt");
        // return st;
        throw new NotYetImplementedException("REF BOLT", ctx.start.getLine(),
                ctx.start.getCharPositionInLine());
    }

    @Override
    public ST visitTypeReferenceCharType(
            SmallPearlParser.TypeReferenceCharTypeContext ctx) {
        // ST st = m_group.getInstanceOf("TypeReferenceChar");
        // return st;
        throw new NotYetImplementedException("REF CHAR", ctx.start.getLine(),
                ctx.start.getCharPositionInLine());
    }

    @Override
    public ST visitTypeReferenceSignalType(
            SmallPearlParser.TypeReferenceSignalTypeContext ctx) {
        // ST st = m_group.getInstanceOf("TypeReferenceSignal");
        // return st;
        throw new NotYetImplementedException("REF SIGNAL", ctx.start.getLine(),
                ctx.start.getCharPositionInLine());
    }

    @Override
    public ST visitTypeReferenceInterruptType(
            SmallPearlParser.TypeReferenceInterruptTypeContext ctx) {
        // ST st = m_group.getInstanceOf("TypeReferenceInterrupt");
        // return st;
        throw new NotYetImplementedException("REF INTERRUPT",
                ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    @Override
    public ST visitType_fixed(SmallPearlParser.Type_fixedContext ctx) {
        ST fixed_type = m_group.getInstanceOf("fixed_type");
        int width = Defaults.FIXED_LENGTH;

        if (ctx.IntegerConstant() != null) {
            width = Integer.parseInt(ctx.IntegerConstant().getText());
            if (width < 1 || width > 63) {
                throw new NotSupportedTypeException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        fixed_type.add("size", width);

        return fixed_type;
    }

    @Override
    public ST visitType_char(SmallPearlParser.Type_charContext ctx) {
        ST char_type = m_group.getInstanceOf("char_type");
        Integer width = Defaults.CHARACTER_LENGTH;

        if (ctx.IntegerConstant() != null) {
            width = Integer.parseInt(ctx.IntegerConstant().getText());
        }

        char_type.add("size", width);

        return char_type;
    }

    @Override
    public ST visitType_float(SmallPearlParser.Type_floatContext ctx) {
        ST float_type = m_group.getInstanceOf("float_type");
        int precision = m_currentSymbolTable.lookupDefaultFloatLength();

        if (ctx.IntegerConstant() != null) {
            precision = Integer.parseInt(ctx.IntegerConstant().getText());
        }

        float_type.add("size", precision);
        return float_type;
    }

    @Override
    public ST visitTypeTime(SmallPearlParser.TypeTimeContext ctx) {
        ST time_type = m_group.getInstanceOf("time_type");

        if (ctx.type_clock() != null) {
            time_type.add("clock_type", visitType_clock(ctx.type_clock()));
        } else if (ctx.type_duration() != null) {
            time_type.add("duration_type",
                    visitType_duration(ctx.type_duration()));
        }

        return time_type;
    }

    @Override
    public ST visitType_clock(SmallPearlParser.Type_clockContext ctx) {
        ST type = m_group.getInstanceOf("clock_type");

        type.add("init", 0);
        return type;
    }

    @Override
    public ST visitType_duration(SmallPearlParser.Type_durationContext ctx) {
        ST type = m_group.getInstanceOf("duration_type");
        type.add("init", 0);
        return type;
    }
*/
    @Override
    public ST visitScalarVariableDeclaration(
            SmallPearlParser.ScalarVariableDeclarationContext ctx) {
        ST scalarVariableDeclaration = m_group
                .getInstanceOf("ScalarVariableDeclaration");

        if (ctx != null) {
            for (int i = 0; i < ctx.variableDenotation().size(); i++) {
                scalarVariableDeclaration
                        .add("variable_denotations",
                                visitVariableDenotation(ctx
                                        .variableDenotation().get(i)));
            }

            if (ctx.cpp_inline() != null) {
                scalarVariableDeclaration.add("cpp",
                        visitCpp_inline(ctx.cpp_inline()));
            }
        }

        return scalarVariableDeclaration;
    }

    @Override
    public ST visitTypeStructure(SmallPearlParser.TypeStructureContext ctx) {
        ST st = m_group.getInstanceOf("StructureFormalParameter");
        st.add("type", "????");
        return st;
    }

    @Override
    public ST visitVariableDenotation(
            SmallPearlParser.VariableDenotationContext ctx) {
        ST variableDenotation = m_group.getInstanceOf("variable_denotation");
        ST typeAttribute = m_group.getInstanceOf("TypeAttribute");
        ArrayList<String> identifierDenotationList = null;

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.IdentifierDenotationContext) {
                    identifierDenotationList = getIdentifierDenotation((SmallPearlParser.IdentifierDenotationContext) c);
                    // NOTE: obsolete??
                    //    getIdentifierDenotation((SmallPearlParser.IdentifierDenotationContext) c);
                }
            }

            for (int i = 0; i < identifierDenotationList.size(); i++) {
                ST v = m_group.getInstanceOf("VariableDeclaration");

                SymbolTableEntry entry = m_currentSymbolTable
                        .lookup(identifierDenotationList.get(i));
                VariableEntry var = (VariableEntry) entry;

                v.add("name", identifierDenotationList.get(i));
                v.add("TypeAttribute", var.getType().toST(m_group));
                // v.add("global", "?");
                v.add("inv", var.getAssigmentProtection());

                if (var.getInitializer() != null) {
                    if (var.getInitializer() instanceof SimpleInitializer) {
                        v.add("InitElement", ((SimpleInitializer) var.getInitializer()).getConstant());
                    }
                }

                variableDenotation.add("decl", v);
            }
        } else {
            throw new InternalCompilerErrorException(
                    ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        return variableDenotation;
    }

    /*
     * @Override public ST
     * visitVariableDenotation(SmallPearlParser.VariableDenotationContext ctx) {
     * ST variableDenotation = m_group.getInstanceOf("variable_denotation"); ST
     * typeAttribute = m_group.getInstanceOf("TypeAttribute"); boolean
     * hasGlobalAttribute = false; boolean hasAllocationProtection = false;
     *
     * ArrayList<String> identifierDenotationList = null; ArrayList<ST>
     * initElementList = null;
     *
     * if (ctx != null) { for (ParseTree c : ctx.children) { if (c instanceof
     * SmallPearlParser.IdentifierDenotationContext) { identifierDenotationList
     * = getIdentifierDenotation((SmallPearlParser.IdentifierDenotationContext)
     * c); } else if (c instanceof SmallPearlParser.AllocationProtectionContext)
     * { hasAllocationProtection = true; } else if (c instanceof
     * SmallPearlParser.TypeAttributeContext) { typeAttribute =
     * visitTypeAttribute((SmallPearlParser.TypeAttributeContext) c); } else if
     * (c instanceof SmallPearlParser.GlobalAttributeContext) {
     * hasGlobalAttribute = true; } else if (c instanceof
     * SmallPearlParser.InitialisationAttributeContext) { initElementList =
     * getInitialisationAttribute
     * ((SmallPearlParser.InitialisationAttributeContext) c); } }
     *
     * if (initElementList != null && identifierDenotationList.size() !=
     * initElementList.size()) { throw new
     * NumberOfInitializerMismatchException(ctx.getText(), ctx.start.getLine(),
     * ctx.start.getCharPositionInLine()); }
     *
     * for (int i = 0; i < identifierDenotationList.size(); i++) { ST v =
     * m_group.getInstanceOf("VariableDeclaration"); v.add("name",
     * identifierDenotationList.get(i)); v.add("TypeAttribute", typeAttribute);
     * v.add("global", hasGlobalAttribute); v.add("inv",
     * hasAllocationProtection);
     *
     * if (initElementList != null) v.add("InitElement",
     * initElementList.get(i));
     *
     * variableDenotation.add("decl", v); } }
     *
     * return variableDenotation; }
     */

    private ArrayList<String> getIdentifierDenotation(
            SmallPearlParser.IdentifierDenotationContext ctx) {
        ArrayList<String> identifierDenotationList = new ArrayList<String>();

        if (ctx != null) {
            for (int i = 0; i < ctx.ID().size(); i++) {
                identifierDenotationList.add(ctx.ID().get(i).toString());
            }
        }

        return identifierDenotationList;
    }

    private ArrayList<Integer> getPreset(SmallPearlParser.PresetContext ctx) {
        ArrayList<Integer> presetList = new ArrayList<Integer>();

        if (ctx != null) {
            for (int i = 0; i < ctx.integerWithoutPrecision().size(); i++) {
                Integer preset = Integer
                        .parseInt(ctx.integerWithoutPrecision(i)
                                .IntegerConstant().getText());
                presetList.add(preset);
            }
        }

        return presetList;
    }

//    private ST getArrayInitialisationAttribute(
//            SmallPearlParser.ArrayInitialisationAttributeContext ctx,
//            int noOfElements) {
//        ST st = m_group.getInstanceOf("ArrayInitalisations");
//        ST last_value = null;
//
//        if (noOfElements < ctx.initElement().size()) {
//            throw new NumberOfInitializerMismatchException(ctx.getText(),
//                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        for (int i = 0; i < noOfElements; i++) {
//            ST element = m_group.getInstanceOf("InitElement");
//
//            if (i < ctx.initElement().size()) {
//                SmallPearlParser.InitElementContext initElement = ctx
//                        .initElement().get(i);
//
//                if (initElement.ID() != null) {
//                    throw new InternalCompilerErrorException(ctx.getText(),
//                            ctx.start.getLine(),
//                            ctx.start.getCharPositionInLine());
//                } else if (initElement.constant() != null) {
//                    ST stConstant = getInitElement(initElement.constant());
//                    last_value = stConstant;
//                    element.add("value", stConstant);
//                    st.add("initElements", element);
//                } else if (initElement.constantExpression() != null) {
//                    // constantExpression
//                    // : floatingPointConstant
//                    // | Sign? durationConstant
//                    // | constantFixedExpression
//
//                    throw new InternalCompilerErrorException(ctx.getText(),
//                            ctx.start.getLine(),
//                            ctx.start.getCharPositionInLine());
//                }
//
//                /*
//                 * if ( constantExpression instanceof
//                 * SmallPearlParser.ConstantFixedExpressionContext) {
//                 * ConstantValue value =
//                 * m_constantExpressionEvaluatorVisitor.lookup
//                 * ((ctx.initElement().get(i).constantExpression())); ST stValue
//                 * = m_group.getInstanceOf("expression"); stValue.add("code",
//                 * value); last_value = stValue; element.add("value", stValue);
//                 * st.add("initElements", element); }
//                 */
//            } else {
//                st.add("initElements", last_value);
//            }
//        }
//
//        return st;
//    }

//    private ArrayList<ST> getInitialisationAttribute(
//            SmallPearlParser.InitialisationAttributeContext ctx) {
//        ArrayList<ST> initElementList = new ArrayList<ST>();
//
//        if (ctx != null) {
//            for (int i = 0; i < ctx.initElement().size(); i++) {
//
//                if (ctx.initElement(i).constantExpression() != null) {
//                    ConstantValue value = m_constantExpressionEvaluatorVisitor
//                            .lookup((ctx.initElement().get(i)
//                                    .constantExpression()));
//
//                    ST stValue = m_group.getInstanceOf("expression");
//
//                    if (value instanceof ConstantFixedValue) {
//                        stValue.add("code", value.toString());
//                    } else {
//                        stValue.add("code", value);
//                    }
//
//                    initElementList.add(stValue);
//                } else if (ctx.initElement(i).constant() != null) {
//                    ST stValue = m_group.getInstanceOf("expression");
//                    stValue.add("code", getInitElement(ctx.initElement(i)
//                            .constant()));
//                    initElementList.add(stValue);
//                } else if (ctx.initElement(i).ID() != null) {
//                    throw new InternalCompilerErrorException(ctx.getText(),
//                            ctx.start.getLine(),
//                            ctx.start.getCharPositionInLine());
//                }
//            }
//        }
//
//        return initElementList;
//    }

    //TODO: Init Array
    private ST getArrayInitialisationAttribute(VariableEntry variableEntry) {
        ST st = m_group.getInstanceOf("ArrayInitalisations");
        ST last_value = null;
        ArrayList<ST> initElementList = new ArrayList<ST>();
        int noOfElements = 0;
        int noOfArrayElements = ((TypeArray) variableEntry
                .getType()).getTotalNoOfElements();

/*
        if (noOfElements <  {

        throw new NumberOfInitializerMismatchException(ctx.getText(),
                ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
*/

        if (variableEntry.getInitializer() != null) {
            ST stValue = null; // we need the last init element to fill up incomplete array initialisers

            if (variableEntry.getInitializer() instanceof SimpleInitializer) {
                SimpleInitializer initializer = (SimpleInitializer) variableEntry.getInitializer();
                ConstantValue value = initializer.getConstant();
                stValue = m_group.getInstanceOf("expression");

                if (value instanceof ConstantFixedValue) {
                    stValue.add("code", value.toString());
                } else {
                    stValue.add("code", value);
                }
                noOfElements++;
                initElementList.add(stValue);
            } else if (variableEntry.getInitializer() instanceof ArrayOrStructureInitializer) {
                ArrayOrStructureInitializer initializer = (ArrayOrStructureInitializer) variableEntry.getInitializer();
                ArrayList<Initializer> listOfInitializers = initializer.getInitElementList();

                for (int i = 0; i < listOfInitializers.size(); i++) {
                    stValue = m_group.getInstanceOf("expression");
                    stValue.add("code", listOfInitializers.get(i));
                    noOfElements++;
                    initElementList.add(stValue);
                }

                // fill array initializer with last value
                while (noOfElements < noOfArrayElements) {
                    initElementList.add(stValue);
                    noOfElements++;
                }
            }

//    private ST getArrayInitialisationAttribute(
//            SmallPearlParser.ArrayInitialisationAttributeContext ctx,
//            int noOfElements) {
//        ST st = m_group.getInstanceOf("ArrayInitalisations");
//        ST last_value = null;
//
//        if (noOfElements < ctx.initElement().size()) {
//            throw new NumberOfInitializerMismatchException(ctx.getText(),
//                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        for (int i = 0; i < noOfElements; i++) {
//            ST element = m_group.getInstanceOf("InitElement");
//
//            if (i < ctx.initElement().size()) {
//                SmallPearlParser.InitElementContext initElement = ctx
//                        .initElement().get(i);
//
//                if (initElement.ID() != null) {
//                    throw new InternalCompilerErrorException(ctx.getText(),
//                            ctx.start.getLine(),
//                            ctx.start.getCharPositionInLine());
//                } else if (initElement.constant() != null) {
//                    ST stConstant = getInitElement(initElement.constant());
//                    last_value = stConstant;
//                    element.add("value", stConstant);
//                    st.add("initElements", element);
//                } else if (initElement.constantExpression() != null) {
//                    // constantExpression
//                    // : floatingPointConstant
//                    // | Sign? durationConstant
//                    // | constantFixedExpression
//
//                    throw new InternalCompilerErrorException(ctx.getText(),
//                            ctx.start.getLine(),
//                            ctx.start.getCharPositionInLine());
//                }
//
//                /*
//                 * if ( constantExpression instanceof
//                 * SmallPearlParser.ConstantFixedExpressionContext) {
//                 * ConstantValue value =
//                 * m_constantExpressionEvaluatorVisitor.lookup
//                 * ((ctx.initElement().get(i).constantExpression())); ST stValue
//                 * = m_group.getInstanceOf("expression"); stValue.add("code",
//                 * value); last_value = stValue; element.add("value", stValue);
//                 * st.add("initElements", element); }
//                 */
//            } else {
//                st.add("initElements", last_value);
//            }
//        }
//
//        return st;
//    }
/** TODO: MS
 for (int i = 0; i < ctx.initElement().size(); i++) {

 if (ctx.initElement(i).constantExpression() != null) {
 ConstantValue value = m_constantExpressionEvaluatorVisitor
 .lookup((ctx.initElement().get(i)
 .constantExpression()));

 ST stValue = m_group.getInstanceOf("expression");

 if (value instanceof ConstantFixedValue) {
 stValue.add("code", value.toString());
 } else {
 stValue.add("code", value);
 }

 initElementList.add(stValue);
 } else if (ctx.initElement(i).constant() != null) {
 ST stValue = m_group.getInstanceOf("expression");
 stValue.add("code", getInitElement(ctx.initElement(i)
 .constant()));
 initElementList.add(stValue);
 } else if (ctx.initElement(i).ID() != null) {
 throw new InternalCompilerErrorException(ctx.getText(),
 ctx.start.getLine(),
 ctx.start.getCharPositionInLine());
 }
 }
 **/
        }

        st.add("initElements", initElementList);
        return st;
    }


//    private ST getInitElement(SmallPearlParser.ConstantContext ctx) {
//        ST constant = m_group.getInstanceOf("Constant");
//        int last_sign = m_sign;
//        ASTAttribute ast = m_ast.lookup(ctx);
//
//        if (ctx != null) {
//            if (ctx.sign() != null
//                    && ctx.sign() instanceof SmallPearlParser.SignMinusContext) {
//                m_sign = -1;
//            }
//
//            if (ctx.fixedConstant() != null) {
//                ST integerConstant = m_group.getInstanceOf("IntegerConstant");
//                int value;
//                int precision = Defaults.FIXED_LENGTH;
//
//                value = Integer.parseInt(ctx.fixedConstant().IntegerConstant()
//                        .getText());
//
//                if (ctx.getChildCount() > 1) {
//                    if (ctx.getChild(0).getText().equals("-")) {
//                        value = -value;
//                    }
//                }
//
//                integerConstant.add("value", value);
//                constant.add("IntegerConstant", integerConstant);
//            } else if (ctx.durationConstant() != null) {
//                ST durationConstant = m_group.getInstanceOf("DurationConstant");
//                durationConstant.add("value",
//                        visitDurationConstant(ctx.durationConstant()));
//                constant.add("DurationConstant", durationConstant);
//            } else if (ctx.timeConstant() != null) {
//                ST timeConstant = m_group.getInstanceOf("TimeConstant");
//                timeConstant
//                        .add("value", visitTimeConstant(ctx.timeConstant()));
//                constant.add("TimeConstant", timeConstant);
//            } else if (ctx.floatingPointConstant() != null) {
//                ST durationConstant = m_group
//                        .getInstanceOf("FloatingPointConstant");
//                Double value;
//                Integer sign = 1;
//
//                value = CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());
//
//                if (ctx.getChildCount() > 1) {
//                    if (ctx.getChild(0).getText().equals("-")) {
//                        value = -value;
//                    }
//                }
//
//                constant.add("FloatingPointConstant", value);
//            } else if (ctx.stringConstant() != null) {
//                ST stringConstant = m_group.getInstanceOf("StringConstant");
//                String s = ctx.stringConstant().StringLiteral().toString();
//
//                if (s.startsWith("'")) {
//                    s = s.substring(1, s.length());
//                }
//
//                if (s.endsWith("'")) {
//                    s = s.substring(0, s.length() - 1);
//                }
//
//                s = CommonUtils.convertPearl2CString(s);
//
//                stringConstant.add("value", s);
//                constant.add("StringConstant", stringConstant);
//            } else if (ctx.bitStringConstant() != null) {
//                constant.add("BitStringConstant", getBitStringConstant(ctx));
//            }
//        }
//
//        m_sign = last_sign;
//        return constant;
//    }
//
//    private String getBitStringConstant(SmallPearlParser.ConstantContext ctx) {
//        String bitString = ctx.bitStringConstant().BitStringLiteral()
//                .toString();
//        int nb = CommonUtils.getBitStringLength(bitString);
//        long value = CommonUtils.convertBitStringToLong(bitString);
//
//        ConstantBitValue bitConst = ConstantPool.lookupBitValue(value, nb);
//
//        if (bitConst != null) {
//            return bitConst.toString();
//        }
//
//        throw new InternalCompilerErrorException(ctx.getText(),
//                ctx.start.getLine(), ctx.start.getCharPositionInLine());
//    }
//
//    private ST formatBitStringConstant(Long l, int numberOfBits) {
//        ST bitStringConstant = m_group.getInstanceOf("BitStringConstant");
//        ST constant = m_group.getInstanceOf("Constant");
//
//        String b = Long.toBinaryString(l);
//        String bres = "";
//
//        int l1 = b.length();
//
//        if (numberOfBits < b.length()) {
//            bres = "";
//            for (int i = 0; i < numberOfBits; i++) {
//                bres = bres + b.charAt(i);
//            }
//
//            Long r = Long.parseLong(bres, 2);
//
//            if (Long.toBinaryString(Math.abs(r)).length() < 15) {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(r));
//            } else if (Long.toBinaryString(Math.abs(r)).length() < 31) {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(r)
//                        + "UL");
//            } else {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(r)
//                        + "ULL");
//            }
//        } else if (numberOfBits > b.length()) {
//            bres = b;
//            Long r = Long.parseLong(bres, 2);
//            if (Long.toBinaryString(Math.abs(r)).length() < 15) {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(r));
//            } else if (Long.toBinaryString(Math.abs(r)).length() < 31) {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(r)
//                        + "UL");
//            } else {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(r)
//                        + "ULL");
//            }
//        } else {
//            if (Long.toBinaryString(Math.abs(l)).length() < 15) {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(l));
//            } else if (Long.toBinaryString(Math.abs(l)).length() < 31) {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(l)
//                        + "UL");
//            } else {
//                bitStringConstant.add("value", "## 0x" + Long.toHexString(l)
//                        + "ULL");
//            }
//        }
//
//        bitStringConstant.add("length", b.length());
//
//        constant.add("BitStringConstant", bitStringConstant);
//
//        return constant;
//    }

    @Override
    public ST visitDurationConstant(SmallPearlParser.DurationConstantContext ctx) {
        ST st = m_group.getInstanceOf("DurationConstant");

        ConstantDurationValue value = CommonUtils.getConstantDurationValue(ctx, m_sign);

        if (value == null) {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        st.add("value", value);
        return st;
    }


    @Override
    public ST visitTypeAttributeForArray(
            SmallPearlParser.TypeAttributeForArrayContext ctx) {
        ST type = m_group.getInstanceOf("TypeAttribute");

        if (ctx.type_fixed() != null) {
            type.add("Type", visitType_fixed(ctx.type_fixed()));
        }

        return type;
    }

    @Override
    public ST visitTypeAttribute(SmallPearlParser.TypeAttributeContext ctx) {
        ST type = m_group.getInstanceOf("TypeAttribute");

        if (ctx.simpleType() != null) {
            type.add("Type", visitSimpleType(ctx.simpleType()));
        } else if (ctx.typeReference() != null) {
            type.add("Type", visitTypeReference(ctx.typeReference()));
        }

        return type;
    }

    @Override
    public ST visitSimpleType(SmallPearlParser.SimpleTypeContext ctx) {
        ST simpleType = m_group.getInstanceOf("SimpleType");

        if (ctx != null) {
            if (ctx.typeInteger() != null) {
                simpleType.add("TypeInteger",
                        visitTypeInteger(ctx.typeInteger()));
            } else if (ctx.typeDuration() != null) {
                simpleType.add("TypeDuration",
                        visitTypeDuration(ctx.typeDuration()));
            } else if (ctx.typeBitString() != null) {
                simpleType.add("TypeBitString",
                        visitTypeBitString(ctx.typeBitString()));
            } else if (ctx.typeFloatingPointNumber() != null) {
                simpleType.add("TypeFloatingPointNumber",
                        visitTypeFloatingPointNumber(ctx
                                .typeFloatingPointNumber()));
            } else if (ctx.typeTime() != null) {
                simpleType.add("TypeTime", visitTypeTime(ctx.typeTime()));
            } else if (ctx.typeCharacterString() != null) {
                simpleType.add("TypeCharacterString",
                        visitTypeCharacterString(ctx.typeCharacterString()));
            }
        }

        return simpleType;
    }

    @Override
    public ST visitTypeDuration(SmallPearlParser.TypeDurationContext ctx) {
        ST st = m_group.getInstanceOf("TypeDuration");

        if (ctx != null) {
            st.add("code", 1);
        }

        return st;
    }

    @Override
    public ST visitTypeInteger(SmallPearlParser.TypeIntegerContext ctx) {
        ST st = m_group.getInstanceOf("TypeInteger");
        int size = m_currentSymbolTable.lookupDefaultFixedLength();

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.MprecisionContext) {
                    size = Integer
                            .parseInt(((SmallPearlParser.MprecisionContext) c)
                                    .getText());
                }
            }
        }

        st.add("size", size);

        return st;
    }

    @Override
    public ST visitTypeBitString(SmallPearlParser.TypeBitStringContext ctx) {
        ST st = m_group.getInstanceOf("TypeBitString");

        int length = m_currentSymbolTable.lookupDefaultBitLength();

        if (ctx.IntegerConstant() != null) {
            length = Integer.parseInt(ctx.IntegerConstant().getText());
            if (length < 1 || length > 64) {
                throw new NotSupportedTypeException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        st.add("length", length);

        return st;
    }

    @Override
    public ST visitTypeCharacterString(
            SmallPearlParser.TypeCharacterStringContext ctx) {
        ST st = m_group.getInstanceOf("TypeCharacterString");
        Integer size = Defaults.CHARACTER_LENGTH;

        if (ctx.IntegerConstant() != null) {
            size = Integer.parseInt(ctx.IntegerConstant().getText());

            if (size < 1 || size > Defaults.CHARACTER_MAX_LENGTH) {
                throw new NotSupportedTypeException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        st.add("size", size);

        return st;
    }

    @Override
    public ST visitTypeFloatingPointNumber(
            SmallPearlParser.TypeFloatingPointNumberContext ctx) {
        ST st = m_group.getInstanceOf("TypeFloatingPointNumber");
        int precision = Defaults.FLOAT_PRECISION;

        if (ctx.IntegerConstant() != null) {
            precision = Integer.parseInt(ctx.IntegerConstant().getText());

            if (precision != Defaults.FLOAT_SHORT_PRECISION
                    && precision != Defaults.FLOAT_LONG_PRECISION) {
                throw new NotSupportedTypeException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        st.add("precision", precision);

        return st;
    }

    @Override
    public ST visitProblem_part(SmallPearlParser.Problem_partContext ctx) {
        ST problem_part = m_group.getInstanceOf("ProblemPart");

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.ScalarVariableDeclarationContext) {
                    problem_part
                            .add("ScalarVariableDeclarations",
                                    visitScalarVariableDeclaration((SmallPearlParser.ScalarVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.ArrayVariableDeclarationContext) {
                    problem_part
                            .add("ArrayVariableDeclarations",
                                    visitArrayVariableDeclaration((SmallPearlParser.ArrayVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.StructVariableDeclarationContext) {
                    problem_part
                            .add("StructVariableDeclarations",
                                    visitStructVariableDeclaration((SmallPearlParser.StructVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.SemaDeclarationContext) {
                    problem_part
                            .add("SemaDeclarations",
                                    visitSemaDeclaration((SmallPearlParser.SemaDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.BoltDeclarationContext) {
                    problem_part
                            .add("BoltDeclarations",
                                    visitBoltDeclaration((SmallPearlParser.BoltDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.TaskDeclarationContext) {
                    problem_part
                            .add("TaskDeclarations",
                                    visitTaskDeclaration((SmallPearlParser.TaskDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.DationSpecificationContext) {
                    problem_part
                            .add("DationSpecifications",
                                    visitDationSpecification((SmallPearlParser.DationSpecificationContext) c));
                } else if (c instanceof SmallPearlParser.DationDeclarationContext) {
                    problem_part
                            .add("DationDeclarations",
                                    visitDationDeclaration((SmallPearlParser.DationDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.ProcedureDeclarationContext) {
                    ST procedureDeclaration = visitProcedureDeclaration((SmallPearlParser.ProcedureDeclarationContext) c);

                    problem_part
                            .add("ProcedureDeclarations", procedureDeclaration);
                    ST spc = m_group.getInstanceOf("ProcedureSpecification");
                    spc.add("id", procedureDeclaration.getAttribute("id"));
                    Object obj = procedureDeclaration.getAttribute("listOfFormalParameters");
                    if (obj != null) {
                      spc.add("listOfFormalParameters", obj);
                    }
                    obj = procedureDeclaration.getAttribute("resultAttribute");
                    if (obj != null) {
                       spc.add("resultAttribute", obj);
                    }
                    obj = procedureDeclaration.getAttribute("globalAttribute");
                    if (obj != null) {
                       spc.add("globalAttribute", obj);
                    }

                    problem_part.add("ProcedureSpecifications", spc);
                } else if (c instanceof SmallPearlParser.InterruptSpecificationContext) {
                    problem_part
                            .add("InterruptSpecifications",
                                    visitInterruptSpecification((SmallPearlParser.InterruptSpecificationContext) c));
                }
            }
        }


        ST semaphoreArrays = m_group.getInstanceOf("SemaphoreArrays");
        ST boltArrays = m_group.getInstanceOf("BoltArrays");
        
  
        //problem_part.add("semaphoreArrays", semaphoreArrays);
        problem_part.add("semaphoreArrays", constantSemaphoreArrays);
        problem_part.add("boltArrays", constantBoltArrays);        


        ST arrayDescriptors = m_group.getInstanceOf("ArrayDescriptors");
        LinkedList<ArrayDescriptor> listOfArrayDescriptors = m_symbolTableVisitor
                .getListOfArrayDescriptors();

        for (int i = 0; i < listOfArrayDescriptors.size(); i++) {
            ST stArrayDescriptor = m_group.getInstanceOf("ArrayDescriptor");

            ArrayDescriptor arrayDescriptor = listOfArrayDescriptors.get(i);

            stArrayDescriptor.add("name", arrayDescriptor.getName());
            arrayDescriptors.add("descriptors", stArrayDescriptor);

            ArrayList<ArrayDimension> listOfArrayDimensions = arrayDescriptor
                    .getDimensions();

            ST stArrayLimits = m_group.getInstanceOf("ArrayLimits");

            for (int j = 0; j < listOfArrayDimensions.size(); j++) {
                ST stArrayLimit = m_group.getInstanceOf("ArrayLimit");

                stArrayLimit.add("lowerBoundary", Integer
                        .toString(listOfArrayDimensions.get(j)
                                .getLowerBoundary()));
                stArrayLimit.add("upperBoundary", Integer
                        .toString(listOfArrayDimensions.get(j)
                                .getUpperBoundary()));

                int noOfElemenstOnNextSubArray = 0;
                for (int k = j + 1; k < listOfArrayDimensions.size(); k++) {
                    noOfElemenstOnNextSubArray += listOfArrayDimensions.get(k)
                            .getNoOfElements();
                }

                if (noOfElemenstOnNextSubArray == 0) {
                    noOfElemenstOnNextSubArray = 1;
                }

                stArrayLimit.add("noOfElemenstOnNextSubArray",
                        noOfElemenstOnNextSubArray);
                stArrayLimits.add("limits", stArrayLimit);
            }

            stArrayDescriptor.add("limits", stArrayLimits);
            stArrayDescriptor.add("dimensions",
                    Integer.toString(listOfArrayDimensions.size()));
        }

        problem_part.add("ArrayDescriptors", arrayDescriptors);

        return problem_part;
    }

    @Override
    public ST visitSemaDeclaration(SmallPearlParser.SemaDeclarationContext ctx) {
        ST st = m_group.getInstanceOf("SemaDeclaration");
        boolean hasGlobalAttribute = false;

        ArrayList<String> identifierDenotationList = null;
        ArrayList<Integer> presetList = null;

        if (ctx != null) {
            if (ctx.globalAttribute() != null) {
                hasGlobalAttribute = true;
            }

            if (ctx.identifierDenotation() != null) {
                identifierDenotationList = getIdentifierDenotation(ctx
                        .identifierDenotation());
            }

            if (ctx.preset() != null) {
                presetList = getPreset(ctx.preset());

                if (identifierDenotationList.size() != presetList.size()) {
                    throw new NumberOfInitializerMismatchException(
                            ctx.getText(), ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }
            }
        }

        for (int i = 0; i < identifierDenotationList.size(); i++) {
            ST v = m_group.getInstanceOf("sema_declaration");
            v.add("name", identifierDenotationList.get(i));
            v.add("global", hasGlobalAttribute);

            if (presetList != null)
                v.add("preset", presetList.get(i));

            st.add("decl", v);
        }

        return st;
    }

    @Override
    public ST visitBoltDeclaration(SmallPearlParser.BoltDeclarationContext ctx) {
        ST st = m_group.getInstanceOf("BoltDeclaration");
        boolean hasGlobalAttribute = false;

        ArrayList<String> identifierDenotationList = null;

        if (ctx != null) {
            if (ctx.globalAttribute() != null) {
                hasGlobalAttribute = true;
            }

            if (ctx.identifierDenotation() != null) {
                identifierDenotationList = getIdentifierDenotation(ctx
                        .identifierDenotation());
            }
        }

        for (int i = 0; i < identifierDenotationList.size(); i++) {
            ST v = m_group.getInstanceOf("bolt_declaration");
            v.add("name", identifierDenotationList.get(i));
            v.add("global", hasGlobalAttribute);

            st.add("decl", v);
        }

        return st;
    }


    @Override
    public ST visitBoltReserve(SmallPearlParser.BoltReserveContext ctx) {
        ST operation = m_group.getInstanceOf("BoltReserve");
        ST newSemaOrBoltArray = m_group.getInstanceOf("BoltArray");
        
        treatListOfSemaOrBoltNames(m_listOfBoltArrays,constantBoltArrays,
            operation, newSemaOrBoltArray, ctx.listOfNames());
        return operation;
    }
 
    @Override
    public ST visitBoltFree(SmallPearlParser.BoltFreeContext ctx) {
        ST operation = m_group.getInstanceOf("BoltFree");
        ST newSemaOrBoltArray = m_group.getInstanceOf("BoltArray");
        
        treatListOfSemaOrBoltNames(m_listOfBoltArrays,constantBoltArrays,
            operation, newSemaOrBoltArray, ctx.listOfNames());
        return operation;
    }


    @Override
    public ST visitBoltEnter(SmallPearlParser.BoltEnterContext ctx) {
        ST operation = m_group.getInstanceOf("BoltEnter");
        ST newSemaOrBoltArray = m_group.getInstanceOf("BoltArray");
        
        treatListOfSemaOrBoltNames(m_listOfBoltArrays,constantBoltArrays,
            operation, newSemaOrBoltArray, ctx.listOfNames());
        return operation;
    }


    @Override
    public ST visitBoltLeave(SmallPearlParser.BoltLeaveContext ctx) {
        ST operation = m_group.getInstanceOf("BoltLeave");
        ST newSemaOrBoltArray = m_group.getInstanceOf("BoltArray");
        
        treatListOfSemaOrBoltNames(m_listOfBoltArrays,constantBoltArrays,
            operation, newSemaOrBoltArray, ctx.listOfNames());
        return operation;
    }


    @Override
    public ST visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
        ST taskdecl = m_group.getInstanceOf("task_declaration");
        ST priority = m_group.getInstanceOf("expression");
        Integer main = 0;

        this.m_currentSymbolTable = m_symbolTableVisitor
                .getSymbolTablePerContext(ctx);

        if (ctx.priority() != null) {
            priority = visitAndDereference(ctx.priority().expression());
        } else {
            priority.add("code", Defaults.DEFAULT_TASK_PRIORITY);
        }

        if (ctx.task_main() != null) {
            main = 1;
        }

        taskdecl.add("name", ctx.ID());
        taskdecl.add("priority", priority);
        taskdecl.add("main", main);

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.TaskBodyContext) {
                    taskdecl.add("body",
                            visitTaskBody((SmallPearlParser.TaskBodyContext) c));
                } else if (c instanceof SmallPearlParser.Cpp_inlineContext) {
                    taskdecl.add(
                            "cpp",
                            visitCpp_inline((SmallPearlParser.Cpp_inlineContext) c));
                }
            }
        }

        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return taskdecl;
    }

    @Override
    public ST visitTaskBody(SmallPearlParser.TaskBodyContext ctx) {
        ST taskbody = m_group.getInstanceOf("task_body");

        if (ctx != null && ctx.children != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.ScalarVariableDeclarationContext) {
                    taskbody.add(
                            "decls",
                            visitScalarVariableDeclaration((SmallPearlParser.ScalarVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.ArrayVariableDeclarationContext) {
                    taskbody.add(
                            "decls",
                            visitArrayVariableDeclaration((SmallPearlParser.ArrayVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.StructVariableDeclarationContext) {
                    taskbody.add(
                            "decls",
                            visitStructVariableDeclaration((SmallPearlParser.StructVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.StatementContext) {
                    taskbody.add(
                            "statements",
                            visitStatement((SmallPearlParser.StatementContext) c));
                } else if (c instanceof SmallPearlParser.DationDeclarationContext) {
                    taskbody.add(
                            "decls",
                            visitDationDeclaration((SmallPearlParser.DationDeclarationContext) c));
                }
            }
        }

        return taskbody;
    }

    @Override
    public ST visitTimeConstant(SmallPearlParser.TimeConstantContext ctx) {
        ST st = m_group.getInstanceOf("TimeConstant");
        st.add("value", getTime(ctx));
        return st;
    }



    private ST getReferenceExpression(SmallPearlParser.ExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ReferenceExpression");

        if (ctx != null) {
            if (ctx instanceof SmallPearlParser.BaseExpressionContext) {
                st.add("code",
                        visitBaseExpression(((SmallPearlParser.BaseExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.AdditiveExpressionContext) {
                st.add("code",
                        visitAdditiveExpression(((SmallPearlParser.AdditiveExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.SubtractiveExpressionContext) {
                st.add("code",
                        visitSubtractiveExpression(((SmallPearlParser.SubtractiveExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.MultiplicativeExpressionContext) {
                st.add("code",
                        visitMultiplicativeExpression((SmallPearlParser.MultiplicativeExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.DivideExpressionContext) {
                st.add("code",
                        visitDivideExpression((SmallPearlParser.DivideExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.DivideIntegerExpressionContext) {
                st.add("code",
                        visitDivideIntegerExpression((SmallPearlParser.DivideIntegerExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.UnaryAdditiveExpressionContext) {
                st.add("code",
                        visitUnaryAdditiveExpression((SmallPearlParser.UnaryAdditiveExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.UnarySubtractiveExpressionContext) {
                st.add("code",
                        visitUnarySubtractiveExpression((SmallPearlParser.UnarySubtractiveExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.ExponentiationExpressionContext) {
                st.add("code",
                        visitExponentiationExpression((SmallPearlParser.ExponentiationExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.LtRelationalExpressionContext) {
                st.add("code",
                        visitLtRelationalExpression((SmallPearlParser.LtRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.GeRelationalExpressionContext) {
                st.add("code",
                        visitGeRelationalExpression((SmallPearlParser.GeRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.NeRelationalExpressionContext) {
                st.add("code",
                        visitNeRelationalExpression((SmallPearlParser.NeRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.EqRelationalExpressionContext) {
                st.add("code",
                        visitEqRelationalExpression((SmallPearlParser.EqRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.GtRelationalExpressionContext) {
                st.add("code",
                        visitGtRelationalExpression((SmallPearlParser.GtRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.LeRelationalExpressionContext) {
                st.add("code",
                        visitLeRelationalExpression((SmallPearlParser.LeRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.IsRelationalExpressionContext) {
              st.add("code",
                      visitIsRelationalExpression((SmallPearlParser.IsRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.IsntRelationalExpressionContext) {
              st.add("code",
                      visitIsntRelationalExpression((SmallPearlParser.IsntRelationalExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.AtanExpressionContext) {
                st.add("code",
                        visitAtanExpression((SmallPearlParser.AtanExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.CosExpressionContext) {
                st.add("code",
                        visitCosExpression((SmallPearlParser.CosExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.ExpExpressionContext) {
                st.add("code",
                        visitExpExpression((SmallPearlParser.ExpExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.LnExpressionContext) {
                st.add("code",
                        visitLnExpression((SmallPearlParser.LnExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.SinExpressionContext) {
                st.add("code",
                        visitSinExpression((SmallPearlParser.SinExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.SqrtExpressionContext) {
                st.add("code",
                        visitSqrtExpression((SmallPearlParser.SqrtExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.TanExpressionContext) {
                st.add("code",
                        visitTanExpression((SmallPearlParser.TanExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.TanhExpressionContext) {
                st.add("code",
                        visitTanhExpression((SmallPearlParser.TanhExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.FitExpressionContext) {
                st.add("code",
                        visitFitExpression((SmallPearlParser.FitExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.ExponentiationExpressionContext) {
                st.add("code",
                        visitExponentiationExpression((SmallPearlParser.ExponentiationExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.AbsExpressionContext) {
                st.add("code",
                        visitAbsExpression((SmallPearlParser.AbsExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.SizeofExpressionContext) {
                st.add("code",
                        visitSizeofExpression((SmallPearlParser.SizeofExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.EntierExpressionContext) {
                st.add("code",
                        visitEntierExpression((SmallPearlParser.EntierExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.RoundExpressionContext) {
                st.add("code",
                        visitRoundExpression((SmallPearlParser.RoundExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.SignExpressionContext) {
                st.add("code",
                        visitSignExpression((SmallPearlParser.SignExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.RemainderExpressionContext) {
                st.add("code",
                        visitRemainderExpression((SmallPearlParser.RemainderExpressionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.NowFunctionContext) {
                st.add("code",
                        visitNowFunction((SmallPearlParser.NowFunctionContext) ctx));
            } else if (ctx instanceof SmallPearlParser.AndExpressionContext) {
                st.add("code",
                        visitAndExpression(((SmallPearlParser.AndExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.OrExpressionContext) {
                st.add("code",
                        visitOrExpression(((SmallPearlParser.OrExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.ExorExpressionContext) {
                st.add("code",
                        visitExorExpression(((SmallPearlParser.ExorExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.CshiftExpressionContext) {
                st.add("code",
                        visitCshiftExpression(((SmallPearlParser.CshiftExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.ShiftExpressionContext) {
                st.add("code",
                        visitShiftExpression(((SmallPearlParser.ShiftExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.CatExpressionContext) {
                st.add("code",
                        visitCatExpression(((SmallPearlParser.CatExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.NotExpressionContext) {
                st.add("code",
                        visitNotExpression(((SmallPearlParser.NotExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.TOFIXEDExpressionContext) {
                st.add("code",
                        visitTOFIXEDExpression(((SmallPearlParser.TOFIXEDExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.TOFLOATExpressionContext) {
                st.add("code",
                        visitTOFLOATExpression(((SmallPearlParser.TOFLOATExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.TOBITExpressionContext) {
                st.add("code",
                        visitTOBITExpression(((SmallPearlParser.TOBITExpressionContext) ctx)));
            } else if (ctx instanceof SmallPearlParser.TaskFunctionContext) {
                st.add("code",
                        visitTaskFunction(((SmallPearlParser.TaskFunctionContext) ctx)));
            }
        }

        return st;
    }

    @Override
    public ST visitTaskFunction(SmallPearlParser.TaskFunctionContext ctx) {
       ST stmt = m_group.getInstanceOf("taskAddress");
       ASTAttribute attr = m_ast.lookup(ctx);
       SymbolTableEntry se = attr.getSymbolTableEntry();
       if (se != null) {
         stmt.add("taskName", getUserVariable(se.getName()));
       }
       return stmt;  
    }

    @Override
    public ST visitPrioFunction(SmallPearlParser.PrioFunctionContext ctx) {
       ST stmt = m_group.getInstanceOf("taskPrio");
       ASTAttribute attr = m_ast.lookup(ctx);
       SymbolTableEntry se = attr.getSymbolTableEntry();
       if (se != null) {
         stmt.add("taskName", getUserVariable(se.getName()));
       }
       return stmt;  
    }

    @Override
    public ST visitStatement(SmallPearlParser.StatementContext ctx) {
        ST stmt = m_group.getInstanceOf("statement");
        //String s = ctx.getText();

        stmt.add("srcFilename", m_sourceFileName);
        stmt.add("srcLine", ctx.start.getLine());
        stmt.add("srcColumn", ctx.start.getCharPositionInLine());
        m_tempVariableList.add(m_group.getInstanceOf("TempVariableList"));
        m_tempVariableNbr.add(new Integer(0));

        if (ctx != null) {
            if (ctx.label_statement() != null) {
                for (int i = 0; i < ctx.label_statement().size(); i++) {
                    stmt.add("label",
                            visitLabel_statement(ctx.label_statement(i)));
                }
            }

            if (ctx.children != null) {
                for (ParseTree c : ctx.children) {
                    if (c instanceof SmallPearlParser.Unlabeled_statementContext) {
                        stmt.add(
                                "code",
                                visitUnlabeled_statement((SmallPearlParser.Unlabeled_statementContext) c));
                    } else if (c instanceof SmallPearlParser.Block_statementContext) {
                        stmt.add(
                                "code",
                                visitBlock_statement((SmallPearlParser.Block_statementContext) c));
                    } else if (c instanceof SmallPearlParser.Cpp_inlineContext) {
                        stmt.add(
                                "cpp",
                                visitCpp_inline((SmallPearlParser.Cpp_inlineContext) c));
                    }
                }
            }
        }
        if (m_tempVariableNbr.lastElement() > 0) {
            stmt.add("tempVariableList", m_tempVariableList.lastElement());
        }
        m_tempVariableList.remove(m_tempVariableList.size() - 1);
        m_tempVariableNbr.remove(m_tempVariableNbr.size() - 1); 
        return stmt;
    }

    @Override
    public ST visitLabel_statement(SmallPearlParser.Label_statementContext ctx) {
        ST st = m_group.getInstanceOf("label_statement");
        st.add("label", ctx.ID().getText());
        return st;
    }

    @Override
    public ST visitGotoStatement(SmallPearlParser.GotoStatementContext ctx) {
        ST st = m_group.getInstanceOf("goto_statement");
        st.add("label", ctx.ID().getText());
        return st;
    }

    @Override
    public ST visitUnlabeled_statement(
            SmallPearlParser.Unlabeled_statementContext ctx) {
        ST statement = m_group.getInstanceOf("statement");

        if (ctx.empty_statement() != null) {
            statement.add("code", visitEmpty_statement(ctx.empty_statement()));
        } else if (ctx.assignment_statement() != null) {
            statement.add("code",
                    visitAssignment_statement(ctx.assignment_statement()));
        } else if (ctx.sequential_control_statement() != null) {
            statement.add("code", visitSequential_control_statement(ctx
                    .sequential_control_statement()));
        } else if (ctx.realtime_statement() != null) {
            statement.add("code",
                    visitRealtime_statement(ctx.realtime_statement()));
        } else if (ctx.interrupt_statement() != null) {
            statement.add("code",
                    visitInterrupt_statement(ctx.interrupt_statement()));
        } else if (ctx.io_statement() != null) {
            statement.add("code", visitIo_statement(ctx.io_statement()));
        } else if (ctx.callStatement() != null) {
            statement.add("code", visitCallStatement(ctx.callStatement()));
        } else if (ctx.returnStatement() != null) {
            statement.add("code", visitReturnStatement(ctx.returnStatement()));
        } else if (ctx.loopStatement() != null) {
            statement.add("code", visitLoopStatement(ctx.loopStatement()));
        } else if (ctx.exitStatement() != null) {
            statement.add("code", visitExitStatement(ctx.exitStatement()));
        } else if (ctx.gotoStatement() != null) {
            statement.add("code", visitGotoStatement(ctx.gotoStatement()));
        } else if (ctx.convertStatement() != null) {
            statement
                    .add("code", visitConvertStatement(ctx.convertStatement()));
        }

        return statement;
    }

    @Override
    public ST visitSequential_control_statement(
            SmallPearlParser.Sequential_control_statementContext ctx) {
        ST statement = m_group.getInstanceOf("statement");

        if (ctx.if_statement() != null) {
            statement.add("code", visitIf_statement(ctx.if_statement()));
        } else if (ctx.case_statement() != null) {
            statement.add("code", visitCase_statement(ctx.case_statement()));
        }

        return statement;
    }

    @Override
    public ST visitIf_statement(SmallPearlParser.If_statementContext ctx) {
        ST stmt = m_group.getInstanceOf("if_statement");

        ST cast = m_group.getInstanceOf("CastBitToBoolean");
        cast.add("name", visitAndDereference(ctx.expression()));
        stmt.add("rhs", cast);

        if (ctx.then_block() != null) {
            stmt.add("then_block", visitThen_block(ctx.then_block()));
        }

        if (ctx.else_block() != null) {
            stmt.add("else_block", visitElse_block(ctx.else_block()));
        }

        return stmt;
    }


    @Override
    public ST visitElse_block(SmallPearlParser.Else_blockContext ctx) {
        ST statement = m_group.getInstanceOf("statement");

        int i;
        for (i = 0; i < ctx.statement().size(); i++) {
            statement.add("code", visitStatement(ctx.statement(i)));
        }

        return statement;
    }

    @Override
    public ST visitThen_block(SmallPearlParser.Then_blockContext ctx) {
        ST statement = m_group.getInstanceOf("statement");

        int i;
        for (i = 0; i < ctx.statement().size(); i++) {
            statement.add("code", visitStatement(ctx.statement(i)));
        }

        return statement;
    }

    @Override
    public ST visitCase_statement(SmallPearlParser.Case_statementContext ctx) {
        ST st = m_group.getInstanceOf("CaseStatement");

        if (ctx.case_statement_selection1() != null) {
            st.add("casestatement1", visitCase_statement_selection1(ctx
                    .case_statement_selection1()));
        } else if (ctx.case_statement_selection2() != null) {
            st.add("casestatement2", visitCase_statement_selection2(ctx
                    .case_statement_selection2()));
        }

        return st;
    }

    @Override
    public ST visitCase_statement_selection1(
            SmallPearlParser.Case_statement_selection1Context ctx) {
        ST st = m_group.getInstanceOf("CaseStatement1");
        ST st_alt = m_group.getInstanceOf("CaseAlternatives");

        st.add("expression", visitAndDereference(ctx.expression()));

        for (int i = 0; i < ctx.case_statement_selection1_alt().size(); i++) {
            SmallPearlParser.Case_statement_selection1_altContext alt = ctx
                    .case_statement_selection1_alt(i);

            ST cur_alt = visitCase_statement_selection1_alt(alt);
            cur_alt.add("alt", i + 1);
            st_alt.add("Alternatives", cur_alt);
        }

        st.add("alternatives", st_alt);

        if (ctx.case_statement_selection_out() != null) {
            st.add("out", visitCase_statement_selection_out(ctx
                    .case_statement_selection_out()));
        }

        return st;
    }

    @Override
    public ST visitCase_statement_selection1_alt(
            SmallPearlParser.Case_statement_selection1_altContext ctx) {
        ST st = m_group.getInstanceOf("CaseAlternative");

        for (int i = 0; i < ctx.statement().size(); i++) {
            st.add("statements", visitStatement(ctx.statement(i)));
        }

        return st;
    }

    @Override
    public ST visitCase_statement_selection_out(
            SmallPearlParser.Case_statement_selection_outContext ctx) {
        ST st = m_group.getInstanceOf("CaseOut");

        for (int i = 0; i < ctx.statement().size(); i++) {
            st.add("statements", visitStatement(ctx.statement(i)));
        }

        return st;
    }

    @Override
    public ST visitCase_statement_selection2(
            SmallPearlParser.Case_statement_selection2Context ctx) {
        ST st = m_group.getInstanceOf("CaseStatement2");
        ST st_alt = m_group.getInstanceOf("CaseAlternatives");

        st.add("expression", visitAndDereference(ctx.expression()));

        for (int i = 0; i < ctx.case_statement_selection2_alt().size(); i++) {
            SmallPearlParser.Case_statement_selection2_altContext alt = ctx
                    .case_statement_selection2_alt(i);
            ST cur_alt = visitCase_statement_selection2_alt(alt);
            st_alt.add("Alternatives", cur_alt);
        }

        st.add("alternatives", st_alt);

        if (ctx.case_statement_selection_out() != null) {
            st.add("out", visitCase_statement_selection_out(ctx
                    .case_statement_selection_out()));
        }

        return st;
    }

    @Override
    public ST visitCase_statement_selection2_alt(
            SmallPearlParser.Case_statement_selection2_altContext ctx) {
        ST st = m_group.getInstanceOf("CaseAlternative2");

        st.add("alts", visitCase_list(ctx.case_list()));

        for (int i = 0; i < ctx.statement().size(); i++) {
            st.add("statements", visitStatement(ctx.statement(i)));
        }

        return st;
    }

    @Override
    public ST visitCase_list(SmallPearlParser.Case_listContext ctx) {
        ST st = m_group.getInstanceOf("CaseIndexList");

        for (int i = 0; i < ctx.index_section().size(); i++) {
            SmallPearlParser.Index_sectionContext index = ctx.index_section(i);

            if (index.constantFixedExpression().size() == 1) {
                // not found proper
                // solution yet :-(
                ST st_index = m_group.getInstanceOf("CaseIndex");

                ConstantValue value = m_constantExpressionEvaluatorVisitor
                        .lookup(index.constantFixedExpression(0));

                if (value == null || !(value instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }

                st_index.add("index", ((ConstantFixedValue) value).getValue());
                st.add("indices", st_index);
            } else if (index.constantFixedExpression().size() == 2) {
                boolean old_map_to_const = m_map_to_const; // very ugly, but did
                // not found proper
                // solution yet :-(

                ST st_range = m_group.getInstanceOf("CaseRange");

                m_map_to_const = false;

                ConstantValue from = m_constantExpressionEvaluatorVisitor
                        .lookup(index.constantFixedExpression(0));

                if (from == null || !(from instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }

                ConstantValue to = m_constantExpressionEvaluatorVisitor
                        .lookup(index.constantFixedExpression(1));

                if (to == null || !(to instanceof ConstantFixedValue)) {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }

                st_range.add("from", ((ConstantFixedValue) from).getValue());
                st_range.add("to", ((ConstantFixedValue) to).getValue());

                m_map_to_const = old_map_to_const;

                st.add("indices", st_range);
            }
        }

        return st;
    }

    @Override
    public ST visitEmpty_statement(SmallPearlParser.Empty_statementContext ctx) {
        ST statement = m_group.getInstanceOf("empty_statement");
        return statement;
    }
    
    private ST lhs4BitSelection( SmallPearlParser.Assignment_statementContext ctx, Boolean derefLhs) {
      ST st = m_group.getInstanceOf("AssignmentStatementBitSlice");
      
      if (derefLhs) {
        ST deref = m_group.getInstanceOf("CONT");
        deref.add("operand", visitName(ctx.name()));
        st.add("id", deref);
      } else {
        st.add("id", visitName(ctx.name()));
      }
       
      SmallPearlParser.BitSelectionSliceContext c = ctx.bitSelectionSlice();
      ASTAttribute attr = m_ast.lookup(ctx.bitSelectionSlice());
      if (attr.getConstantSelection() != null) {
        st.add("lwb", attr.getConstantSelection().getLowerBoundary());
        st.add("upb", attr.getConstantSelection().getUpperBoundary());
      } else {
        st.add("lwb", visitAndDereference(c.expression(0)));
        if (c.expression(1) == null) {
          st.add("upb", st.getAttribute("lwb"));
        } else {
          ST upb = m_group.getInstanceOf("expression");
          upb.add("code", visitAndDereference(c.expression(1)));
          TerminalNode intConst = ctx.bitSelectionSlice().IntegerConstant();

          if (intConst != null) {
            upb.add("code", "+");
            upb.add("code", "(pearlrt::Fixed<15>)" + intConst.getText());
          }
          st.add("upb", upb);
        }

      }
      return st;    
    }
    
    private ST lhs4CharSelection( SmallPearlParser.Assignment_statementContext ctx, Boolean derefLhs) {
      ST st = m_group.getInstanceOf("Assign2CharSelection");

      if (derefLhs) {
        ST deref = m_group.getInstanceOf("CONT");
        deref.add("operand", visitName(ctx.name()));
        st.add("id", deref);
      } else {
        st.add("id", visitName(ctx.name()));
      }

      ASTAttribute attr = m_ast.lookup(ctx.charSelectionSlice());

      SmallPearlParser.CharSelectionSliceContext c = ctx
          .charSelectionSlice();
      ST slice = m_group.getInstanceOf("GetCharSelection");
      if (attr.getConstantSelection() != null) {
        slice.add("lwb", attr.getConstantSelection().getLowerBoundary());
        slice.add("upb", attr.getConstantSelection().getUpperBoundary());
      } else {
        slice.add("lwb", visitAndDereference(c.expression(0)));
        if (c.expression(1) != null) {
          slice.add("upb", visitAndDereference(c.expression(1)));
        } else {
          slice.add("upb", slice.getAttribute("lwb"));
        }
        if (c.IntegerConstant() != null) {
          slice.add("offset", c.IntegerConstant().getText());
        }

      }
      st.add("lhs", slice);

      ST setCharSelection = m_group
          .getInstanceOf("SetCharSelection");

      setCharSelection.add("expr",
          visitAndDereference(ctx.expression()));
      st.add("lhs", setCharSelection);
      return st;
    }

    @Override
    public ST visitAssignment_statement(
        SmallPearlParser.Assignment_statementContext ctx) {
      ST stmt = null;
      String s = ctx.getText();
      ST lhs = visit(ctx.name());  
      Boolean derefLhs = false;
      ASTAttribute attrLhs = m_ast.lookup(ctx.name());
      ASTAttribute attrRhs = m_ast.lookup(ctx.expression());
      
      
      ErrorStack.enter(ctx, ":=");
      
      stmt = m_group.getInstanceOf("assignment_statement");

      if (ctx.dereference() != null) {
        derefLhs = true;
      }
      
      // check if we have a deref and/or a type Reference
      if (ctx.bitSelectionSlice()!= null) {
        ST bitSel = lhs4BitSelection(ctx,derefLhs);
        bitSel.add("rhs", visitAndDereference(ctx.expression()));
        stmt.add("lhs",bitSel);
        } else if (ctx.charSelectionSlice()!= null) {
        stmt.add("lhs", lhs4CharSelection(ctx,derefLhs));
        // rhs is already treated 
      } else if (attrLhs.getType() instanceof TypeReference && derefLhs == false){
        // we have a reference assignment; rhs may only be a name
         stmt.add("lhs",lhs);
         stmt.add("rhs", visit(ctx.expression()));
      } else if (attrLhs.getType() instanceof TypeChar &&
          attrRhs.getType() instanceof TypeVariableChar ) {
        stmt = m_group.getInstanceOf("AssignCharSelection2Char");
        // assign a TypeVariableChar to a Char<S> is very special
        // we must convert the Char<s> into a CharSlice and set the rhs-slice
        // via .setSlice -> CharSlice(lhs).setSlice(<rhs>);
        stmt.add("id", visit(ctx.name()));
        stmt.add("expr", visitAndDereference(ctx.expression()));
        
      } else {
        if (derefLhs) {
          ST deref = m_group.getInstanceOf("CONT");
          deref.add("operand", lhs);
          stmt.add("lhs", deref);
        } else {
          stmt.add("lhs",lhs);
        }
        stmt.add("rhs", visitAndDereference(ctx.expression()));
      }
      ErrorStack.leave();
      return stmt;
    }

/*
    @Override
    public ST visitAssignment_statement(
            SmallPearlParser.Assignment_statementContext ctx) {
        ST stmt = null;
        String id = null;

        ErrorStack.enter(ctx, ":=");

        if (ctx.name() != null) {
            id = ctx.name().ID().getText();
        } else if (ctx.stringSelection() != null) {
            if (ctx.stringSelection().charSelection() != null) {
                id = ctx.stringSelection().charSelection().name().ID().getText();
            } else if (ctx.stringSelection().bitSelection() != null) {
                id = ctx.stringSelection().bitSelection().name().ID().getText();
            }
        }
        SymbolTableEntry entry = m_currentSymbolTable.lookup(id);

        if (entry == null) {
            m_currentSymbolTable.dump();
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        TypeDefinition rhs_type = m_ast.lookupType(ctx.expression());

        if (entry instanceof VariableEntry) {
            VariableEntry variable = (VariableEntry) entry;
            TypeDefinition lhs_type = variable.getType();

            if (lhs_type instanceof TypeFixed) {
                TypeFixed typ = (TypeFixed) (lhs_type);
                m_currFixedLength = typ.getPrecision();
            } else {
                m_currFixedLength = null;
            }

            if (lhs_type instanceof TypeReference) {
                ST st = m_group.getInstanceOf("assignment_statement");

                if (ctx.dereference() != null) {
// CONT ref2CHAR := c.CHAR(a:b); not treated yet!

                    ST dereference = m_group.getInstanceOf("CONT");
                    dereference.add("operand", getUserVariable(ctx.name().ID()
                            .getText()));
                    st.add("lhs", dereference);
                    st.add("rhs", visitAndDereference(ctx.expression()));//getExpression(ctx.expression()));
                    stmt = st;
                } else {
                  // if lhb.basetype instance of TypeArraySpcification and rhyType is TypeArray
                  //    rhs must create an RefArray(arrayDescriptor, arrayData)
                  // // if lhs and rhs are  of TypeArraySpcification 
                  //    it is a simple assignment
                  TypeReference tr = (TypeReference)lhs_type;
                  if (tr.getBaseType() instanceof TypeArraySpecification) {
                    if (lhs_type.equals(rhs_type)) {
                      st.add("lhs",getUserVariable(ctx.name().ID().getText()));
                      st.add("rhs", visit(ctx.expression()));
                      stmt=st;
                    } else {
                    TypeArraySpecification tas = (TypeArraySpecification)tr.getBaseType();
                    if (ctx.name().listOfExpression()==null) {
                       ASTAttribute attr = m_ast.lookup(ctx.expression());
                       VariableEntry ve = attr.getVariable();
                       st.add("lhs", getUserVariable(ctx.name().ID().getText()));
                       if (ve != null) {
                         ST arrayRef = m_group.getInstanceOf("arrayReference");
                    
                         arrayRef.add("basetype", tas.getBaseType().toST(m_group));  
                         arrayRef.add("descriptor", getArrayDescriptor(ve));
                         arrayRef.add("data", "data_"+attr.getVariable().getName());
                         st.add("rhs", arrayRef);
                       } else {
                         st.add("rhs",  visit(ctx.expression()));
                       }
                       stmt = st;
                    } else {
                      // we have indices!
                      // treatment of references missing;
                      // treatment of a(i) := c.CHAR(c:d);  missing
                      
                      ST array = m_group.getInstanceOf("RefArrayReadWrite");
                      array.add("name", variable.getName());
                      ST indices = m_group.getInstanceOf("ArrayIndices");

                      indices.add("indices", visitListOfExpression(ctx.name().listOfExpression()));
                      array.add("indices", indices);
                      st.add("lhs", array);
                      st.add("rhs", visitAndDereference(ctx.expression()));
                      stmt = st; 
                    }
                    }
                  } else {
                    st.add("lhs", getUserVariable(ctx.name().ID().getText()));
                    
                    if (rhs_type instanceof TypeReference) {
                      st.add("rhs", visit(ctx.expression())); //getExpression(ctx.expression()));
                    } else {
                        st.add("rhs", getReferenceExpression(ctx.expression()));
                    }
                    stmt = st;
                  }
                }
            } else {
                if (lhs_type instanceof TypeArray) {
                    // treatment of references missing;
                    // treatment of a(i) := c.CHAR(c:d);  missing
                    ST st = m_group.getInstanceOf("assignment_statement");
                    ST array = m_group.getInstanceOf("ArrayLHS");

                    array.add("descriptor", getArrayDescriptor(variable));

                    array.add("name", variable.getName());
                    ST indices = m_group.getInstanceOf("ArrayIndices");

                    indices.add("indices", visitListOfExpression(ctx.name().listOfExpression()));
                    array.add("indices", indices);
                    st.add("lhs", array);
                    st.add("rhs", visitAndDereference(ctx.expression()));
                    stmt = st;
                } else {
                    if (ctx.stringSelection() != null) {
                        if (ctx.stringSelection().charSelection() != null) {

                            ST st = m_group
                                    .getInstanceOf("Assign2CharSelection");

                            st.add("id", getUserVariable(id));
                            ASTAttribute attr = m_ast.lookup(ctx.stringSelection());

                            SmallPearlParser.CharSelectionContext c = ctx
                                    .stringSelection().charSelection();
                            ST slice = m_group.getInstanceOf("GetCharSelection");
                            if (attr.getConstantSelection() != null) {
                                slice.add("lwb", attr.getConstantSelection().getLowerBoundary());
                                slice.add("upb", attr.getConstantSelection().getUpperBoundary());
                            } else {
                                slice.add("lwb", visitAndDereference(c.charSelectionSlice().expression(0)));
                                if (c.charSelectionSlice().expression(1) != null) {
                                    slice.add("upb", visitAndDereference(c.charSelectionSlice().expression(1)));
                                } else {
                                    slice.add("upb", slice.getAttribute("lwb"));
                                }
                                if (c.charSelectionSlice().IntegerConstant() != null) {
                                    slice.add("offset", c.charSelectionSlice().IntegerConstant().getText());
                                }

                            }
                            st.add("lhs", slice);

                            ST setCharSelection = m_group
                                    .getInstanceOf("SetCharSelection");

                            setCharSelection.add("expr",
                                visitAndDereference(ctx.expression()));
                            st.add("lhs", setCharSelection);
                            stmt = st;
                        } else if (ctx.stringSelection().bitSelection() != null) {
                            ST st = m_group.getInstanceOf("AssignmentStatementBitSlice");

                            st.add("id", getUserVariable(id));
                            SmallPearlParser.BitSelectionSliceContext c = ctx.stringSelection().bitSelection().bitSelectionSlice();
                            ASTAttribute attr = m_ast.lookup(ctx.stringSelection().bitSelection());
                            if (attr.getConstantSelection() != null) {
                                st.add("lwb", attr.getConstantSelection().getLowerBoundary());
                                st.add("upb", attr.getConstantSelection().getUpperBoundary());
                            } else {
                                st.add("lwb", visitAndDereference(c.expression(0)));
                                if (c.expression(1) == null) {
                                    st.add("upb", st.getAttribute("lwb"));
                                } else {
                                    ST upb = m_group.getInstanceOf("expression");
                                    upb.add("code", visitAndDereference(c.expression(1)));
                                    TerminalNode intConst = ctx.stringSelection().bitSelection().bitSelectionSlice().IntegerConstant();

                                    if (intConst != null) {
                                        upb.add("code", "+");
                                        upb.add("code", "(pearlrt::Fixed<15>)" + intConst.getText());
                                    }
                                    st.add("upb", upb);
                                }
                            }

                            st.add("rhs",
                                visitAndDereference(ctx.expression()));

                            stmt = st;
                        } else {
                            ErrorStack.addInternal("missing alternative in CppCoeGeneratorVisitot::visitAssignment");
                        }
                    } else {
                        if (lhs_type instanceof TypeStructure) {
                            ST st = m_group.getInstanceOf("assignment_statement");
                            Log.debug("Structure on LHS");
                            st.add("lhs", traverseNameForStruct(ctx.name(), lhs_type));
                            st.add("rhs", visitAndDereference(ctx.expression()));
                            stmt = st;
                        }
                        else
                        if (rhs_type instanceof TypeVariableChar) {
                            ST st = m_group.getInstanceOf("AssignCharSelection2Char");
                            // assign a TypeVariableChar to a Char<S> is very special
                            // we must convert the Char<s> into a CharSlice and set the rhs-slice
                            // via .setSlice -> CharSlice(lhs).setSlice(<rhs>);
                            st.add("id", getUserVariable(id));
                            st.add("expr", visitAndDereference(ctx.expression()));
                            stmt = st;
                        } else {
                            ST st = m_group.getInstanceOf("assignment_statement");
                            st.add("lhs", getUserVariable(id));
                            //st.add("rhs", getExpression(ctx.expression()));
                            st.add("rhs", visitAndDereference(ctx.expression()));
                            stmt = st;
                        }
                    }
                }
            }
        } else {
            ErrorStack.addInternal("no variable entry found");
        }

        m_currFixedLength = null;
        ErrorStack.leave();
        return stmt;
    }
*/

    @Override
    public ST visitListOfExpression(SmallPearlParser.ListOfExpressionContext ctx) {
        ST indices = m_group.getInstanceOf("ArrayIndices");

        for (int i = 0; i < ctx.expression().size(); i++) {
            ST stIndex = m_group.getInstanceOf("ArrayIndex");
            stIndex.add("index", visitAndDereference(ctx.expression(i)));
            indices.add("indices", stIndex);
        }

        return indices;
    }

    @Override
    public ST visitRealtime_statement(
            SmallPearlParser.Realtime_statementContext ctx) {
        ST statement = m_group.getInstanceOf("statement");

        if (ctx.task_control_statement() != null) {
            statement.add("code",
                    visitTask_control_statement(ctx.task_control_statement()));
        } else if (ctx.task_coordination_statement() != null) {
            statement.add("code", visitTask_coordination_statement(ctx
                    .task_coordination_statement()));
        }

        return statement;
    }

    @Override
    public ST visitBaseExpression(SmallPearlParser.BaseExpressionContext ctx) {
        ST expression = m_group.getInstanceOf("expression");

        if (ctx.primaryExpression() != null) {
            expression.add("code",
                    visitPrimaryExpression(ctx.primaryExpression()));
        }

        return expression;
    }

    private String getBitStringLiteral(String literal) {
        return CommonUtils.convertBitStringToLong(literal).toString();
    }


    @Override
    public ST visitPrimaryExpression(
            SmallPearlParser.PrimaryExpressionContext ctx) {
        ST expression = m_group.getInstanceOf("expression");

        if (ctx.literal() != null) {
            expression.add("code", visitLiteral(ctx.literal()));
        } else if (ctx.name() != null) {
          //ASTAttribute attr = m_ast.lookup(ctx.name());
            expression.add("code", visitName(ctx.name()));
        } else if (ctx.semaTry() != null) {
            expression.add("code", visitSemaTry(ctx.semaTry()));
        } else if (ctx.stringSelection() != null) {
            expression.add("code", visitStringSelection(ctx.stringSelection()));
        } else if (ctx.expression() != null) {
            expression.add("code", "(");
// or ctx.expression(0) ???
            expression.add("code", visitAndDereference(ctx.expression()));
            expression.add("code", ")");
//        } else if (ctx.stringSlice() != null) {
//            expression.add("code", visitStringSlice(ctx.stringSlice()));
        }
        return expression;
    }

    @Override
    public ST visitName(SmallPearlParser.NameContext ctx) {
        ST expression = m_group.getInstanceOf("expression");
        ASTAttribute attr = m_ast.lookup(ctx);
        SymbolTableEntry entry = m_currentSymbolTable.lookup(ctx.ID()
                .getText());

        if (entry instanceof org.smallpearl.compiler.SymbolTable.ProcedureEntry) {
            ST functionCall = m_group.getInstanceOf("FunctionCall");

            if (attr.isFunctionCall() || 
                (ctx.listOfExpression() != null && ctx.listOfExpression().expression().size() > 0)) {
              functionCall.add("callee", getUserVariable(ctx.ID().getText()));

              if (ctx.listOfExpression() != null && 
                  ctx.listOfExpression().expression().size() > 0) {

                functionCall.add("ListOfActualParameters",
                      getActualParameters(ctx.listOfExpression().expression()));
              }
              expression.add("functionCall", functionCall);
            } else {
              // only name of a procedure
              expression.add("id",getUserVariable(ctx.ID().getText()));
            }

        } else if (entry instanceof VariableEntry) {
            VariableEntry variable = (VariableEntry) entry;


            if (attr.isFunctionCall()) {
              // code for dereference an function call missing!
              // code: bit15 = refFunctionWithBitResult;
              ErrorStack.addInternal("typeXX := refFunctionReturningTypeXX not implemented yet");
            } else  
            if (variable.getType() instanceof TypeArray) {
                ST array = m_group.getInstanceOf("ArrayLHS");

                ParserRuleContext c = variable.getCtx();
                if (c instanceof FormalParameterContext) {
                    array.add("descriptor", "ad_" + variable.getName());
                } else {
                    TypeArray type = (TypeArray) variable.getType();
                    ArrayDescriptor array_descriptor = new ArrayDescriptor(
                            type.getNoOfDimensions(), type.getDimensions());
                    array.add("descriptor", array_descriptor.getName());
                }
                array.add("name", variable.getName());

                // if no indices are given, the complete array is accessed
                if (ctx.listOfExpression() != null) {
                    array.add("indices", getIndices(ctx.listOfExpression().expression()));
                }

                expression.add("id", array);
            } else if (variable.getType() instanceof TypeStructure) {
                if ( ctx.name() != null ) {
                  expression.add("id", traverseNameForStruct(ctx,(TypeStructure) variable.getType()));
                } else {
                    expression.add("id", getUserVariable(variable.getName()));
                }
            } else {
                expression.add("id", getUserVariable(ctx.ID().getText()));
            }
        } else {
            expression.add("id", getUserVariable(ctx.ID().getText()));
        }
        return expression;
    }

    @Override
    public ST visitCharSelection(SmallPearlParser.CharSelectionContext ctx) {
        ASTAttribute attr = m_ast.lookup(ctx);

        if (m_verbose > 0) {
            System.out.println("CppCodeGeneratorVisitor: visitCharSelection");
        }

        ST st = null;
        if (attr.getType() instanceof TypeChar) {
            st = m_group.getInstanceOf("MakeCharacterFromStringSelection");

//            st.add("id", getUserVariable(ctx.name().ID().getText()));
            st.add("id", visitName(ctx.name()));
            st.add("size", attr.getType().getPrecision());

            if (attr.getConstantSelection() != null) {
                st.add("lwb", attr.getConstantSelection().getLowerBoundary());
                st.add("upb", attr.getConstantSelection().getUpperBoundary());
            } else {
                st.add("lwb", visitAndDereference(ctx.charSelectionSlice().expression(0)));
                if (ctx.charSelectionSlice().expression(1) != null) {
                    st.add("upb", visitAndDereference(ctx.charSelectionSlice().expression(1)));
                } else {
                    st.add("upb", visitAndDereference(ctx.charSelectionSlice().expression(0)));
                }
                if (ctx.charSelectionSlice().IntegerConstant() != null) {
                    st.add("offset", ctx.charSelectionSlice().IntegerConstant().getText());
                }
            }
        } else if (attr.getType() instanceof TypeVariableChar) {
            st = m_group.getInstanceOf("RhsCharSelection");

            // st.add("id", getUserVariable(ctx.name().ID().getText()));
            st.add("id", visitName(ctx.name()));

            //expr.add("id", s1);
            st.add("lwb", visitAndDereference(ctx.charSelectionSlice().expression(0)));
            if (ctx.charSelectionSlice().expression(1) != null) {
                st.add("upb", visitAndDereference(ctx.charSelectionSlice().expression(1)));
            } else {
                st.add("upb", visitAndDereference(ctx.charSelectionSlice().expression(0)));
            }
            if (ctx.charSelectionSlice().IntegerConstant() != null) {
                st.add("offset", ctx.charSelectionSlice().IntegerConstant().getText());
            }
        }
        return st;
    }

    @Override
    public ST visitBitSelection(SmallPearlParser.BitSelectionContext ctx) {
        ASTAttribute attr = m_ast.lookup(ctx);

        if (m_verbose > 0) {
            System.out.println("CppCodeGeneratorVisitor: visitCharSelection");
        }

        // we have different situations
        //    for BIT(1) we may use  BitString.getBit
        //    for BIT(2..) we may use BitString.getSlice
        if (attr.getType().getPrecision() == 1) {
            ST st = m_group.getInstanceOf("MakeBitString1FromStringSelection");
            //st.add("id", getUserVariable(ctx.name().ID().getText()));
            st.add("id", visitName(ctx.name()));
            if (attr.getConstantSelection() != null) {
                ASTAttribute a = m_ast.lookup(ctx.bitSelectionSlice().expression(0));
                st.add("lwb", a.getConstant()); //attr.getConstantSelection().getLowerBoundary());
            } else {
                st.add("lwb", visitAndDereference(ctx.bitSelectionSlice().expression(0)));
            }
            return st;
        } else {
            ST st = m_group.getInstanceOf("MakeBitStringNFromStringSelection");

            //st.add("id", getUserVariable(ctx.name().ID().getText()));
            st.add("id", visitName(ctx.name()));

            st.add("size", attr.getType().getPrecision());

            if (attr.getConstantSelection() != null) {
                ASTAttribute a = m_ast.lookup(ctx.bitSelectionSlice().expression(0));
                st.add("lwb", a.getConstant());
            } else {
                st.add("lwb", visitAndDereference(ctx.bitSelectionSlice().expression(0)));
            }
            return st;
        }
    }


    @Override
    public ST visitAdditiveExpression(
            SmallPearlParser.AdditiveExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("expression");

        ASTAttribute attr = m_ast.lookup(ctx);

        if (attr.isReadOnly() && attr.getConstantFixedValue() != null) {
            expr.add("code", attr.getConstantFixedValue());
        } else {
            expr.add("code", visitAndDereference(ctx.expression(0)));
            expr.add("code", ctx.op.getText());
            expr.add("code", visitAndDereference(ctx.expression(1)));
        }
        return expr;
    }

    @Override
    public ST visitSubtractiveExpression(
            SmallPearlParser.SubtractiveExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("expression");

        ASTAttribute attr = m_ast.lookup(ctx);

        if (attr.isReadOnly() && attr.getConstantFixedValue() != null) {
            expr.add("code", attr.getConstantFixedValue());
        } else {
            expr.add("code", visitAndDereference(ctx.expression(0)));
            expr.add("code", ctx.op.getText());
            expr.add("code", visitAndDereference(ctx.expression(1)));
        }
        return expr;
    }

    @Override
    public ST visitMultiplicativeExpression(
            SmallPearlParser.MultiplicativeExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("expression");

        ASTAttribute attr = m_ast.lookup(ctx);

        if (attr.isReadOnly() && attr.getConstantFixedValue() != null) {
            expr.add("code", attr.getConstantFixedValue());
        } else {
            expr.add("code", visitAndDereference(ctx.expression(0)));
            expr.add("code", ctx.op.getText());
            expr.add("code", visitAndDereference(ctx.expression(1)));
        }

        return expr;
    }

    @Override
    public ST visitDivideExpression(SmallPearlParser.DivideExpressionContext ctx) {

        ST expr = m_group.getInstanceOf("DivisionExpression");

        expr.add("lhs", visitAndDereference(ctx.expression(0)));
        addFixedFloatConversion(expr, ctx.expression(0), 0);
        expr.add("rhs", visitAndDereference(ctx.expression(1)));
        addFixedFloatConversion(expr, ctx.expression(1), 1);

        return expr;
    }

    @Override
    public ST visitDivideIntegerExpression(
            SmallPearlParser.DivideIntegerExpressionContext ctx) {
        ST expr = null;

        ASTAttribute attr = m_ast.lookup(ctx);

        if (attr.isReadOnly() && attr.getConstantFixedValue() != null) {
            expr = m_group.getInstanceOf("IntegerConstant");
            expr.add("value", attr.getConstantFixedValue());
        } else {
            expr = m_group.getInstanceOf("FixedDivisionExpression");
            expr.add("lhs", visitAndDereference(ctx.expression(0)));
            expr.add("rhs", visitAndDereference(ctx.expression(1)));
        }

        return expr;
    }

    @Override
    public ST visitUnaryExpression(SmallPearlParser.UnaryExpressionContext ctx) {
        ST st = m_group.getInstanceOf("expression");

        // expr.add( "code", visitAndDereference(ctx.expression(0)));
        // expr.add( "code", ctx.op.getText());
        // expr.add( "code", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitUnaryAdditiveExpression(
            SmallPearlParser.UnaryAdditiveExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("expression");
        expr.add("code", visitAndDereference(ctx.expression()));
        return expr;
    }

    @Override
    public ST visitUnarySubtractiveExpression(
            SmallPearlParser.UnarySubtractiveExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("expression");

        int last_sign = m_sign;
        m_sign = -1;

        if (ctx.getChild(1) instanceof SmallPearlParser.BaseExpressionContext) {
            SmallPearlParser.BaseExpressionContext base_ctx = (SmallPearlParser.BaseExpressionContext) (ctx
                    .getChild(1));

            if (base_ctx.primaryExpression() != null) {
                SmallPearlParser.PrimaryExpressionContext primary_ctx = base_ctx
                        .primaryExpression();

                if (primary_ctx.getChild(0) instanceof SmallPearlParser.LiteralContext) {
                    SmallPearlParser.LiteralContext literal_ctx = (SmallPearlParser.LiteralContext) (primary_ctx
                            .getChild(0));

                    if (literal_ctx.floatingPointConstant() != null) {
                        try {
                            double value = -1
                                    * CommonUtils.getFloatingPointConstantValue(literal_ctx.floatingPointConstant());
                            int precision = CommonUtils.getFloatingPointConstantPrecision(literal_ctx.floatingPointConstant(), m_currentSymbolTable.lookupDefaultFloatLength());

                            ConstantFloatValue float_value = new ConstantFloatValue(
                                    value, precision);
                            expr.add("code", float_value);
                        } catch (NumberFormatException ex) {
                            throw new NumberOutOfRangeException(ctx.getText(),
                                    literal_ctx.start.getLine(),
                                    literal_ctx.start.getCharPositionInLine());
                        }
                    } else if (literal_ctx.fixedConstant() != null) {
                        try {
                            int value = 0;
                            int precision = m_currentSymbolTable
                                    .lookupDefaultFixedLength();

                            if (literal_ctx.fixedConstant()
                                    .fixedNumberPrecision() != null) {
                                precision = Integer.parseInt(literal_ctx
                                        .fixedConstant().fixedNumberPrecision()
                                        .IntegerConstant().toString());
                            }

                            String s = literal_ctx.fixedConstant()
                                    .IntegerConstant().toString();
                            value = -1
                                    * Integer.parseInt(literal_ctx
                                    .fixedConstant().IntegerConstant()
                                    .toString());

                            if (m_map_to_const) {
                                ConstantFixedValue fixed_value = new ConstantFixedValue(
                                        value, precision);
                                expr.add("code", fixed_value);
                            } else {
                                expr.add("code", value);
                            }
                        } catch (NumberFormatException ex) {
                            throw new NumberOutOfRangeException(ctx.getText(),
                                    literal_ctx.start.getLine(),
                                    literal_ctx.start.getCharPositionInLine());
                        }
                    } else if (literal_ctx.durationConstant() != null) {
                        ASTAttribute attr = m_ast.lookup(ctx);

                        if (attr.isReadOnly()
                                && attr.getConstantDurationValue() != null) {
                            expr = m_group.getInstanceOf("DurationConstant");
                            expr.add("value", attr.getConstantDurationValue());
                        } else {
                            expr.add("code", ctx.op.getText());
                            expr.add("code", visitAndDereference(ctx.expression()));
                        }
                    } else if (literal_ctx.timeConstant() != null) {
                        throw new NotYetImplementedException(ctx.getText(),
                                literal_ctx.start.getLine(),
                                literal_ctx.start.getCharPositionInLine(),
                                "-CLOCK not treated");
                    } else {
                        throw new NotYetImplementedException(ctx.getText(),
                                literal_ctx.start.getLine(),
                                literal_ctx.start.getCharPositionInLine(),
                                "untreated type" + literal_ctx.toString());
                    }
                } else {
                    expr.add("code", ctx.op.getText());
                    expr.add("code", visitAndDereference(ctx.expression()));
                }
            }
        }

        m_sign = last_sign;
        return expr;
    }

    @Override
    public ST visitExponentiationExpression(
            SmallPearlParser.ExponentiationExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("Exponentiation");

        expr.add("lhs", visitAndDereference(ctx.expression(0)));
        expr.add("rhs", visitAndDereference(ctx.expression(1)));

        return expr;
    }

    @Override
    public ST visitNotExpression(SmallPearlParser.NotExpressionContext ctx) {
        TypeDefinition typ = m_ast.lookupType(ctx);
        ST expr = null;

        // TODO: bitwise
        if (typ instanceof TypeBit) {
            expr = m_group.getInstanceOf("NotBitwiseExpression");
        } else {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        expr.add("rhs", visitAndDereference(ctx.expression()));

        return expr;
    }

    @Override
    public ST visitAndExpression(SmallPearlParser.AndExpressionContext ctx) {
        TypeDefinition typ = m_ast.lookupType(ctx);
        ST expr = null;

        if (typ instanceof TypeBit) {
            expr = m_group.getInstanceOf("AndBitwiseExpression");
        } else {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        expr.add("lhs", visitAndDereference(ctx.expression(0)));
        expr.add("rhs", visitAndDereference(ctx.expression(1)));

        return expr;
    }

    @Override
    public ST visitOrExpression(SmallPearlParser.OrExpressionContext ctx) {
        TypeDefinition typ = m_ast.lookupType(ctx);
        ST expr = null;

        if (typ instanceof TypeBit) {
            expr = m_group.getInstanceOf("OrBitwiseExpression");
        } else {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        expr.add("lhs", visitAndDereference(ctx.expression(0)));
        expr.add("rhs", visitAndDereference(ctx.expression(1)));

        return expr;
    }

    @Override
    public ST visitExorExpression(SmallPearlParser.ExorExpressionContext ctx) {
        TypeDefinition typ = m_ast.lookupType(ctx);
        ST expr = null;

        if (typ instanceof TypeBit) {
            expr = m_group.getInstanceOf("ExorBitwiseExpression");
        } else {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        expr.add("lhs", visitAndDereference(ctx.expression(0)));
        expr.add("rhs", visitAndDereference(ctx.expression(1)));

        return expr;
    }

    @Override
    public ST visitCshiftExpression(SmallPearlParser.CshiftExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("CshiftExpression");

        expr.add("lhs", visitAndDereference(ctx.expression(0)));
        expr.add("rhs", visitAndDereference(ctx.expression(1)));

        return expr;
    }

    @Override
    public ST visitShiftExpression(SmallPearlParser.ShiftExpressionContext ctx) {
        ST expr = m_group.getInstanceOf("ShiftExpression");

        expr.add("lhs", visitAndDereference(ctx.expression(0)));
        expr.add("rhs", visitAndDereference(ctx.expression(1)));

        return expr;
    }

    @Override
    public ST visitCatExpression(SmallPearlParser.CatExpressionContext ctx) {
        ST st;

        TypeDefinition resultType = m_ast.lookupType(ctx);

        if (resultType instanceof TypeChar) {
            st = m_group.getInstanceOf("CharCatExpression");

        } else if (resultType instanceof TypeBit) {
            st = m_group.getInstanceOf("BitCatExpression");
        } else {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        st.add("op1", visitAndDereference(ctx.expression(0)));
        st.add("op2", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitLiteral(SmallPearlParser.LiteralContext ctx) {
        ST literal = m_group.getInstanceOf("literal");
        ASTAttribute attr = m_ast.lookup(ctx);

        if (attr == null || attr.m_constant == null) {
            ErrorStack.enter(ctx);
            ErrorStack.addInternal("AST attribute for constant missing type=" + attr.m_type.toString());
            ErrorStack.leave();
            return null;
        }

        if (ctx.durationConstant() != null) {
            literal.add("duration", attr.m_constant);
        } else if (ctx.floatingPointConstant() != null) {
            literal.add("float", attr.getConstantFloatValue());
        } else if (ctx.timeConstant() != null) {
            literal.add("time", attr.getConstant());
        } else if (ctx.StringLiteral() != null) {
            literal.add("string", attr.m_constant);
        } else if (ctx.BitStringLiteral() != null) {
            literal.add("bitstring", attr.m_constant);
        } else if (ctx.fixedConstant() != null) {
            literal.add("integer", attr.m_constant);
        } else if (ctx.referenceConstant() != null) {
          ConstantNILReference nilRef = ConstantPool.lookupConstantNILReference();

          if (nilRef != null) {
             literal.add("nil", nilRef );
          } else {
            ErrorStack.addInternal(ctx,"literal","no NIL symbol in constant pool");
          }
          
        }

        return literal;
    }

    @Override
    public ST visitLwbDyadicExpression(
            SmallPearlParser.LwbDyadicExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ArrayLWB");

        if (ctx.expression(1) instanceof SmallPearlParser.BaseExpressionContext) {
            SmallPearlParser.BaseExpressionContext expr = (SmallPearlParser.BaseExpressionContext) ctx
                    .expression(1);
            if (expr.primaryExpression().name().ID() != null) {
                String id = expr.primaryExpression().name().ID().toString();
                SymbolTableEntry entry = m_currentSymbolTable.lookup(id);
                if (entry != null) {
                    if (entry instanceof VariableEntry) {
                        VariableEntry var = (VariableEntry) entry;

                        if (var.getType() instanceof TypeArray) {
                            st.add("descriptor", getArrayDescriptor(var));
                            st.add("index", visitAndDereference(ctx.expression(0))
                                    .render());
                        } else {
                            throw new TypeMismatchException(ctx.getText(),
                                    ctx.start.getLine(),
                                    ctx.start.getCharPositionInLine());
                        }
                    } else {
                        throw new TypeMismatchException(ctx.getText(),
                                ctx.start.getLine(),
                                ctx.start.getCharPositionInLine());
                    }
                } else {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }
            } else {
                throw new TypeMismatchException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        return st;
    }

    @Override
    public ST visitLwbMonadicExpression(
            SmallPearlParser.LwbMonadicExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ArrayLWB");

        if (ctx.expression() instanceof SmallPearlParser.BaseExpressionContext) {
            SmallPearlParser.BaseExpressionContext expr = (SmallPearlParser.BaseExpressionContext) ctx
                    .expression();
            if (expr.primaryExpression().name().ID() != null) {
                String id = expr.primaryExpression().name().ID().toString();
                SymbolTableEntry entry = m_currentSymbolTable.lookup(id);
                if (entry != null) {
                    if (entry instanceof VariableEntry) {
                        VariableEntry var = (VariableEntry) entry;

                        if (var.getType() instanceof TypeArray) {
                            st.add("descriptor", getArrayDescriptor(var));
                            st.add("index", 1);
                        } else {
                            throw new TypeMismatchException(ctx.getText(),
                                    ctx.start.getLine(),
                                    ctx.start.getCharPositionInLine());
                        }
                    } else {
                        throw new TypeMismatchException(ctx.getText(),
                                ctx.start.getLine(),
                                ctx.start.getCharPositionInLine());
                    }
                } else {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }
            } else {
                throw new TypeMismatchException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        return st;
    }

    @Override
    public ST visitUpbDyadicExpression(
            SmallPearlParser.UpbDyadicExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ArrayUPB");

        if (ctx.expression(1) instanceof SmallPearlParser.BaseExpressionContext) {
            SmallPearlParser.BaseExpressionContext expr = (SmallPearlParser.BaseExpressionContext) ctx
                    .expression(1);
            if (expr.primaryExpression().name().ID() != null) {
                String id = expr.primaryExpression().name().ID().toString();
                SymbolTableEntry entry = m_currentSymbolTable.lookup(id);
                if (entry != null) {
                    if (entry instanceof VariableEntry) {
                        VariableEntry var = (VariableEntry) entry;

                        if (var.getType() instanceof TypeArray) {
                            st.add("descriptor", getArrayDescriptor(var));
                            st.add("index", visitAndDereference(ctx.expression(0))
                                    .render());
                        } else {
                            throw new TypeMismatchException(ctx.getText(),
                                    ctx.start.getLine(),
                                    ctx.start.getCharPositionInLine());
                        }
                    } else {
                        throw new TypeMismatchException(ctx.getText(),
                                ctx.start.getLine(),
                                ctx.start.getCharPositionInLine());
                    }
                } else {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }
            } else {
                throw new TypeMismatchException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        return st;
    }

    @Override
    public ST visitUpbMonadicExpression(
            SmallPearlParser.UpbMonadicExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ArrayUPB");

        if (ctx.expression() instanceof SmallPearlParser.BaseExpressionContext) {
            SmallPearlParser.BaseExpressionContext expr = (SmallPearlParser.BaseExpressionContext) ctx
                    .expression();
            if (expr.primaryExpression().name().ID() != null) {
                String id = expr.primaryExpression().name().ID().toString();
                SymbolTableEntry entry = m_currentSymbolTable.lookup(id);
                if (entry != null) {
                    if (entry instanceof VariableEntry) {
                        VariableEntry var = (VariableEntry) entry;

                        if (var.getType() instanceof TypeArray) {
                            st.add("descriptor", getArrayDescriptor(var));
                            st.add("index", 1);
                        } else {
                            throw new TypeMismatchException(ctx.getText(),
                                    ctx.start.getLine(),
                                    ctx.start.getCharPositionInLine());
                        }
                    } else {
                        throw new TypeMismatchException(ctx.getText(),
                                ctx.start.getLine(),
                                ctx.start.getCharPositionInLine());
                    }
                } else {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }
            } else {
                throw new TypeMismatchException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        return st;
    }

    @Override
    public ST visitReturnStatement(SmallPearlParser.ReturnStatementContext ctx) {
      ST stmt = m_group.getInstanceOf("return_statement");

      if (ctx.expression() != null) {
        ASTAttribute attr = m_ast.lookup(ctx.expression());

        if (attr.getType() instanceof TypeVariableChar) {
          stmt.add("char_size", m_resultType.getPrecision());
        }

        if (attr.getType() instanceof TypeReference) {
          if (m_resultType instanceof TypeReference) {
            stmt.add("expression",  visit(ctx.expression()));
          } else {
            stmt.add("expression", visitAndDereference(ctx.expression()));
          }
        } else {   
          if (m_resultType instanceof TypeReference) {
            ST ref = m_group.getInstanceOf("referenceOf");
            ref.add("obj", visitAndDereference(ctx.expression()));
            stmt.add("expression", ref);
          } else {
            stmt.add("expression", visitAndDereference(ctx.expression()));
          }
        }
      }
      return stmt;
    }

    @Override
    public ST visitTask_control_statement(
            SmallPearlParser.Task_control_statementContext ctx) {
        ST stmt = m_group.getInstanceOf("task_control_statement");

        if (ctx.taskStart() != null) {
            stmt.add("code", visitTaskStart(ctx.taskStart()));
        } else if (ctx.task_terminating() != null) {
            stmt.add("code", visitTask_terminating(ctx.task_terminating()));
        } else if (ctx.task_suspending() != null) {
            stmt.add("code", visitTask_suspending(ctx.task_suspending()));
        } else if (ctx.taskContinuation() != null) {
            stmt.add("code", visitTaskContinuation(ctx.taskContinuation()));
        } else if (ctx.taskResume() != null) {
            stmt.add("code", visitTaskResume(ctx.taskResume()));
        } else if (ctx.task_preventing() != null) {
            stmt.add("code", visitTask_preventing(ctx.task_preventing()));
        }

        return stmt;
    }

    @Override
    public ST visitTaskStart(SmallPearlParser.TaskStartContext ctx) {
        ST st = m_group.getInstanceOf("task_start");

        st.add("task", visitAndDereference(ctx.name())); //ctx.ID().toString());

        startConditionToST(st, ctx.startCondition());

        if (ctx.frequency() != null) {
            SmallPearlParser.FrequencyContext c = ctx.frequency();
            st.add("Condition", "ALL");
            st.add("all", visitAndDereference(c.expression(0)));

            for (int i = 0; i < c.getChildCount(); i++) {
                if (c.getChild(i) instanceof TerminalNodeImpl) {
                    if (((TerminalNodeImpl) c.getChild(i)).getSymbol()
                            .getText().equals("UNTIL")) {
                        st.add("Condition", "UNTIL");
                        st.add("until", visitAndDereference(c.expression(1)));
                    } else if (((TerminalNodeImpl) c.getChild(i)).getSymbol()
                            .getText().equals("DURING")) {
                        st.add("Condition", "DURING");
                        st.add("during", visitAndDereference(c.expression(1)));
                    }
                }
            }
        }

        if (ctx.priority() != null) {
            st.add("Condition", "PRIO");
            st.add("priority", visitAndDereference(ctx.priority().expression()));
        }

        return st;
    }

    @Override
    public ST visitTask_terminating(SmallPearlParser.Task_terminatingContext ctx) {
        ST stmt = m_group.getInstanceOf("task_terminate");

        if (ctx.name() != null) {
            stmt.add("task", visitAndDereference(ctx.name()));
        }

        return stmt;
    }

    @Override
    public ST visitTask_suspending(SmallPearlParser.Task_suspendingContext ctx) {
        ST stmt = m_group.getInstanceOf("task_suspend");

        if (ctx.name() != null) {
          stmt.add("task", visitAndDereference(ctx.name()));
        }

        return stmt;
    }

    @Override
    public ST visitTaskContinuation(SmallPearlParser.TaskContinuationContext ctx) {
        ST stmt = m_group.getInstanceOf("TaskContinuation");

        if (ctx.name() != null) {
          stmt.add("task", visitAndDereference(ctx.name()));
        }

        startConditionToST(stmt, ctx.startCondition());


        if (ctx.priority() != null) {
            stmt.add("Condition", "PRIO");
            stmt.add("priority", visitAndDereference(ctx.priority().expression()));
        }

        return stmt;
    }

    @Override
    public ST visitTaskResume(SmallPearlParser.TaskResumeContext ctx) {
        ST st = m_group.getInstanceOf("TaskResume");
        
        startConditionToST(st, ctx.startCondition());
        
        return st;
    }
    
    private void startConditionToST(ST st, StartConditionContext ctx) {
      if (ctx != null) {
        
        if (ctx.startConditionAT() != null) {
          st.add("Condition", "AT");
          st.add("at", visitAndDereference(ctx.startConditionAT().expression()));
        }
        if (ctx.startConditionAFTER() != null) {
          st.add("Condition", "AFTER");
          st.add("after", visitAndDereference(ctx.startConditionAFTER().expression()));
        }
        if (ctx.startConditionWHEN() != null) {
          st.add("Condition", "WHEN");
          st.add("when", visitAndDereference(ctx.startConditionWHEN().name()));
        }
      }
    }

    @Override
    public ST visitTask_preventing(SmallPearlParser.Task_preventingContext ctx) {
        ST stmt = m_group.getInstanceOf("task_prevent");
        
        if (ctx.name() != null) {
          stmt.add("task", visitAndDereference(ctx.name()));
        }
        
        return stmt;
    }

    @Override
    public ST visitTask_coordination_statement(
            SmallPearlParser.Task_coordination_statementContext ctx) {
        ST stmt = m_group.getInstanceOf("TaskCoordinationStatement");

        if (ctx.semaRelease() != null) {
            stmt.add("statement", visitSemaRelease(ctx.semaRelease()));
        } else if (ctx.semaRequest() != null) {
            stmt.add("statement", visitSemaRequest(ctx.semaRequest()));
        } else if (ctx.boltEnter() != null) {
            stmt.add("statement", visitBoltEnter(ctx.boltEnter()));
        } else if (ctx.boltReserve() != null) {
            stmt.add("statement", visitBoltReserve(ctx.boltReserve()));
        } else if (ctx.boltFree() != null) {
            stmt.add("statement", visitBoltFree(ctx.boltFree()));
        } else if (ctx.boltLeave() != null) {
            stmt.add("statement", visitBoltLeave(ctx.boltLeave()));
        }
        return stmt;
    }

    @Override
    public ST visitSemaTry(SmallPearlParser.SemaTryContext ctx) {
        ST operation = m_group.getInstanceOf("SemaTry");
        ST newSemaOrBoltArray = m_group.getInstanceOf("SemaphoreArray");
        
        treatListOfSemaOrBoltNames(m_listOfSemaphoreArrays,constantSemaphoreArrays,
            operation, newSemaOrBoltArray, ctx.listOfNames());
  
        return operation;
    }

    @Override
    public ST visitSemaRelease(SmallPearlParser.SemaReleaseContext ctx) {
        ST operation = m_group.getInstanceOf("SemaRelease");
        ST newSemaOrBoltArray = m_group.getInstanceOf("SemaphoreArray");
        
        treatListOfSemaOrBoltNames(m_listOfSemaphoreArrays,constantSemaphoreArrays,
            operation, newSemaOrBoltArray, ctx.listOfNames());


        return operation;
    }

    @Override
    public ST visitSemaRequest(SmallPearlParser.SemaRequestContext ctx) {
        ST operation = m_group.getInstanceOf("SemaRequest");
        ST newSemaOrBoltArray = m_group.getInstanceOf("SemaphoreArray");
        
        treatListOfSemaOrBoltNames(m_listOfSemaphoreArrays,constantSemaphoreArrays,
            operation, newSemaOrBoltArray, ctx.listOfNames());
        return operation;
    }
    
    /*
     * if there is a REF or REF() in the list of names we must create
     * a local array of names
     * else we can create the arrays at system build
     */
    
    private void treatListOfSemaOrBoltNames(
        LinkedList<LinkedList<String>> listOfSemaOrBoltArrays,
        ST stOfSemaOrBoltArrays,ST currentOperation, ST newSemaOrBoltArray, 
        ListOfNamesContext ctx) {
      LinkedList<String> listOfNames = new LinkedList<String>();
      String nameOfArray="";
      
      boolean listIsConstant = true;
     
      
      for (int i = 0; i < ctx.name().size(); i++) {
        
        ST sem = visitAndDereference(ctx.name(i));
       
        ASTAttribute attr = m_ast.lookup(ctx.name(i));
        if (attr.getType() instanceof TypeReference) {
          listIsConstant = false;
        }
        newSemaOrBoltArray.add("element",sem);
        
        listOfNames.add(ctx.name(i).getText());
      }

     
      Collections.sort(listOfNames);

      currentOperation.add("nbrOfElements", ctx.name().size());
      if ( listIsConstant) {
        for (int i = 0; i < listOfNames.size(); i++) {
          currentOperation.add("names", listOfNames.get(i));
          nameOfArray +="_"+listOfNames.get(i);
        }
        addToListOfConstantSemaOrBoltArrays(listOfSemaOrBoltArrays,
            stOfSemaOrBoltArrays,listOfNames, newSemaOrBoltArray);
        newSemaOrBoltArray.add("isConstant",1);
        newSemaOrBoltArray.add("nameOfArray", nameOfArray);
      } else {
        // create a temporary variable for the statement
        // this produces automatically a block and the variable instanciation
        String tempVarName = nextTempVarName();
        newSemaOrBoltArray.add("nameOfArray", tempVarName);
        currentOperation.add("array", newSemaOrBoltArray);
        currentOperation.add("localArrayname", tempVarName);
        m_tempVariableList.lastElement().add("variable", newSemaOrBoltArray);
      }
    }
    
   
    private Void addToListOfConstantSemaOrBoltArrays(
        LinkedList<LinkedList<String>> listOfSemaOrBoltArrays,
        ST stOfSemaOrBoltArrays,
        LinkedList<String> listOfNames,
        ST semaOrBoltArray) {
      Boolean found = false;
      for (int i = 0; i < listOfSemaOrBoltArrays.size(); i++) {
        LinkedList<String> names = listOfSemaOrBoltArrays.get(i);
        if (names.size() == listOfNames.size()) {
          int j = 0;
          for (j = 0; j < names.size(); j++) {
            if (names.get(j).compareTo(listOfNames.get(j)) != 0) {
              break;
            }
          }

          if (j == names.size()) {
            found = true;
            break;
          }
        }
      }

      if (!found) {
        // update list of combinations 
        listOfSemaOrBoltArrays.add(listOfNames);
        // and add to the array container
        stOfSemaOrBoltArrays.add("array", semaOrBoltArray);
      }

      return null;
    }




    @Override
    public ST visitConstant(SmallPearlParser.ConstantContext ctx) {
        ST st = m_group.getInstanceOf("Constant");
        int last_sign = m_sign;

        if (ctx.sign() != null
                && ctx.sign() instanceof SmallPearlParser.SignMinusContext) {
            m_sign = -1;
        }

        m_sign = last_sign;
        return st;
    }

    @Override
    public ST visitIo_statement(SmallPearlParser.Io_statementContext ctx) {
        ST stmt = m_group.getInstanceOf("io_statement");

        if (ctx.close_statement() != null) {
            stmt.add("code", visitClose_statement(ctx.close_statement()));
        } else if (ctx.open_statement() != null) {
            stmt.add("code", visitOpen_statement(ctx.open_statement()));
        } else if (ctx.readStatement() != null) {
            stmt.add("code", visitReadStatement(ctx.readStatement()));
        } else if (ctx.sendStatement() != null) {
            stmt.add("code", visitSendStatement(ctx.sendStatement()));
        } else if (ctx.takeStatement() != null) {
            stmt.add("code", visitTakeStatement(ctx.takeStatement()));
        } else if (ctx.writeStatement() != null) {
            stmt.add("code", visitWriteStatement(ctx.writeStatement()));
        } else if (ctx.getStatement() != null) {
            stmt.add("code", visitGetStatement(ctx.getStatement()));
        } else if (ctx.putStatement() != null) {
            stmt.add("code", visitPutStatement(ctx.putStatement()));
        }

        return stmt;
    }

    @Override
    public ST visitClose_statement(SmallPearlParser.Close_statementContext ctx) {
        ST stmt = m_group.getInstanceOf("close_statement");
        stmt.add("id", visitAndDereference(ctx.dationName().name()));

        if (ctx.close_parameterlist() != null) {
            stmt.add("paramlist",
                    visitClose_parameterlist(ctx.close_parameterlist()));
        } else {
            ST st = m_group.getInstanceOf("close_parameterlist");
            st.add("parameter", m_group.getInstanceOf("close_parameter_none"));
            stmt.add("paramlist", st);
            stmt.add("rst_var", m_group.getInstanceOf("close_parameter_no_rst"));
        }

        return stmt;
    }

    @Override
    public ST visitOpen_statement(SmallPearlParser.Open_statementContext ctx) {
        ST stmt = m_group.getInstanceOf("open_statement");

        stmt.add("id", visitAndDereference(ctx.dationName().name()));

        if (ctx.open_parameterlist() != null) {
            stmt.add("paramlist",
                    visitOpen_parameterlist(ctx.open_parameterlist()));

			/* obsolete: semantic analsyis enshures <= 1 IDF

      if (idfFilenames.size() > 1) {
        throw new NotSupportedFeatureException(
            "open_statement",
						"OPEN: Mulitiple IDF not supported");
            ctx.start.getCharPositionInLine(),
      }

      if (idfFilenames.size() == 1) {
			 */
            Open_parameter_idfContext idfName = null;
            OpenClosePositionRSTContext rstVar = null;

            if (ctx.open_parameterlist() != null) {
                for (int i = 0; i < ctx.open_parameterlist().open_parameter().size(); i++) {
                    if (ctx.open_parameterlist().open_parameter(i).open_parameter_idf() != null) {
                        idfName = ctx.open_parameterlist().open_parameter(i).open_parameter_idf();
                    }


                    if (ctx.open_parameterlist().open_parameter(i).openClosePositionRST() != null) {
                        rstVar = ctx.open_parameterlist().open_parameter(i).openClosePositionRST();
                    }
                }

            }
            if (idfName != null) {
                String fname = null;
                ST declFilename = m_group.getInstanceOf("declare_idf_filename");
                ST refFilename = m_group
                        .getInstanceOf("reference_idf_filename");

                if (idfName.ID() != null) {
                    fname = idfName.ID().toString();
                    SymbolTableEntry entry = m_currentSymbolTable.lookup(fname);

                    if (entry instanceof VariableEntry) {
                        declFilename.add("variable", fname);
                        refFilename.add("variable", fname);
                    } else {
                        declFilename.add("stringConstant", fname);
                        declFilename.add("lengthOfStringConstant", fname.length());
                        refFilename.add("stringConstant", fname);

                    }
                }
                if (idfName.StringLiteral() != null) {
                    fname = idfName.StringLiteral().toString();
                    fname = fname.substring(1, fname.length() - 1);
                    declFilename.add("stringConstant", fname);
                    declFilename.add("lengthOfStringConstant", fname.length());
                    refFilename.add("stringConstant", fname);
                }
                stmt.add("declFileName", declFilename);
                stmt.add("refFileName", refFilename);

            }
            if (rstVar != null) {
                stmt.add("rst_var", visitAndDereference(rstVar.name()));
            }
        }

        return stmt;
    }


    @Override
    public ST visitOpen_parameterlist(
            SmallPearlParser.Open_parameterlistContext ctx) {
        ST st = m_group.getInstanceOf("open_parameterlist");

        if (ctx.open_parameter() != null) {
            for (int i = 0; i < ctx.open_parameter().size(); i++) {
                if (ctx.open_parameter(i).open_parameter_old_new_any() != null) {
                    Open_parameter_old_new_anyContext ctxTmp = (Open_parameter_old_new_anyContext) ctx.open_parameter(i).open_parameter_old_new_any();
                    st.add("parameter",
                            visitOpen_parameter_old_new_any(ctxTmp));
                }

                if (ctx.open_parameter(i).open_close_parameter_can_prm() != null) {
                    Open_close_parameter_can_prmContext ctxTmp = (Open_close_parameter_can_prmContext) ctx.open_parameter(i).open_close_parameter_can_prm();
                    st.add("parameter",
                            visitOpen_close_parameter_can_prm(ctxTmp));
                }
                if (ctx.open_parameter(i).open_parameter_idf() != null) {
                    Open_parameter_idfContext ctxTemp = (Open_parameter_idfContext) (ctx.open_parameter(i).open_parameter_idf());
                    st.add("parameter",
                            visitOpen_parameter_idf(ctxTemp));
                }

                if (ctx.open_parameter(i).openClosePositionRST() != null) {
                    ST rst = m_group.getInstanceOf("open_close_parameter_rst");
                    rst.add("id",visitAndDereference(ctx.open_parameter(i).openClosePositionRST().name()));
                    st.add("parameter", rst);
                }
            }
        }
        return st;
    }

    @Override
    public ST visitOpen_parameter_idf(
            SmallPearlParser.Open_parameter_idfContext ctx) {
        ST st = m_group.getInstanceOf("open_parameter_idf");

        if (ctx.ID() != null) {
            st.add("id", ctx.ID().getText());
        } else if (ctx.StringLiteral() != null) {
            st.add("string", ctx.StringLiteral().getText());
        }
        return st;
    }

    @Override
    public ST visitOpen_parameter_old_new_any(
            Open_parameter_old_new_anyContext ctxTmp) {
        if (ctxTmp.getText().equals("OLD")) {
            ST st = m_group.getInstanceOf("open_parameter_old");
            st.add("attribute", 1);
            return st;
        } else if (ctxTmp.getText().equals("NEW")) {
            ST st = m_group.getInstanceOf("open_parameter_new");
            st.add("attribute", 1);
            return st;
        } else if (ctxTmp.getText().equals("ANY")) {
            ST st = m_group.getInstanceOf("open_parameter_any");
            st.add("attribute", 1);
            return st;
        }

        return null;
    }

    @Override
    public ST visitOpenClosePositionRST(SmallPearlParser.OpenClosePositionRSTContext ctx) {
        ST st = m_group.getInstanceOf("open_close_parameter_rst");
        st.add("id",visitAndDereference(ctx.name()));
        return st;
    }

    @Override
    public ST visitClose_parameterlist(
            SmallPearlParser.Close_parameterlistContext ctx) {
        ST st = m_group.getInstanceOf("close_parameterlist");

        if (ctx.close_parameter() != null) {
            for (int i = 0; i < ctx.close_parameter().size(); i++) {
                if (ctx.close_parameter(i).openClosePositionRST() != null) {
                    ST rst = m_group.getInstanceOf("close_parameter_rst");
                    rst.add("id",visitAndDereference(
                            ctx.close_parameter(i).openClosePositionRST().name()));
                    st.add("parameter", rst);
                }
                if (ctx.close_parameter(i).open_close_parameter_can_prm() != null) {
                    Open_close_parameter_can_prmContext c = (Open_close_parameter_can_prmContext) ctx.close_parameter(i).open_close_parameter_can_prm();
                    if (c.getText().equals("CAN")) {
                        ST can = m_group.getInstanceOf("close_parameter_can");
                        can.add("attribute", 1);
                        st.add("parameter", can);
                    }
                    if (c.getText().equals("PRM")) {
                        ST prm = m_group.getInstanceOf("close_parameter_prm");
                        prm.add("attribute", 1);
                        st.add("parameter", prm);
                    }
                }
            }
        }
        return st;
    }

    @Override
    public ST visitGetStatement(SmallPearlParser.GetStatementContext ctx) {
        ST stmt = m_group.getInstanceOf("iojob_io_statement");
        stmt.add("command", "get");
        ErrorStack.enter(ctx, "GET");

        stmt.add("dation", visitAndDereference(ctx.dationName().name()));

        addDataAndFormatListToST(stmt, ctx.listOfFormatPositions(), ctx.ioDataList());

        ErrorStack.leave();
        return stmt;

    }

    @Override
    public ST visitPutStatement(SmallPearlParser.PutStatementContext ctx) {
        //ST stmt = m_group.getInstanceOf("put_statement");
        ST stmt = m_group.getInstanceOf("iojob_io_statement");
        ErrorStack.enter(ctx, "PUT");

        stmt.add("command", "put");
        stmt.add("dation", visitAndDereference(ctx.dationName().name()));
        addDataAndFormatListToST(stmt, ctx.listOfFormatPositions(), ctx.ioDataList());

        ErrorStack.leave();

        return stmt;
    }

    private void addDataAndFormatListToST(ST stmt, ListOfFormatPositionsContext fmtCtx, IoDataListContext dataCtx) {
        // this flag is set to true if at least one non static format parameter is detected
        m_isNonStatic = false;

        boolean hasFormats = false;

        // this should never occur, since this is checked in the semantic analysis
        // in CheckIOStatements
        ST formatList = m_group.getInstanceOf("iojob_formatlist");

        // for PUT and GET set format LIST, if the format list is empty
        String command = stmt.getAttribute("command").toString();
        if (fmtCtx == null && (command.equals("put") || command.equals("get"))) {
            ST fmt = m_group.getInstanceOf("iojob_list_format");
            formatList.add("formats", fmt);
            hasFormats = true;
        } else {
            if (fmtCtx != null) {
                for (int i = 0; i < fmtCtx.formatPosition().size(); i++) {
                    addFormatPositionToFormatList(formatList, fmtCtx.formatPosition(i));
                    hasFormats = true;
                }
            }
        }

        if (hasFormats) {
            stmt.add("formatlist", formatList);
            if (!m_isNonStatic) {
                stmt.add("format_list_is_static", "1");
            }
        }

        // create list of datas
        ST dataList = getIojobDataList(dataCtx);

        stmt.add("datalist", dataList);

    }

    private ST getIojobDataList(IoDataListContext ctx) {

        if (ctx != null) {
            ST dataList = m_group.getInstanceOf("iojob_datalist");
            for (int i = 0; i < ctx.ioListElement().size(); i++) {
                //System.out.println("type_ "+m_ast.lookupType(ctx.expression(i)));
                if (ctx.ioListElement(i).expression() != null) {
                    ASTAttribute attr = m_ast.lookup(ctx.ioListElement(i).expression());
                    if (attr.m_type instanceof TypeArray) {
                        TypeArray ta = (TypeArray) attr.m_type;
                        ST data = getIojobDataItem(ta.getBaseType());
                        data.add("variable", "data_" + ctx.ioListElement(i).getText());
                        data.add("nbr_of_elements", ta.getTotalNoOfElements());

                        dataList.add("dataelement", data);
                        //} else if (attr.m_type instanceof TypeStruct) {
                        // Note: STRUCT not treated yet -- must roll out all compartments
                    } else {
                        // scalar value; may be:
                        // constant / variable / expression with scalar type (incl. CharSelection)
                        // regard: BitSelections are treated as type Bit at this point
                        // CharSelections need special treatment
                        //   for character selections we must superseed for known result sizes
                        //       the 'type' and 'size' field with CHARSLICE and the size of the base type
                        //       the selection range must be passed in param2
                        //   TypeVariableChar need 'lwb' and 'upb'
                        ST data = getIojobDataItem(attr.m_type);
                        ExpressionContext e = ctx.ioListElement(i).expression();

                        if (attr.getType() instanceof TypeVariableChar) {
                            CharSelectionContext ssc = (CharSelectionContext) (e.getChild(0).getChild(0).getChild(0));
                            data.add("variable", visitName(ssc.name()));
                            data.add("nbr_of_elements", "1");
                            data.add("lwb", visitAndDereference(ssc.charSelectionSlice().expression(0)));
                            data.add("upb", visitAndDereference(ssc.charSelectionSlice().expression(1)));

                        } else if (attr.isReadOnly() || attr.getVariable() != null) {
                            // constant or  variable with simple type
//                            data.add("variable", getExpression(ctx.ioListElement(i).expression()));
                            data.add("variable", visitAndDereference(ctx.ioListElement(i).expression()));
                            data.add("nbr_of_elements", "1");
                        } else {
                            // it is an expression
                            // System.out.println("need temp variable for expression: "+ ctx.expression(i).getText());

                            // get ST according base type
                            ST t = attr.m_type.toST(m_group);
                            if (t == null) {
                                System.out.println("untreated type " + attr.m_type);
                            }

                            // let's see if the expression is of type CharSelectionContext
                            CharSelectionContext ssc = null;
                            try {
                                ssc = (CharSelectionContext) (e.getChild(0).getChild(0).getChild(0));
                            } catch (ClassCastException ex) {
                            } catch (NullPointerException ex) {
                            }
                            ;

                            if (ssc != null) {
                                // fixed size char selection
                                data.remove("type");
                                data.add("type", "CHARSLICE");
                                data.remove("size");
                                ASTAttribute attribName = m_ast.lookup(ssc.name());
                                data.add("size", attribName.getType().getPrecision());
                                data.add("nbr_of_elements", "1");
                                data.add("variable", visitName(ssc.name()));
                                if (attr.getConstantSelection() != null) {
                                    data.add("lwb", attr.getConstantSelection().getLowerBoundary());
                                    data.add("upb", attr.getConstantSelection().getUpperBoundary());
                                } else {
                                    // must be of type .char(x:x+4) of type .char(x); with x is non constant expression
                                    data.add("lwb", visitAndDereference(ssc.charSelectionSlice().expression(0)));
                                    ST upb = m_group.getInstanceOf("expression");
                                    upb.add("code", data.getAttribute("lwb"));
                                    if (ssc.charSelectionSlice().IntegerConstant() != null) {
                                        upb.add("code", "+");
                                        upb.add("code", "(pearlrt::Fixed<15>)(" + (attr.getType().getPrecision() - 1) + ")");
                                    }
                                    data.add("upb", upb);
                                }

                            } else {
                                // expression, which needs a temporary variable to store the result
                                // since we pass a pointer to the formatting routines
                                ST variable_declaration = m_group.getInstanceOf("variable_declaration");
                                variable_declaration.add("name", "tempVar" + i);
                                variable_declaration.add("type", t);
                                variable_declaration.add("init", visitAndDereference(ctx.ioListElement(i).expression()));
                                variable_declaration.add("no_decoration", 1);
                                dataList.add("data_variable", variable_declaration);
                                dataList.add("data_index", i);

                                data.add("variable", "tempVar" + i);
                                data.add("nbr_of_elements", "1");
                            }
                        }

                        //   addVariableConstantOrExpressionToDatalist(dataList,data,attr,ctx.expression(i),i);
                        dataList.add("dataelement", data);
                    }
                } else if (ctx.ioListElement(i).arraySlice() != null) {
                    ArraySliceContext slice = ctx.ioListElement(i).arraySlice();
                    ASTAttribute attr = m_ast.lookup(slice);
                    // we need
                    //  + the base type of the array
                    //  + the address of the name(startIndex)
                    //  + number of elements
                    TypeArraySlice tas = (TypeArraySlice) (attr.getType());
                    TypeArray ta = (TypeArray) (tas.getBaseType());
                    TypeDefinition t = ta.getBaseType();

                    ST data = getIojobDataItem(t);

                    ST firstElement = m_group.getInstanceOf("ArrayLHS");

                    ParserRuleContext c = slice.name();
                    if (c instanceof FormalParameterContext) {
                        firstElement.add("descriptor", "ad_" + slice.name().ID());
                    } else {
                        ArrayDescriptor array_descriptor = new ArrayDescriptor(
                                ta.getNoOfDimensions(), ta.getDimensions());
                        firstElement.add("descriptor", array_descriptor.getName());
                    }
                    firstElement.add("name", slice.name().ID());

                    // if no indices are given, the complete array is accessed
                    firstElement.add("indices", getIndices(slice.startIndex().listOfExpression().expression()));

                    data.add("variable", firstElement);

                    int lastElementInList = slice.startIndex().listOfExpression().expression().size() - 1;
                    if (tas.hasConstantSize()) {
                        // both limits are constant -- we know the nbr_of_elements
                        data.add("nbr_of_elements", tas.getTotalNoOfElements());
                    } else {
                        ST nbr = m_group.getInstanceOf("expression");
                        nbr.add("code", "(");
                        nbr.add("code", visitAndDereference(slice.endIndex().expression()));
                        nbr.add("code", "-");

                        nbr.add("code", visitAndDereference(slice.startIndex().listOfExpression().expression(lastElementInList)));
                        nbr.add("code", ").get()+1");

                        data.add("nbr_of_elements", nbr);
                    }
                    dataList.add("dataelement", data);
                }
            }
            return dataList;
        }
        return null; // no dataList
    }

    private ST getIojobDataItem(TypeDefinition type) {
        ST data = m_group.getInstanceOf("iojob_data_item");
        
        if (type instanceof TypeReference) {
          type = ((TypeReference)type).getBaseType();
        }

        if (type instanceof TypeFixed) {
            data.add("type", "FIXED");
            data.add("size", ((TypeFixed) (type)).getPrecision());
        } else if (type instanceof TypeFloat) {
            data.add("type", "FLOAT");
            data.add("size", ((TypeFloat) (type)).getPrecision());
        } else if (type instanceof TypeChar) {
            data.add("type", "CHAR");
            data.add("size", ((TypeChar) (type)).getSize());
        } else if (type instanceof TypeVariableChar) {
            data.add("type", "CHARSLICE");
            data.add("size", ((TypeVariableChar) (type)).getBaseType().getPrecision());
        } else if (type instanceof TypeBit) {
            data.add("type", "BIT");
            data.add("size", ((TypeBit) (type)).getPrecision());
        } else if (type instanceof TypeClock) {
            data.add("type", "CLOCK");
        } else if (type instanceof TypeDuration) {
            data.add("type", "DURATION");
        } else {
            ErrorStack.addInternal("getIoJobDataItem: untreated type " + type);
        }
        return data;
    }


    private void setIsNonStatic() {
        m_isNonStatic = true;
    }

    private void updateIsNonStatic(ParserRuleContext ctx) {
        ASTAttribute attr;
        attr = m_ast.lookup(ctx);
        if (attr == null) {
            // we do not know details
            System.out.println("no ASTAttributes on " + ctx.getText());
            m_isNonStatic = true;
        } else {
            if (attr.m_constant == null && !attr.isReadOnly()) {
                m_isNonStatic = true;  // no constant
            }
        }
    }

    private int addFormatPositionToFormatList(ST formatList,
                                              FormatPositionContext ctx) {
        int nbrOfFormats = 0;

        // we have to deal with
        //   FactorFormatContext   like 3F(6)
        //   FactorPositionContext like 3X(4)
        //   FactorFormatPositionContext  like (3)(A,F(6),SKIP)
        //   factor is always aptional!
        if (ctx instanceof SmallPearlParser.FactorFormatContext) {
            FactorFormatContext c = (FactorFormatContext) (ctx);
            if (c.factor() != null) {
                addFactorToFormatList(formatList, c.factor());
                nbrOfFormats++;
            }

            // add format
            if (c.format() instanceof SmallPearlParser.FormatContext) {
                FormatContext c1 = (FormatContext) (c.format());
                if (c1.fixedFormat() != null) {
                    FixedFormatContext ffc = c1.fixedFormat();
                    ST fmt = m_group.getInstanceOf("iojob_fixed_format");
                    updateIsNonStatic(ffc.fieldWidth().expression());
                    fmt.add("fieldwidth", visitAndDereference(ffc.fieldWidth().expression()));
                    if (c1.fixedFormat().decimalPositions() != null) {
                        updateIsNonStatic(ffc.decimalPositions().expression());
                        fmt.add("decimalPositions", visitAndDereference(ffc.decimalPositions().expression()));
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (c1.floatFormat() != null) {
                    ST fmt = m_group.getInstanceOf("iojob_e_format");
                    if (c1.floatFormat() instanceof FloatFormatEContext) {
                        FloatFormatEContext c2 = (FloatFormatEContext) c1.floatFormat();
                        updateIsNonStatic(c2.fieldWidth().expression());
                        fmt.add("fieldwidth", visitAndDereference(c2.fieldWidth().expression()));
                        fmt.add("exp23", 2);
                        if (c2.decimalPositions() != null) {
                            updateIsNonStatic(c2.decimalPositions().expression());
                            fmt.add("decimalPositions", visitAndDereference(c2.decimalPositions().expression()));
                        }
                        if (c2.significance() != null) {
                            updateIsNonStatic(c2.significance().expression());
                            fmt.add("significance", visitAndDereference(c2.significance().expression()));
                        }
                    } else if (c1.floatFormat() instanceof FloatFormatE3Context) {
                        FloatFormatE3Context c2 = (FloatFormatE3Context) c1.floatFormat();
                        updateIsNonStatic(c2.fieldWidth().expression());
                        fmt.add("fieldwidth", visitAndDereference(c2.fieldWidth().expression()));
                        fmt.add("exp23", 3);
                        if (c2.decimalPositions() != null) {
                            updateIsNonStatic(c2.decimalPositions().expression());
                            fmt.add("decimalPositions", visitAndDereference(c2.decimalPositions().expression()));
                        }
                        if (c2.significance() != null) {
                            updateIsNonStatic(c2.significance().expression());
                            fmt.add("significance", visitAndDereference(c2.significance().expression()));
                        }
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (c1.characterStringFormat() != null) {
                    if (c1.characterStringFormat() instanceof CharacterStringFormatAContext) {
                        CharacterStringFormatAContext c2 = (CharacterStringFormatAContext) c1.characterStringFormat();
                        ST fmt = m_group.getInstanceOf("iojob_character_string_format");
                        if (c2.fieldWidth() != null) {
                            updateIsNonStatic(c2.fieldWidth().expression());
                            fmt.add("fieldwidth", visitAndDereference(c2.fieldWidth().expression()));
                        }
                        formatList.add("formats", fmt);
                        nbrOfFormats++;
                    }
                } else if (c1.bitFormat() != null) {
                    ST fmt = m_group.getInstanceOf("iojob_bit_format");
                    if (c1.bitFormat() instanceof BitFormat1Context) {
                        BitFormat1Context c2 = (BitFormat1Context) (c1.bitFormat());
                        fmt.add("base", 1);
                        if (c2.numberOfCharacters() != null) {
                            updateIsNonStatic(c2.numberOfCharacters().expression());
                            fmt.add("fieldwidth", visitAndDereference(c2.numberOfCharacters().expression()));
                        }
                    } else if (c1.bitFormat() instanceof BitFormat2Context) {
                        BitFormat2Context c2 = (BitFormat2Context) (c1.bitFormat());
                        fmt.add("base", 2);
                        if (c2.numberOfCharacters() != null) {
                            updateIsNonStatic(c2.numberOfCharacters().expression());
                            fmt.add("fieldwidth", visitAndDereference(c2.numberOfCharacters().expression()));
                        }
                    } else if (c1.bitFormat() instanceof BitFormat3Context) {
                        BitFormat3Context c2 = (BitFormat3Context) (c1.bitFormat());
                        fmt.add("base", 3);
                        if (c2.numberOfCharacters() != null) {
                            updateIsNonStatic(c2.numberOfCharacters().expression());
                            fmt.add("fieldwidth", visitAndDereference(c2.numberOfCharacters().expression()));
                        }
                    } else if (c1.bitFormat() instanceof BitFormat4Context) {
                        BitFormat4Context c2 = (BitFormat4Context) (c1.bitFormat());
                        fmt.add("base", 4);
                        if (c2.numberOfCharacters() != null) {
                            updateIsNonStatic(c2.numberOfCharacters().expression());
                            fmt.add("fieldwidth", visitAndDereference(c2.numberOfCharacters().expression()));
                        }
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (c1.timeFormat() != null) {
                    TimeFormatContext ffc = c1.timeFormat();
                    ST fmt = m_group.getInstanceOf("iojob_time_format");
                    updateIsNonStatic(ffc.fieldWidth().expression());
                    fmt.add("fieldwidth", visitAndDereference(ffc.fieldWidth().expression()));
                    if (ffc.decimalPositions() != null) {
                        updateIsNonStatic(ffc.decimalPositions().expression());
                        fmt.add("decimalPositions", visitAndDereference(ffc.decimalPositions().expression()));
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (c1.durationFormat() != null) {
                    DurationFormatContext ffc = c1.durationFormat();
                    ST fmt = m_group.getInstanceOf("iojob_duration_format");
                    fmt.add("fieldwidth", visitAndDereference(ffc.fieldWidth().expression()));
                    updateIsNonStatic(ffc.fieldWidth().expression());
                    if (ffc.decimalPositions() != null) {
                        updateIsNonStatic(ffc.decimalPositions().expression());
                        fmt.add("decimalPositions", visitAndDereference(ffc.decimalPositions().expression()));
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (c1.listFormat() != null) {
                    ST fmt = m_group.getInstanceOf("iojob_list_format");
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else {
                    System.out.println("untreated format " + c.format().getText());
                }

            }
        }
        if (ctx instanceof SmallPearlParser.FactorPositionContext) {
            FactorPositionContext c = (FactorPositionContext) (ctx);
            if (c.factor() != null) {
                updateIsNonStatic(c.factor());
                addFactorToFormatList(formatList, c.factor());
                nbrOfFormats++;
            }
            // treat position
            if (c.position().openClosePositionRST() != null) {
                ST fmt = m_group.getInstanceOf("iojob_rst");
                fmt.add("element", visitAndDereference(c.position().openClosePositionRST().name()));
                TypeFixed tf = (TypeFixed)(m_ast.lookupType(c.position().openClosePositionRST().name()));
                fmt.add("size", tf.getPrecision());
                

                formatList.add("formats", fmt);
                setIsNonStatic();
                nbrOfFormats++;
            } else if (c.position().relativePosition() != null) {
                RelativePositionContext rp = c.position().relativePosition();

                if (rp.positionSKIP() != null) {
                    ExpressionContext e = rp.positionSKIP().expression();
                    ST fmt = m_group.getInstanceOf("iojob_position_skip");
                    if (e != null) {
                        updateIsNonStatic(e);
                        fmt.add("element", visitAndDereference(e));
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (rp.positionX() != null) {
                    ExpressionContext e = rp.positionX().expression();
                    ST fmt = m_group.getInstanceOf("iojob_position_x");
                    if (e != null) {
                        updateIsNonStatic(e);
                        fmt.add("element", visitAndDereference(e));
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (rp.positionPAGE() != null) {
                    ExpressionContext e = rp.positionPAGE().expression();
                    ST fmt = m_group.getInstanceOf("iojob_position_page");
                    if (e != null) {
                        updateIsNonStatic(e);
                        fmt.add("element", visitAndDereference(e));
                    }
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (rp.positionADV() != null) {
                    ST fmt = m_group.getInstanceOf("iojob_position_adv");
                    for (int i = 0; i < rp.positionADV().expression().size(); i++) {
                        ExpressionContext e = rp.positionADV().expression(i);
                        updateIsNonStatic(e);
                        fmt.add("expression" + (i + 1), visitAndDereference(e));
                    }
                    fmt.add("dimensions", rp.positionADV().expression().size());
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (rp.positionEOF() != null) {
                    ST fmt = m_group.getInstanceOf("iojob_position_eof");
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else {
                    System.out.println("untreated positioning: " + c.position().getText());
                }
            } else if (c.position().absolutePosition() != null) {
                AbsolutePositionContext ap = c.position().absolutePosition();
                if (ap.positionCOL() != null) {
                    ExpressionContext e = ap.positionCOL().expression();
                    ST fmt = m_group.getInstanceOf("iojob_position_col");
                    updateIsNonStatic(e);
                    fmt.add("element", visitAndDereference(e));
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (ap.positionLINE() != null) {
                    ExpressionContext e = ap.positionLINE().expression();
                    ST fmt = m_group.getInstanceOf("iojob_position_line");
                    updateIsNonStatic(e);
                    fmt.add("element", visitAndDereference(e));
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (ap.positionPOS() != null) {
                    ST fmt = m_group.getInstanceOf("iojob_position_pos");
                    for (int i = 0; i < ap.positionPOS().expression().size(); i++) {
                        ExpressionContext e = ap.positionPOS().expression(i);
                        updateIsNonStatic(e);
                        fmt.add("expression" + (i + 1), visitAndDereference(e));
                    }
                    fmt.add("dimensions", ap.positionPOS().expression().size());
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else if (ap.positionSOP() != null) {
                    ST fmt = m_group.getInstanceOf("iojob_position_sop");
                    for (int i = 0; i < ap.positionSOP().ID().size(); i++) {
                        fmt.add("element" + (i + 1), getUserVariable(ap.positionSOP().ID(i).getText()));
                        SymbolTableEntry se = m_currentSymbolTable.lookup(ap.positionSOP().ID(i).getText());
                        if (se instanceof VariableEntry) {
                            TypeFixed tf = (TypeFixed) (((VariableEntry) se).getType());
                            fmt.add("size" + (i + 1), tf.getPrecision());
                        }
                        setIsNonStatic();
                    }
                    fmt.add("dimensions", ap.positionSOP().ID().size());
                    formatList.add("formats", fmt);
                    nbrOfFormats++;
                } else {
                    System.out.println("untreated positioning: " + c.position().getText());
                }
            }
        }

        if (ctx instanceof SmallPearlParser.FactorFormatPositionContext) {
            // System.out.println("(x)( , , , )  treatment");
            FactorFormatPositionContext c = (FactorFormatPositionContext) (ctx);
            //System.out.println("FactorFormatPosition"+c.getText());
            ST loop = m_group.getInstanceOf("iojob_format_loopstart");

            if (c.factor() != null) {
                if (c.factor().expression() != null) {
                    updateIsNonStatic(c.factor().expression());
                    loop.add("repetitions", visitAndDereference(c.factor().expression()));
                    formatList.add("formats", loop);
                    nbrOfFormats++;
                } else if (c.factor().integerWithoutPrecision() != null) {
                    String s = c.factor().integerWithoutPrecision().IntegerConstant().getText();
                    loop.add("repetitions", s);
                }
            }
            nbrOfFormats += addFormatPositionToFormatList(formatList, c.listOfFormatPositions());

            // decrement the number of format element, since the start_loop element
            // does not count, but all containing start_loop elements count
            loop.add("elements", nbrOfFormats - 1);
        }
        return nbrOfFormats;

    }

    private int addFormatPositionToFormatList(ST formatList, ListOfFormatPositionsContext list) {
        int length = 0;
        for (int i = 0; i < list.formatPosition().size(); i++) {
            length += addFormatPositionToFormatList(formatList, list.formatPosition(i));
        }
        return length;
    }


    private void addFactorToFormatList(ST formatList, FactorContext ctx) {
        ST loopStart = m_group.getInstanceOf("iojob_format_loopstart");

        if (ctx.expression() != null) {
            updateIsNonStatic(ctx.expression());
            System.out.println("FactorPosition  (" + ctx.expression().getText() + ")");
            loopStart.add("repetitions", visitAndDereference(ctx.expression()));
        }
        if (ctx.integerWithoutPrecision() != null) {
            loopStart.add("repetitions", ctx.integerWithoutPrecision().IntegerConstant().getText());
        }
        loopStart.add("elements", 1);
        formatList.add("formats", loopStart);
    }


    private ST getUserVariable(String user_variable) {
        ST st = m_group.getInstanceOf("user_variable");
        st.add("name", user_variable);
        return st;
    }


    @Override
    public ST visitSendStatement(SmallPearlParser.SendStatementContext ctx) {
        ST st = m_group.getInstanceOf("iojob_io_statement");
        ErrorStack.enter(ctx, "SEND");
        st.add("command", "send");

        st.add("dation", visitAndDereference(ctx.dationName().name()));

        addDataAndFormatListToST(st, ctx.listOfFormatPositions(), ctx.ioDataList());
        ErrorStack.leave();

        return st;
    }


    @Override
    public ST visitTakeStatement(SmallPearlParser.TakeStatementContext ctx) {
        ST st = m_group.getInstanceOf("iojob_io_statement");
        st.add("command", "take");
        ErrorStack.enter(ctx, "TAKE");

        st.add("dation", visitAndDereference(ctx.dationName().name()));

        addDataAndFormatListToST(st, ctx.listOfFormatPositions(), ctx.ioDataList());
        ErrorStack.leave();

        return st;
    }


    @Override
    public ST visitReadStatement(SmallPearlParser.ReadStatementContext ctx) {
        ST st = m_group.getInstanceOf("iojob_io_statement");
        st.add("command", "read");

        ErrorStack.enter(ctx, "READ");
        
        st.add("dation", visitAndDereference(ctx.dationName().name()));
        
        addDataAndFormatListToST(st, ctx.listOfFormatPositions(), ctx.ioDataList());

        ErrorStack.leave();

        return st;
    }


    @Override
    public ST visitWriteStatement(SmallPearlParser.WriteStatementContext ctx) {
        ST st = m_group.getInstanceOf("iojob_io_statement");
        st.add("command", "write");

        ErrorStack.enter(ctx, "WRITE");

        st.add("dation", visitAndDereference(ctx.dationName().name()));
        
        addDataAndFormatListToST(st, ctx.listOfFormatPositions(), ctx.ioDataList());

        ErrorStack.leave();

        return st;
    }


    @Override
    public ST visitCallStatement(SmallPearlParser.CallStatementContext ctx) {
        ST stmt = m_group.getInstanceOf("CallStatement");

        stmt.add("callee", ctx.ID());
        if (ctx.listOfActualParameters() != null) {
            stmt.add("ListOfActualParameters",
                    visitListOfActualParameters(ctx.listOfActualParameters()));
        }

        return stmt;
    }

    @Override
    public ST visitListOfActualParameters(
            SmallPearlParser.ListOfActualParametersContext ctx) {
        ST stmt = m_group.getInstanceOf("ActualParameters");

        // let's see if we must pass an array
        if (ctx.expression() != null) {
            for (int i = 0; i < ctx.expression().size(); i++) {
                addParameter2StOfProcParamList(stmt, ctx.expression(i));
            }
        }
        return stmt;
    }

    /**
     * add an expression result to the actual parameter list for
     * a functionCall or a procedureCall
     * <p>
     * In case of the given expression is an array, we must add
     * an array descriptor and the array data
     * The array descriptor is derived from the variable entry of the expression.
     * If the array is already a formal parameter, we pass the formal array descriptor.
     * If is is an array variable, the descriptor is derived from the arraqy diemnsions
     *
     * @param stmt       the ST context which holds all parameters
     * @param expression the current parameter
     */
    private void addParameter2StOfProcParamList(ST stmt, ExpressionContext expression) {
        boolean treatArray = false;
        SymbolTableEntry se = null;
        ASTAttribute attr = null;

        attr = m_ast.lookup(expression);
        if (attr != null) {
            if (attr.getType() instanceof TypeArray) {
                treatArray = true;
            }
            if (attr.getVariable() != null) {
                String var = attr.getVariable().getName();
                se = m_currentSymbolTable.lookup(var);
            }
        }


        if (treatArray) {
            ST param = m_group.getInstanceOf("ActualParameters");
			/*
			ArrayDescriptor array_descriptor = new ArrayDescriptor(
					ta.getNoOfDimensions(),
					ta.getDimensions());
			param.add("ActualParameter",array_descriptor.getName());
			*/
            param.add("ActualParameter", getArrayDescriptor((VariableEntry) se));

            stmt.add("ActualParameter", param);
            param = m_group.getInstanceOf("ActualParameters");
            param.add("ActualParameter", "data_" + attr.getVariable().getName());
            stmt.add("ActualParameter", param);
        } else {
            // scalar type
            if (attr.getType() instanceof TypeVariableChar) {
                ST temp = m_group.getInstanceOf("TempCharVariable");

                String tempVarName = nextTempVarName();

                TypeVariableChar t = (TypeVariableChar) (attr.getType());
                temp.add("char_size", t.getBaseType().getPrecision());
                temp.add("variable", tempVarName);
                temp.add("expr", visitAndDereference(expression));
                ST param = m_group.getInstanceOf("ActualParameters");
                param.add("ActualParameter", tempVarName);
                stmt.add("ActualParameter", param);

                m_tempVariableList.lastElement().add("variable", temp);
            } else {
                ST param = m_group.getInstanceOf("ActualParameters");
                param.add("ActualParameter", visitAndDereference(expression));
                stmt.add("ActualParameter", param);
            }
        }
    }

    private ST getActualParameters(
            List<SmallPearlParser.ExpressionContext> parameters) {
        ST stmt = m_group.getInstanceOf("ActualParameters");

        if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                addParameter2StOfProcParamList(stmt, parameters.get(i));
            }
        }

        return stmt;
    }

    private ST getIndices(List<SmallPearlParser.ExpressionContext> indices) {
        ST st = m_group.getInstanceOf("ArrayIndices");

        if (indices != null) {
            for (int i = 0; i < indices.size(); i++) {
                ST stIndex = m_group.getInstanceOf("ArrayIndex");
                stIndex.add("index", visitAndDereference(indices.get(i)));
                st.add("indices", stIndex);
            }
        }

        return st;
    }

    @Override
    public ST visitCpp_inline(SmallPearlParser.Cpp_inlineContext ctx) {
        ST stmt = m_group.getInstanceOf("cpp_inline");

        stmt.add("body", "#warning __cpp__ inline inserted");

        int i;
        for (i = 0; i < ctx.CppStringLiteral().size(); i++) {
            String line = ctx.CppStringLiteral(i).toString();

            line = line.replaceAll("^\"", "");
            line = line.replaceAll("\"$", "");

            line = CommonUtils.unescapeCppString(line);

            stmt.add("body", line);
        }

        return stmt;
    }

    @Override
    public ST visitAtanExpression(SmallPearlParser.AtanExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ATAN");
        st.add("operand", visitAndDereference(ctx.expression()));//getChild(1)));
        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());

        return st;
    }

    @Override
    public ST visitCosExpression(SmallPearlParser.CosExpressionContext ctx) {
        ST st = m_group.getInstanceOf("COS");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));
        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());

        return st;
    }

    @Override
    public ST visitExpExpression(SmallPearlParser.ExpExpressionContext ctx) {
        ST st = m_group.getInstanceOf("EXP");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));
        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());

        return st;
    }

    @Override
    public ST visitLnExpression(SmallPearlParser.LnExpressionContext ctx) {
        ST st = m_group.getInstanceOf("LN");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));

        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());

        return st;
    }

    @Override
    public ST visitSinExpression(SmallPearlParser.SinExpressionContext ctx) {
        ST st = m_group.getInstanceOf("SIN");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));
        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());
        return st;
    }

    @Override
    public ST visitSqrtExpression(SmallPearlParser.SqrtExpressionContext ctx) {
        ST st = m_group.getInstanceOf("SQRT");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));

        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());

        return st;
    }

    private Void addFixedFloatConversion(ST st, ExpressionContext ctxExpr) {
        ASTAttribute op = m_ast.lookup(ctxExpr);

        if (op.getType() instanceof TypeFixed) {
            int precision = ((TypeFixed) op.getType()).getPrecision();
            if (precision > Defaults.FLOAT_SHORT_PRECISION) {
                precision = Defaults.FLOAT_LONG_PRECISION;
            } else {
                precision = Defaults.FLOAT_SHORT_PRECISION;
            }
            st.add("convert_to", precision);
        }

        return null;
    }

    private Void addFixedFloatConversion(ST st, SmallPearlParser.ExpressionContext ctx, int indexOfExpression) {
        ASTAttribute op = m_ast.lookup(ctx);

        if (op.getType() instanceof TypeFixed) {
            int precision = ((TypeFixed) op.getType()).getPrecision();
            if (precision > Defaults.FLOAT_SHORT_PRECISION) {
                precision = Defaults.FLOAT_LONG_PRECISION;
            } else {
                precision = Defaults.FLOAT_SHORT_PRECISION;
            }
            st.add("convert_to" + indexOfExpression, precision);
        }

        return null;
    }

    @Override
    public ST visitTanExpression(SmallPearlParser.TanExpressionContext ctx) {
        ST st = m_group.getInstanceOf("TAN");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));

        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());

        return st;
    }

    @Override
    public ST visitTanhExpression(SmallPearlParser.TanhExpressionContext ctx) {
        ST st = m_group.getInstanceOf("TANH");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));

        // if applied to FIXED type we must to FLOAT before applying the operator
        addFixedFloatConversion(st, ctx.expression());

        return st;
    }

    @Override
    public ST visitAbsExpression(SmallPearlParser.AbsExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ABS");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));
        return st;
    }

    @Override
    public ST visitSignExpression(SmallPearlParser.SignExpressionContext ctx) {
        ST st = m_group.getInstanceOf("SIGN");
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));
        return st;
    }

    @Override
    public ST visitRemainderExpression(
            SmallPearlParser.RemainderExpressionContext ctx) {
        ST st = m_group.getInstanceOf("REM");
        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));
        return st;
    }

    @Override
    public ST visitSizeofExpression(SmallPearlParser.SizeofExpressionContext ctx) {
        ST st = m_group.getInstanceOf("SIZEOF");
        if (ctx.expression() != null) {
            ASTAttribute attr = m_ast.lookup(ctx.expression());
            if (attr.getVariable() != null) {

                VariableEntry ve = attr.getVariable();
                String name = ve.getName();
                if (ve.getType() instanceof TypeArray) {
                    st.add("operand", "data_" + name);
                } else {
                    st.add("operand", getUserVariable(name));
                }
            }
        } else if (ctx.simpleType() != null) {
            String typeName = "";
            if (ctx.simpleType().typeInteger() != null) {
                long length = Defaults.FIXED_LENGTH;
                if (ctx.simpleType().typeInteger().mprecision() != null) {
                    String s = ctx.simpleType().typeInteger().mprecision().integerWithoutPrecision().getText();
                    length = Integer.parseInt(ctx.simpleType().typeInteger().mprecision().integerWithoutPrecision().getText());
                }
                typeName = "pearlrt::Fixed<" + length + ">";
            } else if (ctx.simpleType().typeDuration() != null) {
                typeName = "pearlrt::Duration";
            } else if (ctx.simpleType().typeTime() != null) {
                typeName = "pearlrt::Clock";
            } else if (ctx.simpleType().typeFloatingPointNumber() != null) {
                long length = Defaults.FLOAT_PRECISION;
                if (ctx.simpleType().typeFloatingPointNumber().IntegerConstant() != null) {
                    length = Integer.parseInt(ctx.simpleType().typeFloatingPointNumber().IntegerConstant().toString());
                }
                typeName = "pearlrt::Float<" + length + ">";

            } else if (ctx.simpleType().typeBitString() != null) {
                long length = Defaults.BIT_LENGTH;
                if (ctx.simpleType().typeBitString().IntegerConstant() != null) {
                    length = Integer.parseInt(ctx.simpleType().typeBitString().IntegerConstant().toString());
                }
                typeName = "pearlrt::BitString<" + length + ">";
            } else if (ctx.simpleType().typeCharacterString() != null) {
                long length = Defaults.CHARACTER_LENGTH;
                if (ctx.simpleType().typeCharacterString().IntegerConstant() != null) {
                    length = Integer.parseInt(ctx.simpleType().typeCharacterString().IntegerConstant().toString());
                }
                typeName = "pearlrt::Character<" + length + ">";

            } else {
                // emergency -- set compiler internal type --> cause c++ errors
                typeName = ctx.simpleType().getText().toString();
            }

            st.add("operand", typeName);
        }
        //st.add("operand", visitAndDereference(ctx.getChild(1)));
        return st;
    }

    @Override
    public ST visitEntierExpression(SmallPearlParser.EntierExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ENTIER");
        if (m_debug) {
            System.out
                    .println("CppCodeGeneratorVisitor: visitEntierExpression");
        }
        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));
        return st;
    }

    @Override
    public ST visitRoundExpression(SmallPearlParser.RoundExpressionContext ctx) {
        ST st = m_group.getInstanceOf("ROUND");
        if (m_debug) {
            System.out.println("CppCodeGeneratorVisitor: visitRoundExpression");
        }

        st.add("operand", visitAndDereference(ctx.expression())); //getChild(1)));
        return st;
    }

    @Override
    public ST visitEqRelationalExpression(
            SmallPearlParser.EqRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("EQ");

        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitNeRelationalExpression(
            SmallPearlParser.NeRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("NE");

        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitLtRelationalExpression(
            SmallPearlParser.LtRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("LT");

        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitLeRelationalExpression(
            SmallPearlParser.LeRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("LE");

        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitGtRelationalExpression(
            SmallPearlParser.GtRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("GT");

        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitGeRelationalExpression(
            SmallPearlParser.GeRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("GE");

        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitIsRelationalExpression(
            SmallPearlParser.IsRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("EQ");

        treatIsIsnt(st,ctx.expression(0), ctx.expression(1));
        return st;
    }
    
 

    @Override
    public ST visitIsntRelationalExpression(
            SmallPearlParser.IsntRelationalExpressionContext ctx) {
        ST st = m_group.getInstanceOf("NE");
        treatIsIsnt(st,ctx.expression(0), ctx.expression(1));
        return st;
    }
    
    /* we have to deal with 
      lhs            rhs         
      REF           type    lhs/rhs=visit(ctx0/1)
      type          REF     lhs/rhs=visit(ctx0/1)
      REF()         REF()   rhs/lhs=visit(ctx0/1)
      REF()         ARRAY   lhs=visit; rhs=arrayReference(...)
      REF()         NIL     rhs/lhs=visit(ctx0/1)
      ARRAY         REF()   lhs=arrayRef(...), rhs=visit
      NIL           REF()   rhs/lhs=visit(ctx0/1)
      NIL           NIL     rhs/lhs=visit(ctx0/1)
     
     */
    private void treatIsIsnt(ST st,ExpressionContext ctx0, ExpressionContext ctx1) {
      ASTAttribute attrLhs = m_ast.lookup(ctx0);
      ASTAttribute attrRhs = m_ast.lookup(ctx1);
      
      // lhs is Array
      if (attrLhs.getType() instanceof TypeArray) {
        ST ar = m_group.getInstanceOf("arrayReference");
        TypeDefinition td = ((TypeArray)attrLhs.getType()).getBaseType();
        ar.add("basetype", td.toST(m_group));
        ar.add("descriptor",getArrayDescriptor(attrRhs.getVariable()));
        ar.add("data","data_"+attrLhs.getVariable().getName());
        st.add("lhs", ar);
      } else {
        st.add("lhs",  visit(ctx0));
      }
      // rhs is Array
      if (attrRhs.getType() instanceof TypeArray) {
        ST ar = m_group.getInstanceOf("arrayReference");
        TypeDefinition td = ((TypeArray)attrRhs.getType()).getBaseType();
        ar.add("basetype", td.toST(m_group));
        ar.add("descriptor",getArrayDescriptor(attrRhs.getVariable()));
        ar.add("data","data_"+attrRhs.getVariable().getName());
        st.add("rhs", ar);
      } else {
        st.add("rhs",  visit(ctx1));
      }  
      
     
      return;
    }

    @Override
    public ST visitFitExpression(SmallPearlParser.FitExpressionContext ctx) {
        ST st = m_group.getInstanceOf("FIT");

        st.add("lhs", visitAndDereference(ctx.expression(0)));
        st.add("rhs", visitAndDereference(ctx.expression(1)));

        return st;
    }

    @Override
    public ST visitNowFunction(SmallPearlParser.NowFunctionContext ctx) {
        ST st = m_group.getInstanceOf("NOW");
        return st;
    }

    @Override
    public ST visitDationSpecification(
            SmallPearlParser.DationSpecificationContext ctx) {
        ST dationSpecifications = m_group.getInstanceOf("DationSpecifications");
        boolean hasGlobalAttribute = false;

        ArrayList<String> identifierDenotationList = null;
        if (ctx != null) {
            if (ctx.identifierDenotation() != null) {
                identifierDenotationList = getIdentifierDenotation(ctx
                        .identifierDenotation());
            }

            if (ctx.globalAttribute() != null) {
                hasGlobalAttribute = true;
            }

            String dationClass = getDationClass(ctx.typeDation()
                    .classAttribute());

            for (int i = 0; i < identifierDenotationList.size(); i++) {
                if (dationClass.equals("SystemDationB")) {
                    ST specifyDation = m_group
                            .getInstanceOf("SpecificationSystemDationB");
                    specifyDation.add("name", identifierDenotationList.get(i));
                    dationSpecifications.add("decl", specifyDation);
                } else if (dationClass.equals("SystemDationNB")) {
                    ST specifyDation = m_group
                            .getInstanceOf("SpecificationSystemDationNB");
                    specifyDation.add("name", identifierDenotationList.get(i));
                    dationSpecifications.add("decl", specifyDation);
                } else if (dationClass.equals("DationTS")) {
                    ST specifyDation = m_group
                            .getInstanceOf("SpecificationSystemDationTS");
                    specifyDation.add("name", identifierDenotationList.get(i));
                    dationSpecifications.add("decl", specifyDation);

                } else if (dationClass.equals("DationPG")) {
                    ST specifyDation = m_group
                            .getInstanceOf("SpecificationSystemDationPG");
                    specifyDation.add("name", identifierDenotationList.get(i));
                    dationSpecifications.add("decl", specifyDation);
                } else if (dationClass.equals("DationRW")) {
                    ST specifyDation = m_group
                            .getInstanceOf("SpecificationSystemDationRW");
                    specifyDation.add("name", identifierDenotationList.get(i));
                    dationSpecifications.add("decl", specifyDation);
                }
            }
        }

        return dationSpecifications;
    }

    @Override
    public ST visitDationDeclaration(
            SmallPearlParser.DationDeclarationContext ctx) {
        ST dationDeclarations = m_group.getInstanceOf("DationDeclarations");
        ST typeDation = m_group.getInstanceOf("TypeDation");
        dationDeclarations.add("decl",
                visitIdentifierDenotation(ctx.identifierDenotation()));
        typeDation = getTypeDation(ctx.typeDation(), getDationClass(ctx
                .typeDation().classAttribute()));

        ST typology = m_group.getInstanceOf("Typology");
        ST accessAttributes = m_group.getInstanceOf("AccessAttributes");

        if (ctx.typeDation().typology() != null) {
            m_tfuRecord = -1;
            typology = visitTypology(ctx.typeDation().typology());
        }

        if (ctx.typeDation().accessAttribute() != null) {
            accessAttributes = visitAccessAttribute(ctx.typeDation().accessAttribute());
        }

        if (ctx.globalAttribute() != null) {
            visitGlobalAttribute(ctx.globalAttribute());
        }

        ArrayList<String> identifierDenotationList = null;

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.IdentifierDenotationContext) {
                    identifierDenotationList = getIdentifierDenotation((SmallPearlParser.IdentifierDenotationContext) c);
                    getIdentifierDenotation((SmallPearlParser.IdentifierDenotationContext) c);
                }
            }

            for (int i = 0; i < identifierDenotationList.size(); i++) {
                ST v = m_group.getInstanceOf("DationDeclaration");
                v.add("name", identifierDenotationList.get(i));
                v.add("TypeDation", typeDation);

                if (ctx.typeDation().typology() != null) {
                    typology.add("name", identifierDenotationList.get(i));
                    v.add("Typology", typology);
                    if (m_tfuRecord > 0) {
                        v.add("tfu", m_tfuRecord);
                    }
                }

                v.add("Id", ctx.ID().getText());
                v.add("Dation", getDationClass(ctx.typeDation()
                        .classAttribute()));

                if (ctx.typeDation().accessAttribute() != null) {
                    typeDation.add("AccessAttribute", accessAttributes);
                }

                if (ctx.typeDation().typology() != null) {
                    typeDation.add("Dim", identifierDenotationList.get(i));
                }

                dationDeclarations.add("decl", v);
            }
        }

        return dationDeclarations;
    }

    @Override
    public ST visitTypeDation(SmallPearlParser.TypeDationContext ctx) {
        ST st = m_group.getInstanceOf("TypeDation");
        ST sourceSinkAttributte = m_group.getInstanceOf("SourceSinkAttribute");
        sourceSinkAttributte.add("attribute", ctx.sourceSinkAttribute()
                .getText());
        st.add("SourceSinkAttribute", sourceSinkAttributte);

        if (ctx.classAttribute() != null) {
            st.add("ClassAttribute", getClassAttribute(ctx.classAttribute()));
        }

        return st;
    }

    private ST getTypeDation(SmallPearlParser.TypeDationContext ctx,
                             String dationClass) {
        ST st = m_group.getInstanceOf("TypeDation");
        ST sourceSinkAttributte = m_group.getInstanceOf("SourceSinkAttribute");
        sourceSinkAttributte.add("attribute", ctx.sourceSinkAttribute()
                .getText());
        st.add("SourceSinkAttribute", sourceSinkAttributte);

        if (dationClass.equals("DationRW") && ctx.classAttribute() != null) {
            st.add("ClassAttribute", getClassAttribute(ctx.classAttribute()));
        }

        return st;
    }


    @Override
    public ST visitTypology(SmallPearlParser.TypologyContext ctx) {
        ST st = m_group.getInstanceOf("Typology");
        // let's define the three dimension values with impossible preset
        // 
        int d1 = -1;
        int d2 = -1;
        int d3 = -1;

        for (ParseTree c : ctx.children) {
            if (c instanceof SmallPearlParser.Dimension1StarContext) {
                st.add("DIM1", -1);
                st.add("DIM1Unlimited", 1);
            } else if (c instanceof SmallPearlParser.Dimension1IntegerContext) {
                d1 = CommonUtils.getConstantFixedExpression(((SmallPearlParser.Dimension1IntegerContext) c)
                        .constantFixedExpression(), m_currentSymbolTable);
                st.add("DIM1", d1);
            } else if (c instanceof SmallPearlParser.Dimension2IntegerContext) {
                d2 = CommonUtils.getConstantFixedExpression(((SmallPearlParser.Dimension2IntegerContext) c)
                        .constantFixedExpression(), m_currentSymbolTable);
                st.add("DIM2", d2);
            } else if (c instanceof SmallPearlParser.Dimension3IntegerContext) {
                d3 = CommonUtils.getConstantFixedExpression(((SmallPearlParser.Dimension3IntegerContext) c)
                        .constantFixedExpression(), m_currentSymbolTable);
                st.add("DIM3", d3);
            }
        }

        if (ctx.tfu() != null) {
            // we have TFU specified --> let's get the last given dimension
            if (d3 > 0) {
                m_tfuRecord = d3;
            } else if (d2 > 0) {
                m_tfuRecord = d2;
            } else {
                m_tfuRecord = d1;
            }
        }
        return st;
    }

    // OpenPEARL Language Report: 11.5
    //
    // | BASIC | ALPHIC | ALL / type
    // -------+---------------+----------------+----------------
    // SYSTEM | SystemDationB | SystemDationNB | SystemDationNB
    // | DationTS | DationPG | DationRW
    // -------+---------------+----------------+----------------

    private String getDationClass(SmallPearlParser.ClassAttributeContext ctx)
            throws InternalCompilerErrorException {
        if (ctx.systemDation() != null) {
            if (ctx.basicDation() != null) {
                return "SystemDationB";
            }

            if (ctx.alphicDation() != null) {
                return "SystemDationNB";
            }

            return "SystemDationNB";
        }

        if (ctx.basicDation() != null) {
            return "DationTS";
        }

        if (ctx.alphicDation() != null) {
            return "DationPG";
        }

        if (ctx.typeOfTransmissionData() != null) {
            if (ctx.typeOfTransmissionData() instanceof SmallPearlParser.TypeOfTransmissionDataALLContext) {
                return "DationRW";
            }

            if (ctx.typeOfTransmissionData() instanceof SmallPearlParser.TypeOfTransmissionDataSimpleTypeContext) {
                return "DationRW";
            }
        }

        throw new InternalCompilerErrorException(ctx.getText(),
                ctx.start.getLine(), ctx.start.getCharPositionInLine());
    }

    private ST getStepSize(SmallPearlParser.ClassAttributeContext ctx) {
        ST st = m_group.getInstanceOf("StepSize");

        st.add("type", "Fixed");
        st.add("size", "31");

        return st;
    }

    private ST getClassAttribute(SmallPearlParser.ClassAttributeContext ctx) {
        ST st = m_group.getInstanceOf("ClassAttribute");

        if (ctx.systemDation() != null) {
            st.add("system", "1");
        }

        if (ctx.alphicDation() != null) {
            st.add("alphic", "1");
        } else if (ctx.basicDation() != null) {
            st.add("basic", "1");
        }

        if (ctx.typeOfTransmissionData() != null) {
            st.add("attribute",
                    getTypeOfTransmissionData(ctx.typeOfTransmissionData()));
        }

        return st;
    }

    private ST getTypeOfTransmissionData(
            SmallPearlParser.TypeOfTransmissionDataContext ctx) {
        ST st = m_group.getInstanceOf("TypeOfTransmissionData");

        if (ctx instanceof SmallPearlParser.TypeOfTransmissionDataALLContext) {
            st.add("all", "1");
        } else if (ctx instanceof SmallPearlParser.TypeOfTransmissionDataSimpleTypeContext) {
            SmallPearlParser.TypeOfTransmissionDataSimpleTypeContext c = (SmallPearlParser.TypeOfTransmissionDataSimpleTypeContext) ctx;
            st.add("type", visitSimpleType(c.simpleType()));
        }

        return st;
    }


    @Override
    public ST visitAccessAttribute(SmallPearlParser.AccessAttributeContext ctx) {
        ST st = m_group.getInstanceOf("AccessAttribute");

        for (ParseTree c : ctx.children) {
            st.add("attribute", c.getText());
        }

        return st;
    }

    @Override
    public ST visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
        ST st = m_group.getInstanceOf("block_statement");

        this.m_currentSymbolTable = m_symbolTableVisitor
                .getSymbolTablePerContext(ctx);

        for (ParseTree c : ctx.children) {
            if (c instanceof SmallPearlParser.ScalarVariableDeclarationContext) {
                st.add("code",
                        visitScalarVariableDeclaration((SmallPearlParser.ScalarVariableDeclarationContext) c));
            } else if (c instanceof SmallPearlParser.ArrayVariableDeclarationContext) {
                st.add("code",
                        visitArrayVariableDeclaration((SmallPearlParser.ArrayVariableDeclarationContext) c));
            } else if (c instanceof SmallPearlParser.StructVariableDeclarationContext) {
                st.add("code",
                        visitStructVariableDeclaration((SmallPearlParser.StructVariableDeclarationContext) c));
            } else if (c instanceof SmallPearlParser.StatementContext) {
                st.add("code",
                        visitStatement((SmallPearlParser.StatementContext) c));
            }
        }

        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();

        return st;
    }

    @Override
    public ST visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
        ST st = m_group.getInstanceOf("LoopStatement");
        TypeDefinition fromType = null;
        TypeDefinition toType = null;
        TypeDefinition byType = null;
        Integer rangePrecision = 0;
        Boolean loopCounterNeeded = false;

        this.m_currentSymbolTable = m_symbolTableVisitor
                .getSymbolTablePerContext(ctx);

        st.add("srcLine", ctx.start.getLine());

        if (ctx.loopStatement_for() != null) {
          SymbolTableEntry se = m_currentSymbolTable.lookup(ctx.loopStatement_for().ID().toString());
          rangePrecision = ((TypeFixed)(((VariableEntry)se).getType())).getPrecision();
  
            st.add("variable", ctx.loopStatement_for().ID().toString());
            loopCounterNeeded = true;
        }

        if (ctx.loopStatement_from() != null) {
            boolean old_map_to_const = m_map_to_const;

            fromType = m_ast.lookupType(ctx.loopStatement_from().expression());

            m_map_to_const = true;
            st.add("from", visitAndDereference(ctx.loopStatement_from().expression()));
            m_map_to_const = old_map_to_const;
        }

        if (ctx.loopStatement_to() != null) {
            boolean old_map_to_const = m_map_to_const;

            toType = m_ast.lookupType(ctx.loopStatement_to().expression());

            m_map_to_const = true;
            st.add("to", visitAndDereference(ctx.loopStatement_to().expression()));
            m_map_to_const = old_map_to_const;

            loopCounterNeeded = true;
        }
        
        if (ctx.loopStatement_by() != null) {
          boolean old_map_to_const = m_map_to_const;
          byType = m_ast.lookupType(ctx.loopStatement_by().expression());
          m_map_to_const = true;
          st.add("by", visitAndDereference(ctx.loopStatement_by().expression()));
          m_map_to_const = old_map_to_const;

          loopCounterNeeded = true;
        }
        
        if (rangePrecision == 0) {
          // we have no loop control variable!
          // derive the precision from from/to and by

          if (fromType != null && toType != null) {
            rangePrecision = Math.max(((TypeFixed) fromType).getPrecision(),
                    ((TypeFixed) toType).getPrecision());
          //  st.add("fromPrecision", rangePrecision);
          //  st.add("toPrecision", rangePrecision);
            loopCounterNeeded = true;
          } else if (fromType != null && toType == null) {
            rangePrecision = ((TypeFixed) fromType).getPrecision();
         //   st.add("fromPrecision", ((TypeFixed) fromType).getPrecision());
          } else if (fromType == null && toType != null) {
            rangePrecision = ((TypeFixed) toType).getPrecision();
          //  st.add("toPrecision", ((TypeFixed) toType).getPrecision());
            loopCounterNeeded = true;
          }
          if (byType != null) {
            rangePrecision = Math.max(rangePrecision,((TypeFixed) byType).getPrecision());
          }
        }


        st.add("precision", rangePrecision);

//        if (ctx.loopStatement_by() != null) {
//            boolean old_map_to_const = m_map_to_const;
//
//            m_map_to_const = true;
//            st.add("by", visitAndDereference(ctx.loopStatement_by().expression()));
//            st.add("byPrecision", rangePrecision);
//            m_map_to_const = old_map_to_const;
//
//            loopCounterNeeded = true;
//        }

        if (ctx.loopStatement_while() != null
                && ctx.loopStatement_while().expression() != null) {
            ST wc = visitAndDereference(ctx.loopStatement_while().expression());
            //String s = wc.toString();
            if (wc.toString().length() > 0) {
                ST cast = m_group.getInstanceOf("CastBitToBoolean");
                cast.add("name", wc);
                st.add("while_cond", cast);
            }
        }

        for (int i = 0; i < ctx.loopBody().scalarVariableDeclaration().size(); i++) {
            st.add("body", visitScalarVariableDeclaration(ctx.loopBody()
                    .scalarVariableDeclaration(i)));
        }

        for (int i = 0; i < ctx.loopBody().arrayVariableDeclaration().size(); i++) {
            st.add("body", visitArrayVariableDeclaration(ctx.loopBody()
                    .arrayVariableDeclaration(i)));
        }

        for (int i = 0; i < ctx.loopBody().structVariableDeclaration().size(); i++) {
            st.add("body", visitStructVariableDeclaration(ctx.loopBody().structVariableDeclaration(i)));
        }

        for (int i = 0; i < ctx.loopBody().statement().size(); i++) {
            st.add("body", visitStatement(ctx.loopBody().statement(i)));
        }

        if (ctx.loopStatement_end().ID() != null) {
            st.add("label_end", ctx.loopStatement_end().ID().toString());
        }

        if ((ctx.loopStatement_to() != null)
                || (ctx.loopStatement_for() != null)
                || (ctx.loopStatement_by() != null)) {
            st.add("countLoopPass", 1);
        }

        if (loopCounterNeeded) {
            st.add("GenerateLoopCounter", 1);
        }

        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();

        return st;
    }

    @Override
    public ST visitExitStatement(SmallPearlParser.ExitStatementContext ctx) {
        ST st = m_group.getInstanceOf("ExitStatement");

        if (ctx.ID() != null) {
            st.add("label", ctx.ID().toString());
        }

        return st;
    }

    @Override
    public ST visitProcedureDeclaration(
            SmallPearlParser.ProcedureDeclarationContext ctx) {
        ST st = m_group.getInstanceOf("ProcedureDeclaration");
        st.add("id", ctx.ID().getText());

        SymbolTableEntry se = m_currentSymbolTable.lookup(ctx.ID().toString());

        this.m_currentSymbolTable = m_symbolTableVisitor
                .getSymbolTablePerContext(ctx);

        for (ParseTree c : ctx.typeProcedure().children) {
            //if (c instanceof SmallPearlParser.ProcedureBodyContext) {
            //    st.add("body",
            //            visitProcedureBody((SmallPearlParser.ProcedureBodyContext) c));
            //} else
            if (c instanceof SmallPearlParser.ResultAttributeContext) {
                st.add("resultAttribute", getResultAttributte((ProcedureEntry) se));

            } else if (c instanceof SmallPearlParser.ListOfFormalParametersContext) {
                st.add("listOfFormalParameters",
                        visitListOfFormalParameters((SmallPearlParser.ListOfFormalParametersContext) c));
            }
        }
        
        if (ctx.globalAttribute()!= null) {
          visit(ctx.globalAttribute());
        }
        
        st.add("body", visit(ctx.procedureBody()));
        
        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return st;
    }

    private ST getResultAttributte(ProcedureEntry pe) {
        ST st = m_group.getInstanceOf("ResultAttribute");
        st.add("type", pe.getResultType().toST(m_group));
        m_resultType = pe.getResultType();
        return st;
    }
//    private ST getProcedureSpecification(
//            SmallPearlParser.ProcedureDeclarationContext ctx) {
//        ST st = m_group.getInstanceOf("ProcedureSpecification");
//        st.add("id", ctx.ID().getText());
//
//        for (ParseTree c : ctx.children) {
//            if (c instanceof SmallPearlParser.ResultAttributeContext) {
//                st.add("resultAttribute",
//                        visitResultAttribute((SmallPearlParser.ResultAttributeContext) c));
//            } else if (c instanceof SmallPearlParser.GlobalAttributeContext) {
//                st.add("globalAttribute",
//                        visitGlobalAttribute((SmallPearlParser.GlobalAttributeContext) c));
//            } else if (c instanceof SmallPearlParser.ListOfFormalParametersContext) {
//                st.add("listOfFormalParameters",
//                        visitListOfFormalParameters((SmallPearlParser.ListOfFormalParametersContext) c));
//            }
//        }
//
//        return st;
//    }

    @Override
    public ST visitListOfFormalParameters(
            SmallPearlParser.ListOfFormalParametersContext ctx) {
        ST st = m_group.getInstanceOf("ListOfFormalParameters");

        if (ctx != null) {
            for (int i = 0; i < ctx.formalParameter().size(); i++) {
                st.add("FormalParameters",
                        visitFormalParameter(ctx.formalParameter(i)));
            }
        }

        return st;
    }

    @Override
    public ST visitFormalParameter(SmallPearlParser.FormalParameterContext ctx) {
        ST st = m_group.getInstanceOf("FormalParameters");

        if (ctx != null) {
            for (int i = 0; i < ctx.ID().size(); i++) {
                boolean treatArray = false;
                boolean treatStructure = false;
                String typeName = "";

                ST param = m_group.getInstanceOf("FormalParameter");

                // test if we have an parameter of type array
                SymbolTableEntry se = m_currentSymbolTable.lookup(ctx.ID(i).toString());

                if (se instanceof VariableEntry) {
                    VariableEntry ve = (VariableEntry) se;

                    if (ve.getType() instanceof TypeArray) {
                        treatArray = true;
                    } else if (ve.getType() instanceof TypeStructure) {
                        treatStructure = true;
                        typeName = ((TypeStructure) ve.getType()).getStructureName();
                    }
                }

                if (treatArray) {
                    param.add("isArrayDescriptor", "");
                    String s = param.toString();
                    param.add("isArray", "");
                }
                param.add("id", ctx.ID(i));

                if (treatStructure) {
                    param.add("type", typeName);
                } else {
                    param.add("type", visitParameterType(ctx.parameterType()));
                }
                if (ctx.assignmentProtection() != null) {
                    param.add("assignmentProtection", "");
                }

                if (ctx.passIdentical() != null) {
                    param.add("passIdentical", "");
                }

                st.add("FormalParameter", param);

            }
        }

        return st;
    }

    @Override
    public ST visitParameterType(SmallPearlParser.ParameterTypeContext ctx) {
        ST st = m_group.getInstanceOf("ParameterType");

        for (ParseTree c : ctx.children) {
            if (c instanceof SmallPearlParser.SimpleTypeContext) {
                st.add("type", visitSimpleType(ctx.simpleType()));
            } else if (c instanceof SmallPearlParser.TypeReferenceContext) {
                st.add("type", visitTypeReference(ctx.typeReference()));
            } else if (c instanceof SmallPearlParser.TypeStructureContext) {
                st.add("type", visitTypeStructure(ctx.typeStructure()));
            } else {
                System.err.println("CppCodeGen:visitParameterType: untreated type " + c.getClass().getCanonicalName());
            }
        }

        return st;
    }

    @Override
    public ST visitProcedureBody(SmallPearlParser.ProcedureBodyContext ctx) {
        ST st = m_group.getInstanceOf("ProcedureBody");

        if (ctx != null && ctx.children != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.ScalarVariableDeclarationContext) {
                    st.add("declarations",
                            visitScalarVariableDeclaration((SmallPearlParser.ScalarVariableDeclarationContext) c));
//                  (SmallPearlParser.ScalarVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.ArrayVariableDeclarationContext) {
                    st.add("declarations",
                            visitArrayVariableDeclaration((SmallPearlParser.ArrayVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.StructVariableDeclarationContext) {
                    st.add("declarations",
                            visitStructVariableDeclaration((SmallPearlParser.StructVariableDeclarationContext) c));
                } else if (c instanceof SmallPearlParser.StatementContext) {
                    st.add("statements",
                            visitStatement((SmallPearlParser.StatementContext) c));
                } else if (c instanceof SmallPearlParser.DationDeclarationContext) {
                    new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }
            }
        }

        return st;
    }

    @Override
    public ST visitGlobalAttribute(SmallPearlParser.GlobalAttributeContext ctx) {
        ST st = m_group.getInstanceOf("GlobalAttribute");

        st.add("id", ctx.ID().getText());

        return st;
    }

    @Override
    public ST visitResultAttribute(SmallPearlParser.ResultAttributeContext ctx) {
        ST st = m_group.getInstanceOf("ResultAttribute");
        st.add("resultType", visitResultType(ctx.resultType()));
        ASTAttribute attr = m_ast.lookup(ctx);
        if (attr != null) {
            m_resultType = attr.getType();
        }
        return st;
    }

    @Override
    public ST visitResultType(SmallPearlParser.ResultTypeContext ctx) {
        ST st = m_group.getInstanceOf("ResultType");

        for (ParseTree c : ctx.children) {
            if (c instanceof SmallPearlParser.SimpleTypeContext) {
                st.add("type", visitSimpleType(ctx.simpleType()));
            } else if (c instanceof SmallPearlParser.TypeReferenceContext) {
                st.add("type", visitTypeReference(ctx.typeReference()));
            } else if (c instanceof SmallPearlParser.TypeStructureContext) {
                st.add("type", visitTypeStructure(ctx.typeStructure()));
            }
        }

        return st;
    }

    @Override
    public ST visitUsername_declaration(
            SmallPearlParser.Username_declarationContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public ST visitUsername_declaration_without_data_flow_direction(
            SmallPearlParser.Username_declaration_without_data_flow_directionContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public ST visitUsername_declaration_with_data_flow_direction(
            SmallPearlParser.Username_declaration_with_data_flow_directionContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public ST visitUserConfigurationWithAssociation(
            SmallPearlParser.UserConfigurationWithAssociationContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public ST visitUserConfigurationWithoutAssociation(
            SmallPearlParser.UserConfigurationWithoutAssociationContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public ST visitUsername_parameters(
            SmallPearlParser.Username_parametersContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public ST visitTOFIXEDExpression(
            SmallPearlParser.TOFIXEDExpressionContext ctx) {
        TypeDefinition op = m_ast.lookupType(ctx.expression());

        if (op instanceof TypeBit) {
            ST st = m_group.getInstanceOf("BITSTOFIXED");
            st.add("operand", visitAndDereference(ctx.expression()));
            return st;
        } else if (op instanceof TypeChar) {
            ST st = m_group.getInstanceOf("CHARACTERSTOFIXED");
            st.add("operand", visitAndDereference(ctx.expression()));
            return st;
        }

        return null;
    }

    @Override
    public ST visitTOFLOATExpression(
            SmallPearlParser.TOFLOATExpressionContext ctx) {
        ST st = m_group.getInstanceOf("TOFLOAT");
        TypeDefinition op = m_ast.lookupType(ctx.expression());

        st.add("operand", visitAndDereference(ctx.expression()));

        if (op instanceof TypeFixed) {
            TypeFixed fixedValue = (TypeFixed) op;
            int precision = 0;

            if (fixedValue.getPrecision() <= Defaults.FLOAT_SHORT_PRECISION) {
                precision = Defaults.FLOAT_SHORT_PRECISION;
            } else {
                precision = Defaults.FLOAT_LONG_PRECISION;
            }
            st.add("precision", (precision));
        } else {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        return st;
    }

    @Override
    public ST visitTOBITExpression(SmallPearlParser.TOBITExpressionContext ctx) {
        TypeDefinition op = m_ast.lookupType(ctx.expression());

        ST st = m_group.getInstanceOf("TOBIT");
        st.add("operand", visitAndDereference(ctx.expression()));

        if (op instanceof TypeFixed) {
            st.add("noOfBits", ((TypeFixed) op).getPrecision() + 1);
        } else {
            throw new InternalCompilerErrorException(ctx.getText(),
                    ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        return st;
    }

    @Override
    public ST visitTOCHARExpression(SmallPearlParser.TOCHARExpressionContext ctx) {
        TypeDefinition op = m_ast.lookupType(ctx.expression());

        if (op instanceof TypeFixed) {
            ST st = m_group.getInstanceOf("FIXEDTOCHARACTER");
            st.add("operand", visitAndDereference(ctx.expression()));
            return st;
        }

        return null;
    }

    @Override
    public ST visitCONTExpression(SmallPearlParser.CONTExpressionContext ctx) {
        TypeDefinition op = m_ast.lookupType(ctx.expression());

        // the derefenciation occurs implicit in getExpression
        ST st = m_group.getInstanceOf("CONT");
        st.add("operand", visit(ctx.expression()));
//        ST st = getExpression(ctx.expression());

        return st;
    }

    @Override
    public ST visitArrayVariableDeclaration(
            SmallPearlParser.ArrayVariableDeclarationContext ctx) {
        ST declarations = m_group.getInstanceOf("ArrayVariableDeclarations");

        if (m_verbose > 0) {
            System.out
                    .println("CppCodeGeneratorVisitor: visitArrayVariableDeclaration");
        }

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.ArrayDenotationContext) {
                    declarations
                            .add("declarations",
                                    visitArrayDenotation((SmallPearlParser.ArrayDenotationContext) c));
                }
            }
        }

        return declarations;
    }

    @Override
    public ST visitArrayDenotation(SmallPearlParser.ArrayDenotationContext ctx) {
        ST declarations = m_group.getInstanceOf("ArrayVariableDeclarations");

        for (int i = 0; i < ctx.ID().size(); i++) {
            SymbolTableEntry entry = m_currentSymbolTable.lookup(ctx.ID()
                    .get(i).toString());

            if (entry == null || !(entry instanceof VariableEntry)) {
                throw new InternalCompilerErrorException(ctx.getText(),
                        ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

            VariableEntry variableEntry = (VariableEntry) entry;

            if (variableEntry.getType() instanceof TypeArray) {
                ArrayList<ST> initElementList = null;

                ST declaration = m_group
                        .getInstanceOf("ArrayVariableDeclaration");

                declaration.add("name", variableEntry.getName());

                if (variableEntry.getType() instanceof TypeArray) {
                    TypeArray type = (TypeArray) variableEntry.getType();
                    declaration.add("type", type.getBaseType().toST(m_group));
                } else {
                    throw new InternalCompilerErrorException(ctx.getText(),
                            ctx.start.getLine(),
                            ctx.start.getCharPositionInLine());
                }

                declaration.add("assignmentProtection",
                        variableEntry.getAssigmentProtection());
                declaration.add("totalNoOfElements", ((TypeArray) variableEntry
                        .getType()).getTotalNoOfElements());

                if (ctx.initialisationAttribute() != null) {
                    declaration.add(
                            "initElements",
                            getArrayInitialisationAttribute(variableEntry));
                }

                declarations.add("declarations", declaration);
            }
        }

        return declarations;
    }


    /**
     * create code for CONVERT .. TO
     * we can use lot of the PUT-stuff.
     * We must remove the "dation" tag from the string template,
     * since the code template creates an own element for the conversions
     */
    @Override
    public ST visitConvertToStatement(
            SmallPearlParser.ConvertToStatementContext ctx) {
        //ST st = m_group.getInstanceOf("ConvertToStatement");
        //String convertToString = "";

        if (m_verbose > 0) {
            System.out
                    .println("CppCodeGeneratorVisitor: visitConvertToStatement");
        }

        //String s = ctx.name().getText();

        ST stmt = m_group.getInstanceOf("iojob_convertTo_statement");
        stmt.add("char_string", getUserVariable(ctx.name().getText()));


        // this flag is set to true if at least one non static format parameter is detected
        m_isNonStatic = false;

        // this should never occur, since this is checked in the semantic analysis
        // in CheckIOStatements
        ST formatList = m_group.getInstanceOf("iojob_formatlist");
        if (ctx.listOfFormatPositions() == null) {
            ST fmt = m_group.getInstanceOf("iojob_list_format");
            formatList.add("formats", fmt);
        } else {

            for (int i = 0; i < ctx.listOfFormatPositions().formatPosition().size(); i++) {
                addFormatPositionToFormatList(formatList, ctx.listOfFormatPositions().formatPosition(i));
            }
        }

        stmt.add("formatlist", formatList);
        if (!m_isNonStatic) {
            stmt.add("format_list_is_static", "1");
        }

        // create list of data elements
        ST dataList = getIojobDataList(ctx.ioDataList());

        stmt.add("datalist", dataList);


        return stmt;
    }


    @Override
    public ST visitConvertFromStatement(
            SmallPearlParser.ConvertFromStatementContext ctx) {

        ST stmt = m_group.getInstanceOf("iojob_convertFrom_statement");
        stmt.add("char_string", visitAndDereference(ctx.expression()));

        // this flag is set to true if at least one non static format parameter is detected
        m_isNonStatic = false;

        // this should never occur, since this is checked in the semantic analysis
        // in CheckIOStatements
        ST formatList = m_group.getInstanceOf("iojob_formatlist");
        if (ctx.listOfFormatPositions() == null) {
            ST fmt = m_group.getInstanceOf("iojob_list_format");
            formatList.add("formats", fmt);
        } else {

            for (int i = 0; i < ctx.listOfFormatPositions().formatPosition().size(); i++) {
                addFormatPositionToFormatList(formatList, ctx.listOfFormatPositions().formatPosition(i));
            }
        }

        stmt.add("formatlist", formatList);
        if (!m_isNonStatic) {
            stmt.add("format_list_is_static", "1");
        }

        // create list of data elements
        ST dataList = getIojobDataList(ctx.ioDataList());

        stmt.add("datalist", dataList);


        return stmt;
    }

    @Override
    public ST visitInterruptSpecification(
            SmallPearlParser.InterruptSpecificationContext ctx) {
        ST st = m_group.getInstanceOf("InterruptSpecifications");

        if (m_verbose > 0) {
            System.out
                    .println("CppCodeGeneratorVisitor: visitInterruptSpecification");
        }

        for (int i = 0; i < ctx.ID().size(); i++) {
            ST spec = m_group.getInstanceOf("InterruptSpecification");
            spec.add("id", ctx.ID(i));
            st.add("specs", spec);
        }
        return st;
    }

    @Override
    public ST visitInterrupt_statement(
            SmallPearlParser.Interrupt_statementContext ctx) {
        if (m_verbose > 0) {
            System.out
                    .println("CppCodeGeneratorVisitor: visitInterrupt_statement");
        }

        return visitChildren(ctx);
    }

    @Override
    public ST visitEnableStatement(SmallPearlParser.EnableStatementContext ctx) {
        ST st = m_group.getInstanceOf("EnableStatement");

        if (m_verbose > 0) {
            System.out.println("CppCodeGeneratorVisitor: visitEnableStatement");
        }

        st.add("id", visitAndDereference(ctx.name()));
        return st;
    }

    @Override
    public ST visitDisableStatement(SmallPearlParser.DisableStatementContext ctx) {
        ST st = m_group.getInstanceOf("DisableStatement");

        if (m_verbose > 0) {
            System.out
                    .println("CppCodeGeneratorVisitor: visitDisableStatement");
        }

        st.add("id", visitAndDereference(ctx.name()));
        return st;
    }

    @Override
    public ST visitTriggerStatement(SmallPearlParser.TriggerStatementContext ctx) {
        ST st = m_group.getInstanceOf("TriggerStatement");

        if (m_verbose > 0) {
            System.out
                    .println("CppCodeGeneratorVisitor: visitTriggerStatement");
        }

        st.add("id", visitAndDereference(ctx.name()));
        return st;
    }

    private ConstantValue getConstant(SmallPearlParser.ConstantContext ctx) {

        // constant :
        // sign? ( fixedConstant | floatingPointConstant )
        // | timeConstant
        // | durationConstant
        // | bitStringConstant
        // | StringLiteral
        // | 'NIL'
        return null;
    }

    /**
     * create an array descriptor according the definition of the variable 'array'
     * <p>
     * if the variable is a formal parameter, we create an anyonymous array descrptor
     * according the formal parameter's name
     * x: PROC( abc() FIXED --> data is storedat FIXED* data_abc
     * array descriptor is named 'Array* ad_abc'
     * <p>
     * if the variable is a real array, the array descriptor is named according the
     * arrays dimension
     *
     * @param array
     * @return
     * @note: array slices are not treated
     */
    private String getArrayDescriptor(VariableEntry var) {
        ParserRuleContext c = var.getCtx();
        String s = null;
        if (c instanceof FormalParameterContext) {
            s = "ad_" + var.getName();
        } else {
            TypeArray type = (TypeArray) var.getType();
            ArrayDescriptor array_descriptor = new ArrayDescriptor(
                    type.getNoOfDimensions(), type.getDimensions());
            s = array_descriptor.getName();
        }
        return s;
    }

    @Override
    public ST visitStructVariableDeclaration(SmallPearlParser.StructVariableDeclarationContext ctx) {
        Log.debug("CppCodeGeneratorVisitor:visitStructVariableDeclaration:ctx" + CommonUtils.printContext(ctx));
        ST st = m_group.getInstanceOf("StructureVariableDeclaration");

        for (int i = 0; i < ctx.structureDenotation().size(); i++) {
            String id = ctx.structureDenotation(i).ID().getText();

            SymbolTableEntry symbolTableEntry = m_currentSymbolTable.lookupLocal(ctx.structureDenotation(i).ID().getText());

            if (symbolTableEntry != null && symbolTableEntry instanceof VariableEntry) {
                VariableEntry variable = (VariableEntry) symbolTableEntry;

                if ( variable.getType() instanceof TypeStructure) {
                    TypeStructure typ = (TypeStructure) variable.getType();
                    st.add("name", id);
                    st.add("type", typ.getStructureName());
                } else if  ( variable.getType() instanceof TypeArray) {
                    TypeArray array = (TypeArray) variable.getType();

                    if ( array.getBaseType() instanceof TypeStructure) {
                        TypeStructure typ = (TypeStructure) array.getBaseType();
                        st.add("name", id);
                        st.add("type", typ.getStructureName());
                    }
                }
            }
        }

        return st;
    }



    private ST traverseNameForStruct(SmallPearlParser.NameContext ctx, TypeDefinition type) {
        ST st =  m_group.getInstanceOf("Name");
        st.add("id", ctx.ID().getText());

        if ( ctx.name() != null ) {
            reVisitName(ctx.name(), type, st);
        }

        return st;
    }

    /**
     * iterate over name recursion levels
     */
    private Void reVisitName(SmallPearlParser.NameContext ctx, TypeDefinition type, ST st) {
        Log.debug("CppCodeGeneratorVisitor:reVisitName:ctx" + CommonUtils.printContext(ctx));

        if ( type instanceof TypeStructure) {
            TypeStructure struct = (TypeStructure) type;
            StructureComponent component = struct.lookup(ctx.ID().getText());
            st.add("name", component.m_alias);

            if ( ctx.name() != null ) {
                if ( component.m_type instanceof TypeStructure) {
                    TypeStructure subStruct = (TypeStructure)component.m_type;
                    reVisitName(ctx.name(), subStruct, st);
                }
            }
        }

        return null;
    }
 
    
    /*
     * this method obtains the ST of the given context
     * if the context is of type TypeReference an implicit CONT is added
     * if the variable is a reference to an array and indices are given, 
     *    the effective data element is returned
     * 
     */
    private ST visitAndDereference(ParserRuleContext ctx) {
      String s = ctx.getText();
      ST st = visit(ctx);
      ASTAttribute attr = m_ast.lookup(ctx);
      VariableEntry ve = attr.getVariable();
      if (ve != null) {
        if (ve.getType() instanceof TypeReference) {
          if ( ( (TypeReference)(ve.getType())  ).getBaseType() instanceof TypeArraySpecification) {
            if (ctx instanceof BaseExpressionContext) {
              BaseExpressionContext bc = (BaseExpressionContext)ctx;
              if (bc.primaryExpression() != null && bc.primaryExpression().name() != null) {
                NameContext n =  bc.primaryExpression().name() ;
                if (n.listOfExpression() != null) {
                  ST arrayRef = m_group.getInstanceOf("RefArrayReadWrite");
                  arrayRef.add("name",ve.getName());
                  arrayRef.add("indices", visitListOfExpression(n.listOfExpression()));
                  st = arrayRef;
                }
              }
            }
          } else if ( ( (TypeReference)(ve.getType())  ).getBaseType() instanceof TypeProcedure) {
            BaseExpressionContext bc = (BaseExpressionContext)ctx;
            if (bc.primaryExpression() != null && bc.primaryExpression().name() != null) {
              NameContext n =  bc.primaryExpression().name() ;
              if (n.listOfExpression() != null) {
                // FunctionCall(callee,ListOfActualParameters)
                ST functionCall = m_group.getInstanceOf("FunctionCall");
                ST deref = m_group.getInstanceOf("CONT");
                deref.add("operand",getUserVariable(ve.getName()));
                functionCall.add("callee",deref);
                functionCall.add("ListOfActualParameters", visitListOfExpression(n.listOfExpression() ) );
                st = functionCall;
              }
            }
          }
        }
      }
      TypeDefinition t = attr.getType();
      if (t instanceof TypeReference) {
        ST deref = m_group.getInstanceOf("CONT");
        deref.add("operand", st);
        st = deref;
      }
      return st;
    }
    
    private String nextTempVarName() {
      int index = m_tempVariableNbr.lastElement();
      
      m_tempVariableNbr.remove(m_tempVariableNbr.size()-1);
      m_tempVariableNbr.add(new Integer(index+1));
      return "tmp_"+index;
    }

}
