/*
 *  Copyright (c) 2012-2020 Marcel Schaible
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
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.smallpearl.compiler.SmallPearlParser.*;
import org.smallpearl.compiler.Exception.*;
import org.smallpearl.compiler.SymbolTable.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Stack;

/**
 * run through the complete AST and create symbols for all elements which are need by
 * - semantic checks
 * - code generation
 * - additional ASTAttributes may become generated for suitable information
 */

public class SymbolTableVisitor extends SmallPearlBaseVisitor<Void> implements SmallPearlVisitor<Void> {

    private int m_verbose;
    private boolean m_debug;

    public SymbolTable symbolTable;
    private SymbolTableEntry m_currentEntry;
    private SymbolTable m_currentSymbolTable;
    private TypeStructure m_currentTypeStructure;
    private LinkedList<LinkedList<SemaphoreEntry>> m_listOfTemporarySemaphoreArrays;
    private LinkedList<LinkedList<BoltEntry>> m_listOfTemporaryBoltArrays;
    private LinkedList<ArrayDescriptor> m_listOfArrayDescriptors;
    private LinkedList<TypeStructure> m_listOfStructureDeclarations;

    private TypeDefinition m_type;

    private ParseTreeProperty<SymbolTable> m_symboltablePerContext = null;
    private ConstantPool m_constantPool = null;

    private TypeStructure m_typeStructure = null;
    private StructureComponent m_structureComponent = null;

    public SymbolTableVisitor(int verbose, ConstantPool constantPool) {

        m_debug = false;
        m_verbose = verbose;

        Log.debug("SymbolTableVisitor:Building new symbol table");

        this.symbolTable = new org.smallpearl.compiler.SymbolTable.SymbolTable();
        this.m_listOfTemporarySemaphoreArrays = new LinkedList<LinkedList<SemaphoreEntry>>();
        this.m_listOfTemporaryBoltArrays = new LinkedList<LinkedList<BoltEntry>>();
        this.m_listOfArrayDescriptors = new LinkedList<ArrayDescriptor>();
        this.m_symboltablePerContext = new ParseTreeProperty<SymbolTable>();
        this.m_constantPool = constantPool;
        this.m_currentTypeStructure = null;
//TODO: MS REMOVE?:        this.m_currentStructureEntry = null;
        this.m_typeStructure = null;
    }

    @Override
    public Void visitModule(SmallPearlParser.ModuleContext ctx) {
        Log.debug("SymbolTableVisitor:visitModule:ctx" + CommonUtils.printContext(ctx));

        org.smallpearl.compiler.SymbolTable.ModuleEntry moduleEntry = new org.smallpearl.compiler.SymbolTable.ModuleEntry(ctx.ID().getText(), ctx, null);
        this.m_currentSymbolTable = this.symbolTable.newLevel(moduleEntry);
        this.m_symboltablePerContext.put(ctx, this.m_currentSymbolTable);

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.System_partContext) {
                    visitSystem_part((SmallPearlParser.System_partContext) c);
                } else if (c instanceof SmallPearlParser.Problem_partContext) {
                    visitProblem_part((SmallPearlParser.Problem_partContext) c);
                }
            }
        }


        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitProblem_part(SmallPearlParser.Problem_partContext ctx) {
        Log.debug("SymbolTableVisitor:visitProblem_part:ctx" + CommonUtils.printContext(ctx));

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.ScalarVariableDeclarationContext) {
                    visitScalarVariableDeclaration((SmallPearlParser.ScalarVariableDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.SemaDeclarationContext) {
                    visitSemaDeclaration((SmallPearlParser.SemaDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.BoltDeclarationContext) {
                    visitBoltDeclaration((SmallPearlParser.BoltDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.TaskDeclarationContext) {
                    visitTaskDeclaration((SmallPearlParser.TaskDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.DationSpecificationContext) {
                    visitDationSpecification((SmallPearlParser.DationSpecificationContext) c);
                } else if (c instanceof SmallPearlParser.DationDeclarationContext) {
                    visitDationDeclaration((SmallPearlParser.DationDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.ProcedureDeclarationContext) {
                    visitProcedureDeclaration((SmallPearlParser.ProcedureDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.LengthDefinitionContext) {
                    visitLengthDefinition((SmallPearlParser.LengthDefinitionContext) c);
                } else if (c instanceof SmallPearlParser.StructVariableDeclarationContext) {
                    visitStructVariableDeclaration((SmallPearlParser.StructVariableDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.ArrayVariableDeclarationContext) {
                    visitArrayVariableDeclaration((SmallPearlParser.ArrayVariableDeclarationContext) c);
                } else if (c instanceof SmallPearlParser.InterruptSpecificationContext) {
                    visitInterruptSpecification((SmallPearlParser.InterruptSpecificationContext) c);
                }
            }
        }

        return null;
    }

    @Override
    public Void visitTaskDeclaration(SmallPearlParser.TaskDeclarationContext ctx) {
        Boolean isMain = false;
        Boolean isGlobal = false;
        SmallPearlParser.PriorityContext priority = null;

        Log.debug("SymbolTableVisitor:visitTaskDeclaration:ctx" + CommonUtils.printContext(ctx));

        SymbolTableEntry entry = this.m_currentSymbolTable.lookup(ctx.ID().toString());
        if (entry != null) {
            throw new DoubleDeclarationException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        isMain = ctx.task_main() != null;
        if (ctx.priority() != null) {
            priority = ctx.priority();
        }

        TaskEntry taskEntry = new TaskEntry(ctx.ID().getText(), priority, isMain, isGlobal, ctx, this.m_currentSymbolTable);
        this.m_currentSymbolTable = this.m_currentSymbolTable.newLevel(taskEntry);
        this.m_symboltablePerContext.put(ctx, this.m_currentSymbolTable);

        visitChildren(ctx);

        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitProcedureDeclaration(SmallPearlParser.ProcedureDeclarationContext ctx) {
        String globalId = null;
        LinkedList<FormalParameter> formalParameters = null;
        ASTAttribute resultType = null;

        Log.debug("SymbolTableVisitor:visitProcedureDeclaration:ctx" + CommonUtils.printContext(ctx));
        TypeProcedureContext tpc = ctx.typeProcedure();
        for (ParseTree c : tpc.children) {
            if (c instanceof SmallPearlParser.ResultAttributeContext) {
                resultType = new ASTAttribute(getResultAttribute((SmallPearlParser.ResultAttributeContext) c));
            } else if (c instanceof SmallPearlParser.GlobalAttributeContext) {
                globalId = ctx.ID().getText();
            } else if (c instanceof SmallPearlParser.ListOfFormalParametersContext) {
                formalParameters = getListOfFormalParameters((SmallPearlParser.ListOfFormalParametersContext) c);
            }
        }
        
        if (ctx.globalAttribute() != null) {
          globalId = ctx.globalAttribute().ID().getText();
        }
        
        SymbolTableEntry entry = this.m_currentSymbolTable.lookup(ctx.ID().toString());
        if (entry != null) {
            // --------------
            // the context is the complete procedure - it would be good to have the ID as a separate
            // context to limit the message to the pure ID
            // for this - we must change the grammar towards
            //     procName ':' 'PROC' ....   instead of
            //     ID       ':' 'PROC'
            // ------
            //ErrorStack.enter(ctx,"definition");
            //ErrorStack.add("'"+ctx.ID().toString()+"' is multiple defined");
            //ErrorStack.warn("previous definition was at "+((ModuleEntry)(entry)).getSourceLineNo()+":"+
            //		((ModuleEntry)(entry)).getCharPositionInLine());
            //ErrorStack.leave();
            //return null;   // abort treatment
            throw new DoubleDeclarationException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        ProcedureEntry procedureEntry = new ProcedureEntry(ctx.ID().getText(), formalParameters, resultType, globalId, ctx, this.m_currentSymbolTable);
        this.m_currentSymbolTable = this.m_currentSymbolTable.newLevel(procedureEntry);

        /* Enter formal parameter into the local symbol table of this procedure */
        if (formalParameters != null && formalParameters.size() > 0) {
            for (FormalParameter formalParameter : formalParameters) {
                //VariableEntry param = new VariableEntry(formalParameter.name, formalParameter.type, formalParameter.assignmentProtection, formalParameter.m_ctx, null);
                //this.m_currentSymbolTable.enter(param);
                this.m_currentSymbolTable.enter(formalParameter);
            }
        }

        this.m_symboltablePerContext.put(ctx, this.m_currentSymbolTable);

        visitChildren(ctx);

        this.m_currentSymbolTable = this.m_currentSymbolTable.ascend();
        return null;
    }

    private TypeDefinition getResultAttribute(SmallPearlParser.ResultAttributeContext ctx) {
        Log.debug("SymbolTableVisitor:getResultAttribute:ctx" + CommonUtils.printContext(ctx));
        visitChildren(ctx.resultType());
        return m_type;
    }

    private LinkedList<FormalParameter> getListOfFormalParameters(SmallPearlParser.ListOfFormalParametersContext ctx) {
        LinkedList<FormalParameter> listOfFormalParameters = new LinkedList<FormalParameter>();

        Log.debug("SymbolTableVisitor:getListOfFormalParameters:ctx" + CommonUtils.printContext(ctx));

        if (ctx != null) {
            for (int i = 0; i < ctx.formalParameter().size(); i++) {
                getFormalParameter(listOfFormalParameters, ctx.formalParameter(i));
            }
        }

        return listOfFormalParameters;
    }

    private Void getFormalParameter(LinkedList<FormalParameter> listOfFormalParameters, SmallPearlParser.FormalParameterContext ctx) {
        Log.debug("SymbolTableVisitor:getFormalParameter:ctx" + CommonUtils.printContext(ctx));

        if (ctx != null) {
            for (int i = 0; i < ctx.ID().size(); i++) {
                int nbrDimensions = 0;  // default to scalar value
                String name = null;
                Boolean assignmentProtection = false;
                Boolean passIdentical = false;

                name = ctx.ID(i).getText();

                if (ctx.virtualDimensionList() != null) {

                    // get the number of array dimensions
                    // we count the ',' symbols and add 1,since 0 ',' is dimension 1
                    nbrDimensions = 1;
                    if (ctx.virtualDimensionList().commas() != null) {
                        nbrDimensions += ctx.virtualDimensionList().commas().getChildCount();
                    }
                }

                if (ctx.assignmentProtection() != null) {
                    assignmentProtection = true;
                }

                if (ctx.passIdentical() != null) {
                    passIdentical = true;
                }

                getParameterType(ctx.parameterType());

                if (nbrDimensions > 0) {
                    TypeArray array = new TypeArray();
                    array.setBaseType(m_type);
                    for (i = 0; i < nbrDimensions; i++) {
                        // the real dimensions limits are passed via array descriptor
                        array.addDimension(new ArrayDimension());

                    }
                    m_type = array;
                }

                listOfFormalParameters.add(new FormalParameter(name, m_type, assignmentProtection, passIdentical, ctx));
            }
        }

        return null;
    }

    private Void getParameterType(SmallPearlParser.ParameterTypeContext ctx) {
        Log.debug("SymbolTableVisitor:getParameterType:ctx" + CommonUtils.printContext(ctx));

	visitChildren(ctx);

       // if (ctx.simpleType() != null) {
       //     visitSimpleType(ctx.simpleType());
       // } else if (ctx.typeStructure() != null) {
       //     visitTypeStructure(ctx.typeStructure());
       // }

        return null;
    }

    @Override
    public Void visitBlock_statement(SmallPearlParser.Block_statementContext ctx) {
        String blockLabel;

        Log.debug("SymbolTableVisitor:visitBlock_statement:ctx" + CommonUtils.printContext(ctx));

        if (ctx.ID() != null) {
            blockLabel = ctx.ID().toString();
        } else {
            blockLabel = "?anonymous?";
        }

        BlockEntry blockEntry = new BlockEntry(blockLabel, ctx, m_currentSymbolTable);

        m_currentSymbolTable = m_currentSymbolTable.newLevel(blockEntry);
        this.m_symboltablePerContext.put(ctx, this.m_currentSymbolTable);

        visitChildren(ctx);

        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitScalarVariableDeclaration(SmallPearlParser.ScalarVariableDeclarationContext ctx) {
        Log.debug("SymbolTableVisitor:visitScalarVariableDeclaration:ctx" + CommonUtils.printContext(ctx));

        if (ctx != null) {
            for (int i = 0; i < ctx.variableDenotation().size(); i++) {
                visitVariableDenotation(ctx.variableDenotation().get(i));
            }
        }

        return null;
    }

    @Override
    public Void visitVariableDenotation(SmallPearlParser.VariableDenotationContext ctx) {
        boolean hasGlobalAttribute = false;
        boolean hasAllocationProtection = false;

        ArrayList<String> identifierDenotationList = null;
        ArrayList<Initializer> initElementList = null;

        Log.debug("SymbolTableVisitor:visitVariableDenotation:ctx" + CommonUtils.printContext(ctx));

        m_type = null;
        

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.IdentifierDenotationContext) {
                    identifierDenotationList = getIdentifierDenotation((SmallPearlParser.IdentifierDenotationContext) c);
                } else if (c instanceof SmallPearlParser.AllocationProtectionContext) {
                    hasAllocationProtection = true;
                } else if (c instanceof SmallPearlParser.TypeAttributeContext) {
                    visitTypeAttribute((SmallPearlParser.TypeAttributeContext) c);
                } else if (c instanceof SmallPearlParser.GlobalAttributeContext) {
                    hasGlobalAttribute = true;
                } else if (c instanceof SmallPearlParser.InitialisationAttributeContext) {
                    initElementList = getInitialisationAttribute((SmallPearlParser.InitialisationAttributeContext) c);
                }
            }

            // check allowed REF types; only types which may be used as arrays are allowed
            // REF () is not allowed on TASK, INTERRUPT, SIGNAL, PROCEDURE, FORMAT
            //         in OpenPEARL additionally not for DATION
            if (m_type instanceof TypeReference) {
              TypeDefinition base = ((TypeReference)m_type).getBaseType();
              if (base instanceof TypeArraySpecification) {
                TypeDefinition arraySpec = ((TypeArraySpecification)base).getBaseType();
                if (arraySpec instanceof TypeTask      ||
                    arraySpec instanceof TypeInterrupt ||
                    arraySpec instanceof TypeSignal    ||
                    arraySpec instanceof TypeProcedure) {
                  ErrorStack.add(ctx.typeAttribute(),null," REF () not allowed on "+arraySpec.toString());
                } else if (arraySpec instanceof TypeDation) {
                  ErrorStack.add(ctx.typeAttribute(),null," REF () DATION is not supported");
                }
              }
            }

            m_type.setHasAssignmentProtection(hasAllocationProtection);
            
            if (initElementList != null && identifierDenotationList.size() != initElementList.size()) {
                throw new NumberOfInitializerMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

            int j = 0;
            for (int i = 0; i < identifierDenotationList.size(); i++) {
                VariableEntry variableEntry = null;

                if (initElementList != null && initElementList.size() > 0) {
                    if (j < initElementList.size()) {
                        if ( initElementList.get(j) instanceof SimpleInitializer) {
                            SimpleInitializer initializer = (SimpleInitializer)initElementList.get(j);
                            fixPrecision(ctx, m_type, initializer);
                            variableEntry = new VariableEntry(identifierDenotationList.get(i), m_type, hasAllocationProtection, ctx, initElementList.get(j));
                            j++;
                        }
                    } else {
                        if ( initElementList.get(j) instanceof SimpleInitializer) {
                            SimpleInitializer initializer = (SimpleInitializer)initElementList.get(j - 1);
                            fixPrecision(ctx, m_type, initializer);
                            variableEntry = new VariableEntry(identifierDenotationList.get(i), m_type, hasAllocationProtection, ctx, initElementList.get(j - 1));
                        }
                    }
                } else {
                    variableEntry = new VariableEntry(identifierDenotationList.get(i), m_type, hasAllocationProtection, ctx, null);
                }

                if (!m_currentSymbolTable.enter(variableEntry)) {
                    SymbolTableEntry entry = m_currentSymbolTable.lookupLocal(identifierDenotationList.get(i));
                    if (entry != null) {
                        if (entry instanceof VariableEntry) {
                            if (((VariableEntry) entry).getLoopControlVariable()) {
                                throw new SemanticError(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine(), "Loop control variable cannot be declared");
                            }
                        }
                    }

                    throw new SemanticError(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine(), "Double definition of " + identifierDenotationList.get(i));
                }
            }
        }

        return null;
    }

    private void fixPrecision(ParserRuleContext ctx, TypeDefinition type, SimpleInitializer initializer) {
        Log.debug("SymbolTableVisitor:fixPrecision:ctx" + CommonUtils.printContext(ctx));

        if (type instanceof TypeFixed) {
            TypeFixed typ = (TypeFixed) type;

            if (initializer.getConstant() instanceof ConstantFixedValue) {
                ConstantFixedValue value = (ConstantFixedValue) initializer.getConstant();
                value.setPrecision(typ.getPrecision());
            } else {
                throw new TypeMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        } else if (type instanceof TypeFloat) {
            TypeFloat typ = (TypeFloat) type;

            if (initializer.getConstant() instanceof ConstantFloatValue) {
                ConstantFloatValue value = (ConstantFloatValue) initializer.getConstant();
                value.setPrecision(typ.getPrecision());
            } else if (initializer.getConstant() instanceof ConstantFixedValue) {
                ConstantFixedValue fixedValue = (ConstantFixedValue) initializer.getConstant();
                ConstantFloatValue floatValue = new ConstantFloatValue((double) fixedValue.getValue(), typ.getPrecision());
                m_constantPool.add(floatValue);
                initializer.setConstant(floatValue);
            } else {
                throw new TypeMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }
    }

    @Override
    public Void visitTypeAttribute(SmallPearlParser.TypeAttributeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeAttribute:ctx" + CommonUtils.printContext(ctx));

        if (ctx.simpleType() != null) {
            visitSimpleType(ctx.simpleType());
        } else if (ctx.typeReference() != null) {
            visitTypeReference(ctx.typeReference());
        }
        return null;
    }

    @Override
    public Void visitSimpleType(SmallPearlParser.SimpleTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitSimpleType:ctx" + CommonUtils.printContext(ctx));

        if (ctx != null) {
            if (ctx.typeInteger() != null) {
                visitTypeInteger(ctx.typeInteger());
            } else if (ctx.typeDuration() != null) {
                visitTypeDuration(ctx.typeDuration());
            } else if (ctx.typeBitString() != null) {
                visitTypeBitString(ctx.typeBitString());
            } else if (ctx.typeFloatingPointNumber() != null) {
                visitTypeFloatingPointNumber(ctx.typeFloatingPointNumber());
            } else if (ctx.typeTime() != null) {
                visitTypeTime(ctx.typeTime());
            } else if (ctx.typeCharacterString() != null) {
                visitTypeCharacterString(ctx.typeCharacterString());
            }
        }

        return null;
    }

    @Override
    public Void visitTypeReference(SmallPearlParser.TypeReferenceContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeReference:ctx" + CommonUtils.printContext(ctx));
        boolean hasVistualDimensionList = false;
        boolean hasAssignmentProtection = false;
        int dimensions = 0;
        if (ctx.virtualDimensionList()!= null) {
          hasVistualDimensionList = true;
          if (ctx.virtualDimensionList().commas() != null) {
             dimensions = ctx.virtualDimensionList().commas().getChildCount();
          } else {
            dimensions = 1;
          }
        }
        if (ctx.assignmentProtection() != null) {
          hasAssignmentProtection = true;
        }
        visitChildren(ctx);
        m_type.setHasAssignmentProtection(hasAssignmentProtection);
        if (hasVistualDimensionList) {
          m_type = new TypeArraySpecification(m_type,dimensions);
        }
        m_type = new TypeReference(m_type);
         
        return null;
    }

//    @Override
//    public Void visitTypeReferenceSimpleType(SmallPearlParser.TypeReferenceSimpleTypeContext ctx) {
//        Log.debug("SymbolTableVisitor:visitTypeReferenceSimpleType:ctx" + CommonUtils.printContext(ctx));
//
//        visitSimpleType(ctx.simpleType());
//        return null;
//    }
    
    @Override
    public Void visitTypeDation(SmallPearlParser.TypeDationContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeDation:ctx" + CommonUtils.printContext(ctx));

        TypeDation d = new TypeDation();
//        private void treatTypeDation(TypeDationContext ctx, TypeDation d) {
          if (ctx != null) {
              // let's have a look on the type of the dation
              if (ctx.sourceSinkAttribute() != null) {
                  if (ctx.sourceSinkAttribute() instanceof SourceSinkAttributeINContext) {
                      d.setIn(true);
                  } else if (ctx.sourceSinkAttribute() instanceof SourceSinkAttributeOUTContext) {
                      d.setOut(true);
                  } else if (ctx.sourceSinkAttribute() instanceof SourceSinkAttributeINOUTContext) {
                      d.setIn(true);
                      d.setOut(true);
                  } else {
                      throw new InternalCompilerErrorException("SymbolTableVisitor-untreated SourceSinkAttribute");
                  }
              }
              if (ctx.classAttribute() != null) {
                  if (ctx.classAttribute().systemDation() != null) {
                      d.setSystemDation(true);
                  }
                  if (ctx.classAttribute().alphicDation() != null) {
                      d.setAlphic(true);
                  }
                  if (ctx.classAttribute().basicDation() != null) {
                      d.setBasic(true);
                  }
                  if (ctx.classAttribute().typeOfTransmissionData() != null) {
                      TypeOfTransmissionDataContext c = ctx.classAttribute().typeOfTransmissionData();
                      if (c instanceof TypeOfTransmissionDataALLContext) {
                        // nothing to do here!
                      } else {
                        // ether simpleType or typeStructure possible
                        visitChildren(c);
                        d.setTypeOfTransmission(m_type);
                      }
                      // maybe we need some treatment for STRUCT
                      d.setTypeOfTransmission(c.getText());
                  }
              }
          }

          // in progress treat typologgy
          if (ctx.typology() != null) {
              TypologyContext c = ctx.typology();
              if (c.dimension1() != null) {
                  d.setDimension1(treatDimension(c.dimension1().getText()));
              }
              if (c.dimension2() != null) {
                  d.setDimension2(treatDimension(c.dimension2().getText()));
              }
              if (c.dimension3() != null) {
                  d.setDimension3(treatDimension(c.dimension3().getText()));
              }

              if (c.tfu() != null) {
                  d.setTfu(true);
                  if (c.tfu().tfuMax() != null) {
                      ErrorStack.enter(c.tfu(), "TFU MAX");
                      ErrorStack.warn("is deprecated");
                      ErrorStack.leave();
                  }
              }

          } else {
              // we have a type basic without typology --> DationTS; ALPHIC should not be set
              // lets check this in another stage
          }

          if (ctx.accessAttribute() != null) {
              ErrorStack.enter(ctx.accessAttribute(), "accessAttributes");
              for (ParseTree c1 : ctx.accessAttribute().children) {
                  if (c1.getText().equals("DIRECT")) d.setDirect(true);
                  else if (c1.getText().equals("FORWARD")) d.setDirect(false);
                  else if (c1.getText().equals("FORBACK")) {
                      ErrorStack.add("FORBACK is not supported");
                  } else if (c1.getText().equals("CYCLIC")) d.setCyclic(true);
                  else if (c1.getText().equals("NOCYCL")) d.setCyclic(false);
                  else if (c1.getText().equals("STREAM")) d.setStream(true);
                  else if (c1.getText().equals("NOSTREAM")) d.setStream(false);
                  else {
                      throw new InternalCompilerErrorException("DationDCL untreated accessAttribute" + c1.getText());
                  }
              }
              ErrorStack.leave();

          }
          m_type = d;
        Log.warn("SybTyVis: treatment of TypeDation must be more elaboarate");
        return null;
    }

    
    @Override
    public Void visitTypeReferenceTaskType(SmallPearlParser.TypeReferenceTaskTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeReferenceTaskType:ctx" + CommonUtils.printContext(ctx));

        m_type = new TypeTask();
        return null;
    }

    @Override
    public Void visitTypeReferenceSemaType(SmallPearlParser.TypeReferenceSemaTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeReferenceSemaType:ctx" + CommonUtils.printContext(ctx));

        m_type = new TypeSemaphore();
        return null;
    }

    @Override
    public Void visitTypeReferenceBoltType(SmallPearlParser.TypeReferenceBoltTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeReferenceBoltType:ctx" + CommonUtils.printContext(ctx));

        m_type = new TypeBolt();
        return null;
    }

    @Override
    public Void visitTypeProcedure(SmallPearlParser.TypeProcedureContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeProcedure:ctx" + CommonUtils.printContext(ctx));
        LinkedList<FormalParameter> formalParameters = null;
        TypeDefinition resultType=null;
        
        ASTAttribute resultAttr = null;
        for (ParseTree c : ctx.children) {
            if (c instanceof SmallPearlParser.ResultAttributeContext) {
              resultType = getResultAttribute((SmallPearlParser.ResultAttributeContext) c);
              resultAttr = new ASTAttribute(resultType);
            } else if (c instanceof SmallPearlParser.ListOfFormalParametersContext) {
                formalParameters = getListOfFormalParameters((SmallPearlParser.ListOfFormalParametersContext) c);
            }
        }
        m_type = new TypeProcedure(formalParameters, resultType);
        
        return null;
    }
    @Override
    public Void visitTypeReferenceProcedureType(SmallPearlParser.TypeReferenceProcedureTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeReferenceProcedureType:ctx" + CommonUtils.printContext(ctx));

        m_type = new TypeProcedure();
        return null;
    }

    @Override
    public Void visitTypeReferenceInterruptType(SmallPearlParser.TypeReferenceInterruptTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeReferenceInterruptType:ctx" + CommonUtils.printContext(ctx));

        m_type = new TypeInterrupt();
        return null;
    }

    @Override
    public Void visitTypeReferenceSignalType(SmallPearlParser.TypeReferenceSignalTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeReferenceSignalType:ctx" + CommonUtils.printContext(ctx));

        m_type = new TypeSignal();
        return null;
    }

    @Override
    public Void visitTypeInteger(SmallPearlParser.TypeIntegerContext ctx) {
        Integer size = m_currentSymbolTable.lookupDefaultFixedLength();


        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.MprecisionContext) {
                    size = Integer.parseInt(((SmallPearlParser.MprecisionContext) c).integerWithoutPrecision().IntegerConstant().getText());
                }
            }
        }

        m_type = new TypeFixed(size);
        return null;
    }

    @Override
    public Void visitTypeBitString(SmallPearlParser.TypeBitStringContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeBitString:ctx" + CommonUtils.printContext(ctx));
        int length = m_currentSymbolTable.lookupDefaultBitLength();
        

        if (ctx.IntegerConstant() != null) {
            length = Integer.parseInt(ctx.IntegerConstant().getText());
            if (length < 1 || length > 64) {
                throw new NotSupportedTypeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        m_type = new TypeBit(length);

        return null;
    }

    @Override
    public Void visitTypeCharacterString(SmallPearlParser.TypeCharacterStringContext ctx) {
        int length = m_currentSymbolTable.lookupDefaultCharLength();


        if (ctx.IntegerConstant() != null) {
            length = Integer.parseInt(ctx.IntegerConstant().getText());

            if (length < 1 || length > Defaults.CHARACTER_MAX_LENGTH) {
                throw new NotSupportedTypeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        m_type = new TypeChar(length);

        return null;
    }

    @Override
    public Void visitTypeFloatingPointNumber(SmallPearlParser.TypeFloatingPointNumberContext ctx) {
        int precision = m_currentSymbolTable.lookupDefaultFloatLength();


        if (ctx.IntegerConstant() != null) {
            precision = Integer.parseInt(ctx.IntegerConstant().getText());

            if (precision != Defaults.FLOAT_SHORT_PRECISION && precision != Defaults.FLOAT_LONG_PRECISION) {
                throw new NotSupportedTypeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        m_type = new TypeFloat(precision);

        return null;
    }

    @Override
    public Void visitTypeDuration(SmallPearlParser.TypeDurationContext ctx) {
        m_type = new TypeDuration();
        return null;
    }

    private ArrayList<String> getIdentifierDenotation(SmallPearlParser.IdentifierDenotationContext ctx) {
        ArrayList<String> identifierDenotationList = new ArrayList<String>();

        if (ctx != null) {
            for (int i = 0; i < ctx.ID().size(); i++) {
                identifierDenotationList.add(ctx.ID().get(i).toString());
            }
        }

        return identifierDenotationList;
    }

    @Override
    public Void visitTypeTime(SmallPearlParser.TypeTimeContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeTime:ctx" + CommonUtils.printContext(ctx));

        if (ctx.type_clock() != null) {
            m_type = new TypeClock();
        } else if (ctx.type_duration() != null) {
            m_type = new TypeDuration();
        }

        return null;
    }

    @Override
    public Void visitType_clock(SmallPearlParser.Type_clockContext ctx) {
        Log.debug("SymbolTableVisitor:visitType_clock:ctx" + CommonUtils.printContext(ctx));

        m_type = new TypeClock();
        return null;
    }

    @Override
    public Void visitArrayVariableDeclaration(SmallPearlParser.ArrayVariableDeclarationContext ctx) {
        if (m_verbose > 0) {
            System.out.println("SymbolTableVisitor: visitArrayVariableDeclaration");
        }

        visitChildren(ctx);

        return null;
    }

    @Override
    public Void visitArrayDenotation(SmallPearlParser.ArrayDenotationContext ctx) {
        Log.debug("SymbolTableVisitor:visitArrayDenotation:ctx" + CommonUtils.printContext(ctx));

        boolean hasGlobalAttribute = false;
        boolean hasAssigmentProtection = false;
        ArrayList<String> identifierDenotationList = new ArrayList<String>();
        ArrayOrStructureInitializer arrayOrStructureInitializer = null;

        m_type = new TypeArray();

        if (ctx != null) {
            for (int i = 0; i < ctx.ID().size(); i++) {
                identifierDenotationList.add(ctx.ID().get(i).toString());
            }
        }

        if (ctx != null) {
            for (ParseTree c : ctx.children) {
                if (c instanceof SmallPearlParser.DimensionAttributeContext) {
                    visitDimensionAttribute((SmallPearlParser.DimensionAttributeContext) c);
                } else if (c instanceof SmallPearlParser.AssignmentProtectionContext) {
                    hasAssigmentProtection = true;
                } else if (c instanceof SmallPearlParser.TypeAttributeForArrayContext) {
                    visitTypeAttributeForArray((SmallPearlParser.TypeAttributeForArrayContext) c);
                } else if (c instanceof SmallPearlParser.GlobalAttributeContext) {
                    hasGlobalAttribute = true;
                }
            }

            if ( ctx.initialisationAttribute() != null ) {
                ArrayList<Initializer> initElementList = null;

                initElementList = getInitialisationAttribute(ctx.initialisationAttribute());

                if ( initElementList != null && initElementList.size() > 0) {
                    arrayOrStructureInitializer = new ArrayOrStructureInitializer(ctx, initElementList);
                }
                else {
                    arrayOrStructureInitializer = null;
                }
            }

            addArrayDescriptor(new ArrayDescriptor(((TypeArray) m_type).getNoOfDimensions(), ((TypeArray) m_type).getDimensions()));

            for (int i = 0; i < identifierDenotationList.size(); i++) {
                VariableEntry variableEntry = new VariableEntry(identifierDenotationList.get(i), m_type, hasAssigmentProtection, ctx, arrayOrStructureInitializer);
                if (!m_currentSymbolTable.enter(variableEntry)) {
                    System.out.println("ERR: Double definition of " + identifierDenotationList.get(i));
                }
            }
        }

        return null;
    }

    @Override
    public Void visitDimensionAttribute(SmallPearlParser.DimensionAttributeContext ctx) {
        Log.debug("SymbolTableVisitor:visitDimensionAttribute:ctx" + CommonUtils.printContext(ctx));
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitBoundaryDenotation(SmallPearlParser.BoundaryDenotationContext ctx) {
        if (ctx.constantFixedExpression().size() == 1) {
            int upb = CommonUtils.getConstantFixedExpression(ctx.constantFixedExpression(0), m_currentSymbolTable);
            ((TypeArray) m_type).addDimension(new ArrayDimension(
                    Defaults.DEFAULT_ARRAY_LWB, upb, ctx));
            //Integer.parseInt(ctx.constantFixedExpression(0).getText())));
        } else {
            int lwb = CommonUtils.getConstantFixedExpression(ctx.constantFixedExpression(0), m_currentSymbolTable);
            int upb = CommonUtils.getConstantFixedExpression(ctx.constantFixedExpression(1), m_currentSymbolTable);
            ((TypeArray) m_type).addDimension(new ArrayDimension(lwb, upb, ctx));

        }

        return null;
    }

    @Override
    public Void visitTypeAttributeForArray(SmallPearlParser.TypeAttributeForArrayContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeAttributeForArray:ctx" + CommonUtils.printContext(ctx));

        if (ctx.type_bit() != null) {
            TypeDefinition tempType = m_type;
            visitType_bit(ctx.type_bit());
            ((TypeArray) tempType).setBaseType(m_type);
            m_type = tempType;
        } else if (ctx.type_char() != null) {
            TypeDefinition tempType = m_type;
            visitType_char(ctx.type_char());
            ((TypeArray) tempType).setBaseType(m_type);
            m_type = tempType;
        } else if (ctx.type_clock() != null) {
            ((TypeArray) m_type).setBaseType(new TypeClock());
        } else if (ctx.type_duration() != null) {
            ((TypeArray) m_type).setBaseType(new TypeDuration());
        } else if (ctx.type_fixed() != null) {
            TypeDefinition tempType = m_type;
            visitType_fixed(ctx.type_fixed());
            ((TypeArray) tempType).setBaseType(m_type);
            m_type = tempType;
        } else if (ctx.type_float() != null) {
            TypeDefinition tempType = m_type;
            visitType_float(ctx.type_float());
            ((TypeArray) tempType).setBaseType(m_type);
            m_type = tempType;
        } else if (ctx.typeReference() != null) {
            TypeDefinition tempType = m_type;
            visitTypeReference(ctx.typeReference());
            ((TypeArray) tempType).setBaseType(m_type);
            m_type = tempType;
        }

        return null;
    }

    @Override
    public Void visitType_bit(SmallPearlParser.Type_bitContext ctx) {
        Log.debug("SymbolTableVisitor:visitType_bit:ctx" + CommonUtils.printContext(ctx));

        Integer width = Defaults.BIT_LENGTH;

        if (ctx.IntegerConstant() != null) {
            width = Integer.parseInt(ctx.IntegerConstant().getText());
        }

        m_type = new TypeBit(width);
        return null;
    }

    @Override
    public Void visitType_fixed(SmallPearlParser.Type_fixedContext ctx) {
        Log.debug("SymbolTableVisitor:visitType_fixed:ctx" + CommonUtils.printContext(ctx));

        Integer width = Defaults.FIXED_LENGTH;

        if (ctx.IntegerConstant() != null) {
            width = Integer.parseInt(ctx.IntegerConstant().getText());
            if (width < 1 || width > 63) {
                throw new NotSupportedTypeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        m_type = new TypeFixed(width);
        return null;
    }

    @Override
    public Void visitType_char(SmallPearlParser.Type_charContext ctx) {
        Log.debug("SymbolTableVisitor:visitType_char:ctx" + CommonUtils.printContext(ctx));

        Integer width = Defaults.CHARACTER_LENGTH;

        if (ctx.IntegerConstant() != null) {
            width = Integer.parseInt(ctx.IntegerConstant().getText());
            if (width < 1 || width > 255) {
                throw new NotSupportedTypeException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        m_type = new TypeChar(width);
        return null;
    }

    @Override
    public Void visitType_float(SmallPearlParser.Type_floatContext ctx) {
        Log.debug("SymbolTableVisitor:visitType_float:ctx" + CommonUtils.printContext(ctx));

        Integer precision = Defaults.FLOAT_PRECISION;

        if (ctx.IntegerConstant() != null) {
            precision = Integer.parseInt(ctx.IntegerConstant().getText());
        }

        m_type = new TypeFloat(precision);
        return null;
    }

    @Override
    public Void visitSemaDeclaration(SmallPearlParser.SemaDeclarationContext ctx) {
        Log.debug("SymbolTableVisitor:visitSemaDeclaration:ctx" + CommonUtils.printContext(ctx));

        boolean hasGlobalAttribute = false;

        ArrayList<String> identifierDenotationList = null;
        ArrayList<Integer> presetList = null;

        if (ctx != null) {
            if (ctx.globalAttribute() != null) {
                hasGlobalAttribute = true;
            }

            if (ctx.identifierDenotation() != null) {
                identifierDenotationList = getIdentifierDenotation(ctx.identifierDenotation());
            }
        }

        for (int i = 0; i < identifierDenotationList.size(); i++) {
            SemaphoreEntry entry = new SemaphoreEntry(identifierDenotationList.get(i), ctx);
            if (!m_currentSymbolTable.enter(entry)) {
                System.out.println("ERR: Double definition of " + identifierDenotationList.get(i));
            }
        }

        return null;
    }

    @Override
    public Void visitStatement(SmallPearlParser.StatementContext ctx) {
        Log.debug("SymbolTableVisitor:visitStatement:ctx" + CommonUtils.printContext(ctx));

        if (ctx != null) {
            if (ctx.label_statement() != null) {
                for (int i = 0; i < ctx.label_statement().size(); i++) {
                    LabelEntry entry = new LabelEntry(ctx.label_statement(i).ID().getText(), ctx.label_statement(i));

                    if (!m_currentSymbolTable.enter(entry)) {
                        System.out.println("ERR: Double definition of " + ctx.label_statement(i).ID().getText());
                    }
                }
            }

            visitChildren(ctx);
        }

        return null;
    }

//    @Override
//    public Void visitSemaTry(SmallPearlParser.SemaTryContext ctx) {
//        Log.debug("SymbolTableVisitor:visitSemaTry:ctx" + CommonUtils.printContext(ctx));
//
//        LinkedList<SemaphoreEntry> listOfSemaphores = new LinkedList<SemaphoreEntry>();
//
//        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();
//
//        if (listOfModules.size() > 1) {
//            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        ModuleEntry moduleEntry = listOfModules.get(0);
//        SymbolTable symbolTable = moduleEntry.scope;
//
//        for (int i = 0; i < ctx.name().size(); i++) {
//            SymbolTableEntry entry = symbolTable.lookup(ctx.name(i).getText());
//
//            if (entry != null && entry instanceof SemaphoreEntry) {
//                listOfSemaphores.add((SemaphoreEntry) entry);
//            } else {
//                throw new ArgumentMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }
//        }
//
//        Collections.sort(listOfSemaphores);
//        addToListOfTemporarySemaphoreArrays(listOfSemaphores);
//
//        return null;
//    }
//
//    @Override
//    public Void visitSemaRequest(SmallPearlParser.SemaRequestContext ctx) {
//        Log.debug("SymbolTableVisitor:visitSemaRequest:ctx" + CommonUtils.printContext(ctx));
//
//        LinkedList<SemaphoreEntry> listOfSemaphores = new LinkedList<SemaphoreEntry>();
//
//        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();
//
//        if (listOfModules.size() > 1) {
//            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        ModuleEntry moduleEntry = listOfModules.get(0);
//        SymbolTable symbolTable = moduleEntry.scope;
//
//        for (int i = 0; i < ctx.name().size(); i++) {
//            SymbolTableEntry entry = symbolTable.lookup(ctx.name(i).getText());
//
//            if (entry != null && entry instanceof SemaphoreEntry) {
//                listOfSemaphores.add((SemaphoreEntry) entry);
//            } else {
//                throw new ArgumentMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }
//        }
//
//        Collections.sort(listOfSemaphores);
//        addToListOfTemporarySemaphoreArrays(listOfSemaphores);
//
//        return null;
//    }
//
//    @Override
//    public Void visitSemaRelease(SmallPearlParser.SemaReleaseContext ctx) {
//        Log.debug("SymbolTableVisitor:visitSemaRelease:ctx" + CommonUtils.printContext(ctx));
//
//        LinkedList<SemaphoreEntry> listOfSemaphores = new LinkedList<SemaphoreEntry>();
//
//        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();
//
//        if (listOfModules.size() > 1) {
//            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        ModuleEntry moduleEntry = listOfModules.get(0);
//        SymbolTable symbolTable = moduleEntry.scope;
//
//        for (int i = 0; i < ctx.name().size(); i++) {
//          String s = ctx.name(i).getText();
//            SymbolTableEntry entry = symbolTable.lookup(ctx.name(i).getText());
//
//            if (entry != null && entry instanceof SemaphoreEntry) {
//                listOfSemaphores.add((SemaphoreEntry) entry);
//            } else {
//                throw new ArgumentMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }
//        }
//
//        Collections.sort(listOfSemaphores);
//        addToListOfTemporarySemaphoreArrays(listOfSemaphores);
//
//        return null;
//    }
//
//    private Void addToListOfTemporarySemaphoreArrays(LinkedList<SemaphoreEntry> listOfSemaphores) {
//        Boolean found = false;
//        for (int i = 0; i < m_listOfTemporarySemaphoreArrays.size(); i++) {
//            LinkedList<SemaphoreEntry> semaphores = m_listOfTemporarySemaphoreArrays.get(i);
//            if (semaphores.size() == listOfSemaphores.size()) {
//                int j = 0;
//                for (j = 0; j < semaphores.size(); j++) {
//                    if (semaphores.get(j).compareTo(listOfSemaphores.get(j)) != 0) {
//                        break;
//                    }
//                }
//
//                if (j == semaphores.size()) {
//                    found = true;
//                    break;
//                }
//            }
//        }
//
//        if (!found) {
//            this.m_listOfTemporarySemaphoreArrays.add(listOfSemaphores);
//        }
//
//        return null;
//    }
//
//    public LinkedList<LinkedList<SemaphoreEntry>> getListOfTemporarySemaphoreArrays() {
//        return m_listOfTemporarySemaphoreArrays;
//    }
//
//    private Void addToListOfTemporaryBoltArrays(LinkedList<BoltEntry> listOfBolts) {
//        Boolean found = false;
//        for (int i = 0; i < m_listOfTemporaryBoltArrays.size(); i++) {
//            LinkedList<BoltEntry> bolts = m_listOfTemporaryBoltArrays.get(i);
//            if (bolts.size() == listOfBolts.size()) {
//                for (int j = 0; j < bolts.size(); j++) {
//                    if (bolts.get(j).compareTo(listOfBolts.get(j)) == 0) {
//                        found = true;
//                        break;
//                    }
//                }
//            }
//        }
//
//        if (!found) {
//            this.m_listOfTemporaryBoltArrays.add(listOfBolts);
//        }
//
//        return null;
//    }
//
//    public LinkedList<LinkedList<BoltEntry>> getListOfTemporaryBoltArrays() {
//        return m_listOfTemporaryBoltArrays;
//    }
//
    public LinkedList<ArrayDescriptor> getListOfArrayDescriptors() {
        Log.debug("SymbolTableVisitor:getListOfArrayDescriptors");

        return m_listOfArrayDescriptors;
    }

    @Override
    public Void visitLoopStatement(SmallPearlParser.LoopStatementContext ctx) {
        Log.debug("SymbolTableVisitor:visitLoopStatement:ctx" + CommonUtils.printContext(ctx));

        String blockLabel = null;

        if (m_verbose > 0) {
            System.out.println("SymbolTableVisitor: visitLoopStatement");
        }

        LoopEntry loopEntry = new LoopEntry(blockLabel, ctx, m_currentSymbolTable);

        m_currentSymbolTable = m_currentSymbolTable.newLevel(loopEntry);
        this.m_symboltablePerContext.put(ctx, this.m_currentSymbolTable);

        if (ctx.loopStatement_for() != null) {
          // we define the symbol with the default length
          // the ExpressionTypeVisitor checks the LoopStatement with types and precisions
          // and updates the precision of the loop control variable to fit with start-value,
          // increment nad end-value as far as they are given
            VariableEntry controlVariable = new VariableEntry(ctx.loopStatement_for().ID().getText(), new TypeFixed(Defaults.FIXED_LENGTH), true, null, null);
            controlVariable.setLoopControlVariable();
            m_currentSymbolTable.enter(controlVariable);
        }

        for (int i = 0; i < ctx.loopBody().scalarVariableDeclaration().size(); i++) {
            visitScalarVariableDeclaration(ctx.loopBody().scalarVariableDeclaration(i));
        }

        for (int i = 0; i < ctx.loopBody().arrayVariableDeclaration().size(); i++) {
            visitArrayVariableDeclaration(ctx.loopBody().arrayVariableDeclaration(i));
        }

        for (int i = 0; i < ctx.loopBody().structVariableDeclaration().size(); i++) {
            visitStructVariableDeclaration(ctx.loopBody().structVariableDeclaration(i));
        }

        for (int i = 0; i < ctx.loopBody().statement().size(); i++) {
            SmallPearlParser.StatementContext stmt = ctx.loopBody().statement(i);

            if (stmt.block_statement() != null) {
                visitBlock_statement(stmt.block_statement());
            } else if (stmt.unlabeled_statement() != null) {
                visitUnlabeled_statement(stmt.unlabeled_statement());
            }
        }

        if (ctx.loopStatement_end().ID() != null) {
            blockLabel = ctx.loopStatement_end().ID().getText();
        }

        m_currentSymbolTable = m_currentSymbolTable.ascend();
        return null;
    }

    @Override
    public Void visitBoltDeclaration(SmallPearlParser.BoltDeclarationContext ctx) {
        Log.debug("SymbolTableVisitor:visitBoltDeclaration:ctx" + CommonUtils.printContext(ctx));

        boolean hasGlobalAttribute = false;

        if (m_verbose > 0) {
            System.out.println("SymbolTableVisitor: visitBoltDeclaration");
        }

        ArrayList<String> identifierDenotationList = null;

        if (ctx != null) {
            if (ctx.globalAttribute() != null) {
                hasGlobalAttribute = true;
            }

            if (ctx.identifierDenotation() != null) {
                identifierDenotationList = getIdentifierDenotation(ctx.identifierDenotation());
            }
        }

        for (int i = 0; i < identifierDenotationList.size(); i++) {
            BoltEntry entry = new BoltEntry(identifierDenotationList.get(i), ctx);
            if (!m_currentSymbolTable.enter(entry)) {
                System.out.println("ERR: Double definition of " + identifierDenotationList.get(i));
            }
        }

        return null;
    }


//    @Override
//    public Void visitBoltReserve(SmallPearlParser.BoltReserveContext ctx) {
//        Log.debug("SymbolTableVisitor:visitBoltReserve:ctx" + CommonUtils.printContext(ctx));
//
//        LinkedList<BoltEntry> listOfBolts = new LinkedList<BoltEntry>();
//
//        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();
//
//        if (listOfModules.size() > 1) {
//            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        ModuleEntry moduleEntry = listOfModules.get(0);
//        SymbolTable symbolTable = moduleEntry.scope;
//
//        for (int i = 0; i < ctx.name().size(); i++) {
//            SymbolTableEntry entry = symbolTable.lookup(ctx.name(i).getText());
//
//            if (entry != null && entry instanceof BoltEntry) {
//                listOfBolts.add((BoltEntry) entry);
//            } else {
//                throw new ArgumentMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }
//        }
//
//        Collections.sort(listOfBolts);
//        addToListOfTemporaryBoltArrays(listOfBolts);
//
//        return null;
//    }
//
//    @Override
//    public Void visitBoltFree(SmallPearlParser.BoltFreeContext ctx) {
//        Log.debug("SymbolTableVisitor:visitBoltFree:ctx" + CommonUtils.printContext(ctx));
//
//        LinkedList<BoltEntry> listOfBolts = new LinkedList<BoltEntry>();
//
//        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();
//
//        if (listOfModules.size() > 1) {
//            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        ModuleEntry moduleEntry = listOfModules.get(0);
//        SymbolTable symbolTable = moduleEntry.scope;
//
//        for (int i = 0; i < ctx.name().size(); i++) {
//            SymbolTableEntry entry = symbolTable.lookup(ctx.name(i).getText());
//
//            if (entry != null && entry instanceof BoltEntry) {
//                listOfBolts.add((BoltEntry) entry);
//            } else {
//                throw new ArgumentMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }
//        }
//
//        Collections.sort(listOfBolts);
//        addToListOfTemporaryBoltArrays(listOfBolts);
//
//        return null;
//    }
//
//    @Override
//    public Void visitBoltEnter(SmallPearlParser.BoltEnterContext ctx) {
//        Log.debug("SymbolTableVisitor:visitBoltEnter:ctx" + CommonUtils.printContext(ctx));
//
//        LinkedList<BoltEntry> listOfBolts = new LinkedList<BoltEntry>();
//
//        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();
//
//        if (listOfModules.size() > 1) {
//            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        ModuleEntry moduleEntry = listOfModules.get(0);
//        SymbolTable symbolTable = moduleEntry.scope;
//
//        for (int i = 0; i < ctx.name().size(); i++) {
//            SymbolTableEntry entry = symbolTable.lookup(ctx.name(i).getText());
//
//            if (entry != null && entry instanceof BoltEntry) {
//                listOfBolts.add((BoltEntry) entry);
//            } else {
//                throw new ArgumentMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }
//        }
//
//        Collections.sort(listOfBolts);
//        addToListOfTemporaryBoltArrays(listOfBolts);
//
//        return null;
//    }
//
//    @Override
//    public Void visitBoltLeave(SmallPearlParser.BoltLeaveContext ctx) {
//        Log.debug("SymbolTableVisitor:visitBoltLeave:ctx" + CommonUtils.printContext(ctx));
//
//        LinkedList<BoltEntry> listOfBolts = new LinkedList<BoltEntry>();
//
//        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();
//
//        if (listOfModules.size() > 1) {
//            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
//
//        ModuleEntry moduleEntry = listOfModules.get(0);
//        SymbolTable symbolTable = moduleEntry.scope;
//
//        for (int i = 0; i < ctx.name().size(); i++) {
//            SymbolTableEntry entry = symbolTable.lookup(ctx.name(i).getText());
//
//            if (entry != null && entry instanceof BoltEntry) {
//                listOfBolts.add((BoltEntry) entry);
//            } else {
//                throw new ArgumentMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }
//        }
//
//        Collections.sort(listOfBolts);
//        addToListOfTemporaryBoltArrays(listOfBolts);
//
//        return null;
//    }

    public SymbolTable getSymbolTablePerContext(ParseTree ctx) {
        return m_symboltablePerContext.get(ctx);
    }

    @Override
    public Void visitDationDeclaration(SmallPearlParser.DationDeclarationContext ctx) {
        // the TypeDation may have lot of parameters depending on user/system dation
        // so we just create an object and set the attributes while scanning the context

        if (m_verbose > 0) {
            System.out.println("SymbolTableVisitor: visitDationDeclaration");
        }

        ErrorStack.enter(ctx, "DationDCL");

        visitTypeDation(ctx.typeDation());
        
        TypeDation d = (TypeDation)m_type;
        d.setIsDeclaration(true);
        treatIdentifierDenotation(ctx.identifierDenotation(), d);
        

        if (ctx.globalAttribute() != null) {
            treatGlobalAttribute(ctx.globalAttribute(), d);
        }

        // get CREATED parameter
        d.setCreatedOn(ctx.ID().getText());

        //
        ErrorStack.leave();
        return null;
    }

    /* -------------------------------------------------------------------  */
    /* START of common for dationDeclaration and dationSpecification 	    */
    /* -------------------------------------------------------------------  */
    private void treatIdentifierDenotation(IdentifierDenotationContext ctx, TypeDation d) {
        for (int i = 0; i < ctx.ID().size(); i++) {
            String dationName = ctx.ID(i).toString();
            //System.out.println("DationName: "+ dationName);

            SymbolTableEntry entry1 = this.m_currentSymbolTable.lookup(dationName);

            if (entry1 != null) {
                ErrorStack.add("symbol " + dationName + " already defined");
                //throw new DoubleDeclarationException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

            VariableEntry ve = new VariableEntry(dationName, d, ctx);
            if (!m_currentSymbolTable.enter(ve)) {
                throw new InternalCompilerErrorException("SymbolTableVisitor- could not add symbol");
            }
        }
    }

  
    int treatDimension(String s) {
        if (s.equals("*")) {
            return 0;  // '*'
        } else {
            int nbr = Integer.parseInt(s);
            if (nbr <= 0) {
                ErrorStack.add("dimension range must be > 0");
            }
            return (nbr);
        }
    }

    void treatGlobalAttribute(GlobalAttributeContext ctx, TypeDation d) {
        if (ctx.ID() != null) {
            d.setGlobal(ctx.ID().getText());
        }
    }
    /* -------------------------------------------------------------------  */
    /* END of common for dationDeclaration and dationSpecification 			*/
    /* -------------------------------------------------------------------  */

    @Override
    public Void visitLengthDefinition(SmallPearlParser.LengthDefinitionContext ctx) {
        if (m_verbose > 0) {
            System.out.println("SymbolTableVisitor: visitLengthDefinition");
        }

        if (ctx.lengthDefinitionType() instanceof SmallPearlParser.LengthDefinitionFixedTypeContext) {
            TypeFixed typ = new TypeFixed(Integer.valueOf((ctx.IntegerConstant().toString())));

            if (typ.getPrecision() < Defaults.FIXED_MIN_LENGTH || typ.getPrecision() > Defaults.FIXED_MAX_LENGTH) {
                throw new PrecisionNotSupportedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

            LengthEntry entry = new LengthEntry(typ, ctx);
            m_currentSymbolTable.enterOrReplace(entry);
        } else if (ctx.lengthDefinitionType() instanceof SmallPearlParser.LengthDefinitionFloatTypeContext) {
            TypeFloat typ = new TypeFloat(Integer.valueOf((ctx.IntegerConstant().toString())));

            if (typ.getPrecision() != Defaults.FLOAT_SHORT_PRECISION && typ.getPrecision() != Defaults.FLOAT_LONG_PRECISION) {
                throw new PrecisionNotSupportedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

            LengthEntry entry = new LengthEntry(typ, ctx);
            m_currentSymbolTable.enterOrReplace(entry);
        } else if (ctx.lengthDefinitionType() instanceof SmallPearlParser.LengthDefinitionBitTypeContext) {
            TypeBit typ = new TypeBit(Integer.valueOf((ctx.IntegerConstant().toString())));
            LengthEntry entry = new LengthEntry(typ, ctx);
            m_currentSymbolTable.enterOrReplace(entry);
        } else if (ctx.lengthDefinitionType() instanceof SmallPearlParser.LengthDefinitionCharacterTypeContext) {
            TypeChar typ = new TypeChar(Integer.valueOf((ctx.IntegerConstant().toString())));
            LengthEntry entry = new LengthEntry(typ, ctx);
            m_currentSymbolTable.enterOrReplace(entry);
        }

        return null;
    }

    private Void addArrayDescriptor(ArrayDescriptor descriptor) {
        boolean found = false;
        for (int i = 0; i < m_listOfArrayDescriptors.size(); i++) {
            if (m_listOfArrayDescriptors.get(i).equals(descriptor)) {
                found = true;
            }
        }

        if (!found) {
            m_listOfArrayDescriptors.add(descriptor);
        }
        return null;
    }


    private ArrayList<Initializer> getInitialisationAttribute(SmallPearlParser.InitialisationAttributeContext ctx) {
        Log.debug("SymbolTableVisitor:getInitialisationAttribute:ctx" + CommonUtils.printContext(ctx));

        ArrayList<Initializer> initElementList = new ArrayList<>();

        if (ctx != null) {
            for (int i = 0; i < ctx.initElement().size(); i++) {
                ConstantValue constant = getInitElement(ctx.initElement(i));
                
                SimpleInitializer initializer = new SimpleInitializer(ctx.initElement(i), constant);
                initElementList.add(initializer);
//TODO: MERGE                Initializer initializer = new Initializer(ctx.initElement(i), constant);
//                initElementList.add(initializer);
            }
        }

        if (initElementList.size() > 0) {
            return initElementList;
        } else {
            return null;
        }
    }

    private ConstantValue getInitElement(SmallPearlParser.InitElementContext ctx) {
        ConstantValue constant = null;

        Log.debug("SymbolTableVisitor:getInitElement:ctx" + CommonUtils.printContext(ctx));

        if (ctx.constantExpression() != null) {
            constant = getConstantExpression(ctx.constantExpression());
        } else if (ctx.constant() != null) {
            constant = getConstant(ctx.constant());
        } else if (ctx.ID() != null) {
            SymbolTableEntry entry = this.m_currentSymbolTable.lookup(ctx.ID().toString());

            if ( entry == null ) {
                throw new UnknownIdentifierException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine(),(ctx.ID().toString()));
            }

            if ( entry instanceof VariableEntry) {
                VariableEntry var = (VariableEntry)entry;

                if ( var.getAssigmentProtection()) {
                    if ( var.getInitializer() instanceof SimpleInitializer) {
                        constant = ((SimpleInitializer)var.getInitializer()).getConstant();
                    }
                    else {
                        throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
                    }
                }
                else {
                    throw new TypeMismatchException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine(),(ctx.ID().toString()));
                }
            }
            else {
                throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }
        }

        constant = m_constantPool.add(constant);
        return constant;
    }

    private int getPrecisionByType(TypeDefinition type) {
        int precision =  0;

        if ( type instanceof TypeFixed) {
            precision = ((TypeFixed)type).getPrecision();
        } else if ( type instanceof TypeFloat) {
            precision = ((TypeFloat)type).getPrecision();
        } else if ( type instanceof TypeBit) {
            precision = ((TypeBit)type).getPrecision();
        } else if ( type instanceof TypeArray) {
            TypeArray arrType = (TypeArray)type;
            precision = getPrecisionByType(arrType.getBaseType());
        } else {
            throw new InternalCompilerErrorException("Cannot determine lenght of type");
        }

        return precision;
    }

    private ConstantValue getConstant(SmallPearlParser.ConstantContext ctx) {
        ConstantValue constant = null;
        int sign = 1;

        if ( ctx.sign() != null ) {
            if ( ctx.sign() instanceof SmallPearlParser.SignMinusContext ) {
                sign = -1;
            }
        }

//        if ( ctx.stringConstant() != null) {
//            constant = new ConstantCharacterValue(ctx.stringConstant().StringLiteral().toString());
//        }
//        else 
          if ( ctx.fixedConstant() != null) {
            long curval = sign * Long.parseLong(ctx.fixedConstant().IntegerConstant().toString());
            int curlen =   m_currentSymbolTable.lookupDefaultFixedLength();

            if ( ctx.fixedConstant().fixedNumberPrecision() != null ) {
                curlen = Integer.parseInt(ctx.fixedConstant().fixedNumberPrecision().IntegerConstant().toString());
            } else {
                curlen = getPrecisionByType(m_type);
            }

            constant = new ConstantFixedValue(curval,curlen);
        }
        else if ( ctx.floatingPointConstant() != null) {
            double curval = sign * CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());
            int curlen = 0;

            if ( m_type instanceof TypeArray) {
                curlen = ((TypeArray)m_type).getBaseType().getPrecision();
            } else {
                curlen = ((TypeFloat)m_type).getPrecision();
            }

            constant = new ConstantFloatValue(curval,curlen);
        }
        else if ( ctx.durationConstant() != null) {
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

            constant = new ConstantDurationValue(hours, minutes, seconds);
//  TODO MS          }
//            else if ( m_type instanceof TypeClock) {
//                constant = new ConstantClockValue(hours, minutes, seconds);
//            }
//            else {
//                throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//            }

        }
        else if ( ctx.bitStringConstant() != null) {
            long value = CommonUtils.convertBitStringToLong(ctx.bitStringConstant().BitStringLiteral().getText());
            int  nb = CommonUtils.getBitStringLength(ctx.bitStringConstant().BitStringLiteral().getText());
            constant = new ConstantBitValue(value,nb);
        }
        else if ( ctx.timeConstant() != null) {
            Integer hours = 0;
            Integer minutes = 0;
            Double seconds = 0.0;

            hours = Integer.valueOf(ctx.timeConstant().IntegerConstant(0).toString());
            minutes = Integer.valueOf(ctx.timeConstant().IntegerConstant(1).toString());

            if (ctx.timeConstant().floatingPointConstant() != null) {
                seconds = CommonUtils.getFloatingPointConstantValue(ctx.timeConstant().floatingPointConstant());
            }
            else if (ctx.timeConstant().IntegerConstant(2) != null) {
                seconds = Double.valueOf(ctx.timeConstant().IntegerConstant(2).toString());
            }
            else {
                throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
            }

            constant = new ConstantClockValue(hours, minutes, seconds);
        }
        else if ( ctx.stringConstant() != null) {
            String s = ctx.stringConstant().StringLiteral().toString();
           // s = CommonUtils.removeQuotes(s);
            //s = CommonUtils.compressPearlString(s);
            ConstantCharacterValue ccv = new ConstantCharacterValue(s);
            if (ccv.getLength() == 0) {
              ErrorStack.enter(ctx,"character string constant");
              ErrorStack.add("need at least 1 character");
              ErrorStack.leave();
            }
            // continue with wrong constant for further analysis
            constant = ccv;
        }

        return constant;
    }

    private ConstantValue getConstantExpression(SmallPearlParser.ConstantExpressionContext ctx) {
        Log.debug("SymbolTableVisitor:getConstantExpression:ctx" + CommonUtils.printContext(ctx));

        ConstantFixedExpressionEvaluator evaluator = new ConstantFixedExpressionEvaluator(m_verbose, m_debug, m_currentSymbolTable, null, null);
        ConstantValue constant = evaluator.visit(ctx.constantFixedExpression());

        return constant;
    }


	/* moved to CommonUtils
	 private ConstantFixedValue getConstantFixedExpression(SmallPearlParser.ConstantFixedExpressionContext ctx) {
	 	ConstantFixedExpressionEvaluator evaluator = new ConstantFixedExpressionEvaluator(m_verbose, m_debug, m_currentSymbolTable,null, null);
		ConstantFixedValue constant = evaluator.visit(ctx);

		return constant;
	}
	*/

    @Override
    public Void visitDationSpecification(SmallPearlParser.DationSpecificationContext ctx) {
        LinkedList<ModuleEntry> listOfModules = this.symbolTable.getModules();

        Log.debug("SymbolTableVisitor:visitDationSpecification:ctx" + CommonUtils.printContext(ctx));

        if (listOfModules.size() > 1) {
            throw new NotYetImplementedException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        ModuleEntry moduleEntry = listOfModules.get(0);
        SymbolTable symbolTable = moduleEntry.scope;

        symbolTable.setUsesSystemElements();

        /* ---------------- */


        if (m_verbose > 0) {
            System.out.println("SymbolTableVisitor: visitDationSpecification");
        }

        ErrorStack.enter(ctx, "DationSPC");


        visitTypeDation(ctx.typeDation());
        TypeDation d = (TypeDation)m_type;
        d.setIsDeclaration(false);

        treatIdentifierDenotation(ctx.identifierDenotation(), d);

        if (ctx.globalAttribute() != null) {
            treatGlobalAttribute(ctx.globalAttribute(), d);
        }

        //
        ErrorStack.leave();

        /* ---------------- */
        return null;
    }

    /**
     * add specified interrupts to the symbol table
     * maybe this would be better placed in SymbolTableVisitor
     */
    @Override
    public Void visitInterruptSpecification(
            SmallPearlParser.InterruptSpecificationContext ctx) {

        boolean isGlobal = false;

        if (m_verbose > 0) {
            System.out
                    .println("Semantic: Check RT-statements: visitInterruptSpecification");
        }

        if (ctx.globalAttribute() != null) {
            isGlobal = true;
        }

        for (int i = 0; i < ctx.ID().size(); i++) {
            String iName = ctx.ID(i).toString();
            SymbolTableEntry se = m_currentSymbolTable.lookup(iName);
            if (se != null) {
                ErrorStack.add("'" + iName + "' already defined as " + se.toString());
            } else {
                InterruptEntry ie = new InterruptEntry(iName, isGlobal, ctx);
                m_currentSymbolTable.enter(ie);
            }

        }
        return null;
    }

    @Override
    public Void visitStructVariableDeclaration(SmallPearlParser.StructVariableDeclarationContext ctx) {
        Log.debug("SymbolTableVisitor:visitStructVariableDeclaration:ctx" + CommonUtils.printContext(ctx));
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitStructureDenotation(SmallPearlParser.StructureDenotationContext ctx) {
        Log.debug("SymbolTableVisitor:visitStructureDenotation:ctx" + CommonUtils.printContext(ctx));
        boolean hasAssignmentProtection = false;

        ArrayList<Initializer> initElementList = null;
        SymbolTableEntry entry = this.m_currentSymbolTable.lookupLocal(ctx.ID().toString());

        if (entry != null) {
            throw new DoubleDeclarationException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        m_typeStructure = null;
        visitTypeStructure(ctx.typeStructure());

        if ( ctx.dimensionAttribute() != null ) {
            m_type = new TypeArray();
            visitDimensionAttribute(ctx.dimensionAttribute());
            ((TypeArray) m_type).setBaseType(m_typeStructure);
        } else if ( ctx.typeStructure() != null ) {
            m_type = m_typeStructure;
        } else {
            throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
        }

        if (ctx.initialisationAttribute() != null) {
            initElementList = getInitialisationAttribute(ctx.initialisationAttribute());
        }

        ArrayOrStructureInitializer arrayOrStructureInitializer = null;

        if ( initElementList != null && initElementList.size() > 0) {
            arrayOrStructureInitializer = new ArrayOrStructureInitializer(ctx, initElementList);
        }
        else {
            arrayOrStructureInitializer = null;
        }

        int no = symbolTable.getNumberOfComponents(m_type);
        hasAssignmentProtection = ctx.assignmentProtection() != null;

        VariableEntry variableEntry = new VariableEntry(ctx.ID().toString(), m_type, hasAssignmentProtection, ctx, arrayOrStructureInitializer);
        m_currentSymbolTable.enter(variableEntry);

        return null;
    }

    @Override
    public Void visitTypeStructure(SmallPearlParser.TypeStructureContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeStructure:ctx" + CommonUtils.printContext(ctx));

        m_typeStructure = new TypeStructure();
        m_type = m_typeStructure;

        for ( int i = 0; i < ctx.structureComponent().size(); i++ ) {
            visitStructureComponent(ctx.structureComponent(i));
        }

        return null;
    }

    @Override
    public Void visitStructureComponent(SmallPearlParser.StructureComponentContext ctx) {
        Log.debug("SymbolTableVisitor:visitStructureComponent:ctx" + CommonUtils.printContext(ctx));
        TypeDefinition type_saved = m_type;
        TypeArray array = null;

        StructureComponent component = null;

        TypeStructure saved_typeStructure = m_typeStructure;

        if ( ctx.dimensionAttribute() != null ) {
            Log.debug("SymbolTableVisitor:visitStructureComponent: ARRAY");
            array = new TypeArray();
            m_type = array;
            visitDimensionAttribute(ctx.dimensionAttribute());
            addArrayDescriptor(new ArrayDescriptor(array.getNoOfDimensions(), array.getDimensions()));
        }

        if ( ctx.typeAttributeInStructureComponent() != null ) {
            type_saved = m_type;
            visitTypeAttributeInStructureComponent(ctx.typeAttributeInStructureComponent());
            m_typeStructure = saved_typeStructure;

            for (int i = 0; i < ctx.ID().size(); i++) {
                component = new StructureComponent();

                if (ctx.dimensionAttribute() != null) {
                    array.setBaseType(m_type);
                    component.m_type = array;
                } else {
                    component.m_type = m_type;
                }

                component.m_id = ctx.ID(i).getText();
                saved_typeStructure.add(component);
            }
            m_type = m_typeStructure;
        }

        return null;
    }

    @Override
    public Void visitTypeAttributeInStructureComponent(SmallPearlParser.TypeAttributeInStructureComponentContext ctx) {
        Log.debug("SymbolTableVisitor:visitTypeAttributeInStructureComponent:ctx" + CommonUtils.printContext(ctx));

        if ( ctx.simpleType() != null ) {
            visitSimpleType(ctx.simpleType());
        }
        else if ( ctx.structuredType() != null ) {
            visitStructuredType( ctx.structuredType());
        }
        else if ( ctx.typeReference() != null ) {
          visitTypeReference( ctx.typeReference());
        }
        else {
           ErrorStack.addInternal(ctx,"SymbolTableVisitor::visitTypeAttributeInStructureComponent","missing alternative");
        }

        return null;
    }

    @Override
    public Void visitStructuredType(SmallPearlParser.StructuredTypeContext ctx) {
        Log.debug("SymbolTableVisitor:visitStructuredType:ctx" + CommonUtils.printContext(ctx));
        visitChildren(ctx);
        return null;
    }
}
