/*
 [The "BSD license"]
 Copyright (c) 2012-2020 Rainer Muelle & Marcel Schaible
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
 
/* changelog
2020-05-50 (rm): loopStatement: loopBody now as separate rule to  simplify the 
   treatment in the ExpressionTypeVisitor 
*/
grammar SmallPearl;

////////////////////////////////////////////////////////////////////////////////

@header
{
    package org.smallpearl.compiler;
    import org.smallpearl.compiler.SmallPearlLexer;
}

////////////////////////////////////////////////////////////////////////////////

tokens {
     Letter,
     Digit
}


////////////////////////////////////////////////////////////////////////////////
// Excerpt from PEARL 90 LANGUAGE REPORT, Version 2.2 September 1998, page 135
//
// Following meta characters are used in the syntax description:
//
// meta character      meaning
// --------------      ---------------------------------------------------------
// ::=                 introduction of a Name (nonterminal symbol) for a language
//                     form
// []                  bracketing of optional parts of a language form
// |                   separation of alternative parts of a language form
// {}                  putting together several elements to a new element
// ...                 one or multiple repetition of the preceding element
//                     (or of several elements bracketed by { } or [ ] )
// §                   separates an explaining comment from a language form Name
// /∗ ∗/               comment brackets: includes an explaining text, possibly
//                     explaining the language form in detail instead of a formal
//                     description
//
// All other elements occurring in the syntax rules are either Names of language
// forms or terminal symbols. Examples for terminal symbols are the PEARL keywords
// or the characters semicolon “;”, opening round bracket “(” and closing round
// bracket “)”, or the apostrophe “ ’ ”; the terminal symbols opening square
// bracket “[” and closing square bracket “]” are printed boldly to distinguish
// them from the meta symbols for optional parts. Attention: the round brackets
// are no meta characters and have thus no grouping effect!
////////////////////////////////////////////////////////////////////////////////

program:
  module+
  ;

////////////////////////////////////////////////////////////////////////////////

ID :   Letter ( Letter | Digit | '_' )* ;
//IDENTIFIER :   Letter ( Letter | Digit | '_' )* ;

////////////////////////////////////////////////////////////////////////////////

module:
  'MODULE' ( '(' ID ')' | ID ) ';' cpp_inline* system_part? problem_part? 'MODEND' ';'
  ;

////////////////////////////////////////////////////////////////////////////////

system_part:
    'SYSTEM' ';'
    ( username_declaration | user_configuration | cpp_inline )*
    ;

////////////////////////////////////////////////////////////////////////////////
//  SystemName ::=
//    Identifier [ (nni§index) ]
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  nni ::= IntegerWithoutPrecision§NonNegative
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// ExtensionProcessDevice ::=
//  ⇤nni§ChannelNumber [ ⇤nni§Position [ , nni§Width ] ]
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  DeviceAssociation ::=
//    UserName: SystemName [ ExtensionProcessDevice ]
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  UserName ::= Identifier
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  SystemName   Device
//  -----------  -------------------------------------------------
//  STDIN        standard input (console)
//  STDOUT       standard output (console)
//  SERIAL       for I/O with serial interfaces
//  DISC         for reading and writing files (disk, floppy,...)
//  DIGIO        digital I/O
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  DationSpecification ::=
//    { SPECIFY | SPC } IdentifierDenotation TypeDation [ GlobalAttribute ] ;
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  DationDeclaration ::=
//    {DECLARE | DCL} IdentifierDenotation TypeDation [GlobalAttribute]
//    CREATED (Name§SystemDefDation);
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  TypeDation ::=
//    DATION SourceSinkAttribute ClassAttribute [ Structure ] [ AccessAttribute ]
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  SourceSinkAttribute ::=
//    IN | OUT | INOUT
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//  Declaration ::=
//    LengthDefinition
//    | TypeDefinition
//    | VariableDeclaration
//    | FormatDeclaration
//    | ProcedureDeclaration
//    | TaskDeclaration
////////////////////////////////////////////////////////////////////////////////

problem_part:
    'PROBLEM' ';'
    (
          lengthDefinition
        | typeDefinition
        | scalarVariableDeclaration
        | structVariableDeclaration
        | arrayVariableDeclaration
        | semaDeclaration
        | boltDeclaration
        | interruptSpecification
        | identification
        | dationSpecification
        | dationDeclaration
        | taskDeclaration
        | procedureDeclaration
        | cpp_inline
    )*
    ;

////////////////////////////////////////////////////////////////////////////////

// Changes added by Hertwig ////////////////////////////////////////////////////
//
// Possible composition of the PEARL system part (new syntax)
// Thus the grammar must be capable of dealing with this type of declaration
// ...
// i2cbus : i2cbus_1 ('/dev/i2cbus', 100000);
// tempsensor : i2cbus <- LM75(100.000);	1st version
// tempsensor : IN : i2cbus <--- LM75(100.000);	2nd version
// tempsensor : IN : i2cbus --- LM75(100.000);	3rd version (final)

username_declaration:
    ID ':' (username_declaration_without_data_flow_direction |
			username_declaration_with_data_flow_direction ) ';'
	;

////////////////////////////////////////////////////////////////////////////////

username_declaration_without_data_flow_direction:
	ID username_parameters?
	;

////////////////////////////////////////////////////////////////////////////////

username_declaration_with_data_flow_direction:
    ID username_parameters '---' ID username_parameters?
    ;

////////////////////////////////////////////////////////////////////////////////

user_configuration:
      user_configuration_without_association        #userConfigurationWithoutAssociation
    | user_configuration_with_association           #userConfigurationWithAssociation
    ;

////////////////////////////////////////////////////////////////////////////////

user_configuration_without_association:
    ID username_parameters? ';'
    ;

////////////////////////////////////////////////////////////////////////////////

user_configuration_with_association:
    ID username_parameters? '---' ID ';'
    ;

////////////////////////////////////////////////////////////////////////////////

username_parameters:
    '(' literal (',' literal)* ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// TODO: Identification ::=
//           { SPECIFY | SPC } Identifier [ AllocationProtection ] Type IdentificationAttribute ;

identification:
    ( 'SPECIFY' | 'SPC' ) ID  allocation_protection? type? identification_attribute? globalAttribute? ';'
    ;

////////////////////////////////////////////////////////////////////////////////

allocation_protection:
    ID
    ;

////////////////////////////////////////////////////////////////////////////////
//  IdentificationAttribute ::=
//    IDENT ( Name§Object )
////////////////////////////////////////////////////////////////////////////////

identification_attribute:
    'IDENT' '(' ID ')'
    ;

////////////////////////////////////////////////////////////////////////////////
//
//  ScalarVariableDeclaration ::=
//      { DECLARE | DCL } VariableDenotation [ , VariableDenotation ] ... ;
//
//  VariableDenotation ::=
//      IdentifierDenotation [ AllocationProtection ] TypeAttribute [ GlobalAttribute ] [ InitialisationAttribute ]
//
//  IdentifierDenotation ::=
//      Identifier | (Identifier [ , Identifier ] ... )
//
//  TypeAttribute ::=
//      SimpleType | TypeReference | Identifier§ForType
//
//  SimpleType ::=
//      TypeInteger | TypeFloatingPointNumber | TypeBitString | TypeCharacterString | TypeTime | TypeDuration
//
//  InitialisationAttribute ::=
//      { INITIAL | INIT } ( InitElement [ , InitElement ] ... )
//
//  InitElement ::= Constant
//      | Identifier§NamedConstant | ConstantExpression ...
//
//  Constant ::= Integer
//      | FloatingPointNumber | BitStringConstant
//      | TimeConstant
//      | DurationConstant
//      | NIL
//
////////////////////////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////////////////////////
//   TypeDefinition ::=
//     TYPE Identifier§ForType { SimpleType | TypeStructure } ;
////////////////////////////////////////////////////////////////////////////////

typeDefinition :
    'TYPE' ID ( simpleType | typeStructure | ID) ';'
    ;

////////////////////////////////////////////////////////////////////////////////
//  ScalarVariableDeclaration ::=
//      { DECLARE | DCL } VariableDenotation [ , VariableDenotation ] ... ;
////////////////////////////////////////////////////////////////////////////////

scalarVariableDeclaration :
    ( 'DECLARE' | 'DCL' ) variableDenotation ( ',' variableDenotation )* ';'
    | cpp_inline
    ;

////////////////////////////////////////////////////////////////////////////////
//  VariableDenotation ::=
//      IdentifierDenotation [ AllocationProtection ] TypeAttribute [ GlobalAttribute ] [ InitialisationAttribute ]
////////////////////////////////////////////////////////////////////////////////


variableDenotation :
    identifierDenotation allocationProtection? typeAttribute globalAttribute? initialisationAttribute?
    ;

////////////////////////////////////////////////////////////////////////////////

allocationProtection :
    'INV'
    ;

////////////////////////////////////////////////////////////////////////////////
// GlobalAttribute ::=
//      GLOBAL [ (Identifier§OfaModule) ]
////////////////////////////////////////////////////////////////////////////////

globalAttribute :
    'GLOBAL'  ( '(' ID ')' )?
    ;

////////////////////////////////////////////////////////////////////////////////
//  TypeAttribute ::=
//      SimpleType | TypeReference | Identifier§ForType
////////////////////////////////////////////////////////////////////////////////

typeAttribute
    : simpleType
    | typeReference
    | ID
    ;

////////////////////////////////////////////////////////////////////////////////
// SimpleType ::=
//    TypeInteger | TypeFloatingPointNumber | TypeBitString | TypeCharacterString | TypeTime | TypeDuration
////////////////////////////////////////////////////////////////////////////////

simpleType :
       typeInteger
     | typeFloatingPointNumber
     | typeBitString
     | typeCharacterString
     | typeTime
     | typeDuration
    ;

////////////////////////////////////////////////////////////////////////////////
// TypeInteger ::=
//      FIXED [ (Precision) ]
////////////////////////////////////////////////////////////////////////////////

typeInteger :
    'FIXED' ( '(' mprecision ')' )?
    ;

////////////////////////////////////////////////////////////////////////////////
// Precision ::=
//      IntegerWithoutPrecision§GreaterZero
////////////////////////////////////////////////////////////////////////////////

mprecision :
    integerWithoutPrecision
    ;

integerWithoutPrecision :
    IntegerConstant
    ;

////////////////////////////////////////////////////////////////////////////////
//  Integer ::=
//    IntegerWithoutPrecision [ ( Precision ) ]
////////////////////////////////////////////////////////////////////////////////

typeFloatingPointNumber :
    'FLOAT' ( '(' IntegerConstant ')' )?
    ;

typeBitString :
    'BIT' ( '(' IntegerConstant ')' )?
    ;

typeCharacterString :
    ( 'CHARACTER' | 'CHAR' ) ( '(' IntegerConstant ')' )?
    ;

typeDuration :
    'DURATION' | 'DUR'
    ;

////////////////////////////////////////////////////////////////////////////////
//  IdentifierDenotation ::=
//      Identifier | (Identifier [ , Identifier ] ... )
////////////////////////////////////////////////////////////////////////////////

identifierDenotation :
    ID | '(' ID ( ',' ID )*  ')'
    ;

////////////////////////////////////////////////////////////////////////////////
//  InitialisationAttribute ::=
//      { INITIAL | INIT } ( InitElement [ , InitElement ] ... )
////////////////////////////////////////////////////////////////////////////////

initialisationAttribute :
    ( 'INITIAL' | 'INIT' ) '(' initElement ( ',' initElement )* ')'
    ;

////////////////////////////////////////////////////////////////////////////////
//  InitElement ::= Constant
//      | Identifier§NamedConstant | ConstantExpression ...
////////////////////////////////////////////////////////////////////////////////

initElement
    : ID
    | constant
    | constantExpression
    ;

////////////////////////////////////////////////////////////////////////////////
// StructureDeclaration ::=
//   { DECLARE | DCL } StructureDenotation [ , StructureDenotation ] ... ;
////////////////////////////////////////////////////////////////////////////////

structVariableDeclaration :
    ( 'DECLARE' | 'DCL' ) structureDenotation ( ',' structureDenotation )* ';'
    | cpp_inline
    ;

////////////////////////////////////////////////////////////////////////////////
// StructureDenotation ::=
//   IdentifierDenotation§MainStructure [ DimensionAttribute ]
//   TypeStructure [ GlobalAttribute ][ InitialisationAttribute ]
////////////////////////////////////////////////////////////////////////////////

structureDenotation :
    ID dimensionAttribute? assignmentProtection? typeStructure globalAttribute? initialisationAttribute?
    ;

////////////////////////////////////////////////////////////////////////////////
// TypeStructure ::=
//   STRUCT [ StructureComponent [ , StructureComponent ] ... ]
////////////////////////////////////////////////////////////////////////////////

typeStructure :
    'STRUCT' '[' structureComponent ( ',' structureComponent )* ']'
    ;

////////////////////////////////////////////////////////////////////////////////
// StructureComponent ::=
//   OneIdentifierOrList§ForStructureComponent [ DimensionAttribute ]
//   TypeAttributeInStructureComponent
////////////////////////////////////////////////////////////////////////////////

structureComponent :
    ( ID | '(' ID ( ',' ID)* ')' ) dimensionAttribute? assignmentProtection? typeAttributeInStructureComponent
    ;

////////////////////////////////////////////////////////////////////////////////
// TypeAttributeInStructureComponent ::=
//   [ INV ] { SimpleType | StructuredType | TypeReference }
////////////////////////////////////////////////////////////////////////////////

typeAttributeInStructureComponent :
    (simpleType | structuredType | typeReference )
    ;

////////////////////////////////////////////////////////////////////////////////
// StructuredType ::=
//   TypeStructure | Identifier§ForNewDefinedType
////////////////////////////////////////////////////////////////////////////////

structuredType :
    typeStructure | ID
    ;

////////////////////////////////////////////////////////////////////////////////
// StructureSpecification ::=
//   { SPECIFY | SPC } StructureDenotationS [ , StructureDenotationS ] ... ;
////////////////////////////////////////////////////////////////////////////////

structureSpecfication :
    ;

////////////////////////////////////////////////////////////////////////////////
// StructureDenotationS ::=
//   OneIdentifierOrList§MainRecord [ VirtualDimensionList ]
//   [ INV ] TypeStructure { GlobalAttribute | IdentificationAttribute}
////////////////////////////////////////////////////////////////////////////////

structureDenotationS :
    ;

////////////////////////////////////////////////////////////////////////////////
// ArrayDeclaration ::=
//   { DECLARE | DCL } ArrayDenotation [ , ArrayDenotation ] ... ;
////////////////////////////////////////////////////////////////////////////////

arrayVariableDeclaration :
    ( 'DECLARE' | 'DCL' ) arrayDenotation ( ',' arrayDenotation )* ';'
    | cpp_inline
    ;

////////////////////////////////////////////////////////////////////////////////
// ArrayDenotation ::=
//   OneIdentifierOrList DimensionAttribute [ INV ] TypeAttributeForArray
//  [ GlobalAttribute ] [ InitialisationAttribute ]
////////////////////////////////////////////////////////////////////////////////

arrayDenotation :
     ( ID | '(' ID ( ',' ID)* ')' ) dimensionAttribute assignmentProtection? typeAttributeForArray globalAttribute?
     initialisationAttribute?
    ;

////////////////////////////////////////////////////////////////////////////////
// TypeAttributeForArray ::=
//    TypeInteger | TypeFloat
//  | TypeDuration | TypeClock
//  | TypeBitString | TypeCharacterString
//  | TypeDefinition
//  | REF { TypeDation | SEMA | BOLT
//  | typeRefedure | TASK | CHAR()
//  ??? INTERRUPT, IPRT,SIGNAL
////////////////////////////////////////////////////////////////////////////////

typeAttributeForArray :
      type_fixed
    | type_float
    | type_duration
    | type_clock
    | type_bit
    | type_char
    | typeReference
    ;

////////////////////////////////////////////////////////////////////////////////
// TypeReference ::=
//   REF
//   { [ VirtualDimensionList ] [ INV ] { SimpleType | StructuredType }
//   | [ VirtualDimensionList ] { TypeDation | SEMA | BOLT }
//   | TypeProcedure | TASK | INTERRUPT | IRPT | SIGNAL
//   | CHAR( )
//   }
////////////////////////////////////////////////////////////////////////////////

typeReference :
	'REF' assignmentProtection? virtualDimensionList?
    (
	 simpleType 
	| typeStructure 
	| typeDation
	| typeProcedure		
	| typeReferenceTaskType				//<< should become renamed to typeTask as well as taskType -> typeTask
	| typeReferenceSemaType
	| typeReferenceBoltType
	| typeReferenceInterruptType		// << should become renamed to interruptType
	| typeReferenceSignalType			// << should become renamed to signalType
	| typeRefChar						// should stay typeRefChar; since type_char is in simpleType
	)
	;

typeRefChar:
	'CHAR' '('  ')' 
	;
	
/*
replaced by definition above
typeReferences
    : typeReferenceSimpleType
    | typeReferenceStructuredType
    | typeReferenceDationType
    | typeReferenceSemaType
    | typeReferenceBoltType
    | typeReferenceProcedureType
    | typeReferenceTaskType
    | typeReferenceInterruptType
    | typeReferenceSignalType
    ;


typeReferenceSimpleType
    : assignmentProtection? simpleType
    ;
*/

typeReferenceStructuredType
    :
    ;

typeReferenceDationType
    :
    ;

typeReferenceSemaType
    : 'SEMA'
    ;

typeReferenceBoltType
    : 'BOLT'
    ;

typeReferenceProcedureType
    : ('PROCEDURE' | 'PROC' )  listOfFormalParameters? resultAttribute? globalAttribute?
    ;

typeReferenceTaskType
    : 'TASK'
    ;

typeReferenceInterruptType
    : ( 'INTERRUPT' | 'IRPT' )
    ;

typeReferenceSignalType
    : 'SIGNAL'
    ;

typeReferenceCharType
    : 'CHAR' ( '(' expression ')' )?
    ;

////////////////////////////////////////////////////////////////////////////////
//  SemaDeclaration ::=
//   { DECLARE | DCL } Identifier or IdentifierList [ DimensionAttribute ] SEMA [ GlobalAttribute ]
////////////////////////////////////////////////////////////////////////////////

semaDeclaration :
    ( 'DECLARE' | 'DCL' ) identifierDenotation 'SEMA' globalAttribute? preset? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
//   [ PRESET (IntegerWithoutPrecision [ , IntegerWithoutPrecision ] ... ) ];
////////////////////////////////////////////////////////////////////////////////

preset :
    'PRESET' '(' integerWithoutPrecision (',' integerWithoutPrecision )* ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// ProcedureDeclaration ::=
//   Identifier: { PROCEDURE | PROC } [ ListOfFormalParameters ] [ ResultAttribute ] [ GlobalAttribute ] ;
//      ProcedureBody
//   END;
////////////////////////////////////////////////////////////////////////////////

procedureDeclaration:
//	 ID ':' ( 'PROCEDURE' | 'PROC' ) listOfFormalParameters? resultAttribute? globalAttribute? ';'
	 ID ':' typeProcedure globalAttribute? ';'
        procedureBody
      'END' ';'
    ;


typeProcedure:
	 ( 'PROCEDURE' | 'PROC' ) listOfFormalParameters? resultAttribute?
	 ;
	 
////////////////////////////////////////////////////////////////////////////////
// ProcedureBody ::=
//   [ Declaration... ] [ Statement... ]
////////////////////////////////////////////////////////////////////////////////

procedureBody :
    ( scalarVariableDeclaration | structVariableDeclaration | arrayVariableDeclaration | dationDeclaration | lengthDefinition)*
    statement*
    ;

////////////////////////////////////////////////////////////////////////////////
// ListOfFormalParameters ::=
//   (FormalParameter [ , FormalParameter ]...)
////////////////////////////////////////////////////////////////////////////////

listOfFormalParameters :
   '(' formalParameter ( ',' formalParameter )* ')'
   ;

////////////////////////////////////////////////////////////////////////////////
// FormalParameter ::=
//   Identifier or IdentifierList [ VirtualDimensionList ] [ AssignmentProtection ] ParameterType [ IDENTICAL | IDENT ]
////////////////////////////////////////////////////////////////////////////////

formalParameter :
    ( ID | '(' ID ( ',' ID)* ')' ) virtualDimensionList? assignmentProtection? parameterType passIdentical?
    ;

virtualDimensionList :
	'('  commas  ')'
       | '(' ')'
       | '()'
	;

commas:
	',' (',')*
	;

assignmentProtection :
    'INV'
    ;

passIdentical:
    'IDENTICAL' | 'IDENT'
    ;

////////////////////////////////////////////////////////////////////////////////
// VirtualDimensionList ::= ([,... ])
////////////////////////////////////////////////////////////////////////////////
virtualDimensionList2:
    '(' (',')* ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// TODO: ParameterType ::=
//   SimpleType | TypeReference | TypeStructure
//  | Identifier§ForType | TypeDation | TypeRealTimeObject
////////////////////////////////////////////////////////////////////////////////

parameterType :
      simpleType
    | typeDation
    | typeReference
    | typeStructure
    ;


////////////////////////////////////////////////////////////////////////////////
// DisableStatement ::=
//   DISABLE Name§Interrupt;
////////////////////////////////////////////////////////////////////////////////

disableStatement :
    'DISABLE' name ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// EnableStatement ::=
//   ENABLE Name§Interrupt;
////////////////////////////////////////////////////////////////////////////////

enableStatement :
    'ENABLE' name ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// TriggerStatement ::=
//   TRIGGER Name§Interrupt;
////////////////////////////////////////////////////////////////////////////////

triggerStatement :
    'TRIGGER' name ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// ResultAttribute ::=
//   RETURNS(ResultType)
////////////////////////////////////////////////////////////////////////////////

resultAttribute :
    'RETURNS' '(' resultType ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// ResultType ::=
//   SimpleType | TypeReference | TypeStructure | Identifier§ForType
////////////////////////////////////////////////////////////////////////////////

resultType :
      simpleType
    | typeReference
    | typeStructure
    | ID
    ;

////////////////////////////////////////////////////////////////////////////////
// TODO: { SPECIFY | SPC } Identifier§Procedure { ENTRY | [ : ] PROC }
//   [ ListOfParametersFor-SPC ] [ResultAttribute ] GlobalAttribute ;
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// TODO: ListOfParametersFor-SPC ::=
//   (ParameterSpecification [ , ParameterSpecification ]...)
////////////////////////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////////////////////////
// TODO: ParameterSpecification ::=
//   [ Identifier ] [ VirtualDimensionList ] [ AssignmentProtection ] ParameterType [ IDENTICAL | IDENT ]
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// TaskDeclaration ::=
//   Identifier: TASK [ PriorityAttribute ] [ MAIN ] [ GlobalAttribute ] ; TaskBody
//  END;
////////////////////////////////////////////////////////////////////////////////

taskDeclaration :
    ID ':' 'TASK' priority? task_main? ';' taskBody 'END' ';' cpp_inline?
    ;

////////////////////////////////////////////////////////////////////////////////

task_main: 'MAIN';

////////////////////////////////////////////////////////////////////////////////

taskBody:
    ( scalarVariableDeclaration | structVariableDeclaration | arrayVariableDeclaration | dationDeclaration | lengthDefinition)*
    procedureDeclaration*
    statement*
    ;


////////////////////////////////////////////////////////////////////////////////

statement:
        label_statement* ( unlabeled_statement | block_statement | cpp_inline )  ;

////////////////////////////////////////////////////////////////////////////////

unlabeled_statement:
      empty_statement
    | realtime_statement
    | interrupt_statement
    | assignment_statement
    | sequential_control_statement
    | io_statement
    | callStatement
    | returnStatement
    | gotoStatement
    | loopStatement
    | exitStatement
    | convertStatement
    ;

////////////////////////////////////////////////////////////////////////////////

empty_statement
    : ';'
    ;

////////////////////////////////////////////////////////////////////////////////

label_statement
    : ID ':'
    ;

////////////////////////////////////////////////////////////////////////////////
// CallStatement ::=
//   [ CALL ] Name§SubprogramProcedure [ ListOfActualParameters ] ;
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// ListOfActualParameters ::=
//   (Expression [ , Expression ]...)
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// Example:
// SPC Output PROC (P FIXED, N FIXED) GLOBAL;
// DCL (Pos, No) FIXED;
// ...
// ! Assignments to Pos and No
// CALL Output (Pos, No);
////////////////////////////////////////////////////////////////////////////////

callStatement
    : 'CALL'? ID listOfActualParameters?  ';'
    ;

////////////////////////////////////////////////////////////////////////////////

listOfActualParameters
    : '(' expression  ( ',' expression )* ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// ReturnStatement ::=
//   RETURN [ ( Expression ) ] ;
////////////////////////////////////////////////////////////////////////////////

returnStatement
    : 'RETURN' ( '(' expression ')' )? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// GoToStatement ::=
//   GOTO Identifier§Label ;
////////////////////////////////////////////////////////////////////////////////

gotoStatement
    : 'GOTO' ID ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// ExitStatement ::=
//   EXIT [ Identifier§BlockOrLoop ] ;
////////////////////////////////////////////////////////////////////////////////

exitStatement
    : 'EXIT' ID? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// Assignment ::=
//    ScalarAssignment | StructureAssignment | RefProcAssignment
////////////////////////////////////////////////////////////////////////////////

assignment_statement:
     (dereference)? name ( bitSelectionSlice | charSelectionSlice)?
       ( ':=' | '=' ) expression ';'
    ;

////////////////////////////////////////////////////////////////////////////////

dereference
    : 'CONT'
    ;

////////////////////////////////////////////////////////////////////////////////
// StructureAssignment ::=
//    Name§Structure 1 { := | = } Expression§Structure 2;
////////////////////////////////////////////////////////////////////////////////

// TODO: MS: 2020-05-23 Is this still relevant?
// 2020-05-28: stringSelection is still used in primaryExpression

////////////////////////////////////////////////////////////////////////////////

stringSelection:
  	  bitSelection
   	| charSelection
   	;

////////////////////////////////////////////////////////////////////////////////
// 2020-03-17 (rm) language report defines that .BIT may be applied on a name
//		x.BIT(3:5).BIT(2) is not allowed
//bitSelection:
//    name bitSelectionSlice+
bitSelection:
    name bitSelectionSlice
    ;

////////////////////////////////////////////////////////////////////////////////

bitSelectionSlice:
    '.' 'BIT' '(' 
    (
    	 expression
       | expression ':' expression '+' IntegerConstant	    	 
       | expression ':' expression   // << may be constant fixed or not
    )  
    ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// 2020-03-17 (rm) language report defines that .CHAR may be applied on a name
//		x.CHAR(3:5).CHAR(2) is not allowed
//charSelection:
//    name charSelectionSlice+
charSelection:
    name charSelectionSlice    
    ;

////////////////////////////////////////////////////////////////////////////////

charSelectionSlice:
 //   '.' ( 'CHAR' | 'CHARACTER' ) '('  expression ( ':' expression )? ')'
 //   ;
     '.' 'CHAR' '(' 
    (
    	 expression
       | expression ':' expression '+' IntegerConstant	    	 
       | expression ':' expression  // << may be constant fixed or not
    )  
    ')'
	;

////////////////////////////////////////////////////////////////////////////////

sequential_control_statement:
      if_statement
    | case_statement
    ;

////////////////////////////////////////////////////////////////////////////////
// IfStatement ::=
//   IF Expression§OfType-BIT(1)
//   THEN [ Statement ... ]
//  [ ELSE [ Statement ... ] ] FIN ;
////////////////////////////////////////////////////////////////////////////////

if_statement
    : 'IF' expression then_block else_block? 'FIN' ';'
    ;

////////////////////////////////////////////////////////////////////////////////

then_block
    : 'THEN' statement+
    ;

////////////////////////////////////////////////////////////////////////////////

else_block
    : 'ELSE' statement+
    ;

////////////////////////////////////////////////////////////////////////////////
// CaseStatement ::=
//   StatementSelection1 | StatementSelection2
////////////////////////////////////////////////////////////////////////////////

case_statement
    : 'CASE' ( case_statement_selection1 | case_statement_selection2 ) 'FIN' ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// CaseStatement1 ::=
//   CASE Expression§WithIntegerAsValue
//     { ALT [ Statement ... ] } ...
//     [ OUT [ Statement ... ] ]
//   FIN ;
////////////////////////////////////////////////////////////////////////////////

case_statement_selection1
    : expression case_statement_selection1_alt+ case_statement_selection_out?
    ;

case_statement_selection1_alt
    : 'ALT' statement+
    ;

case_statement_selection_out
    : 'OUT' statement+
    ;

////////////////////////////////////////////////////////////////////////////////
// CaseStatement2 ::=
//   CASE CaseIndex
//     { ALT ( CaseList ) [ Statement ... ] }...
//     [ OUT [ Statement ... ] ]
//    FIN ;
////////////////////////////////////////////////////////////////////////////////
// CaseIndex ::=
//    Expression§WithValueOfType-FIXED-Or-CHAR(1)
////////////////////////////////////////////////////////////////////////////////

case_statement_selection2
    : expression case_statement_selection2_alt+ case_statement_selection_out?
    ;

case_statement_selection2_alt
    : 'ALT' case_list statement+
    ;

////////////////////////////////////////////////////////////////////////////////
// CaseList ::=
//   IndexSection [ , IndexSection ] ...
////////////////////////////////////////////////////////////////////////////////

case_list
    : '(' index_section ( ',' index_section)*  ')'
    ;

////////////////////////////////////////////////////////////////////////////////
//  IndexSection::=
//    Constant-FIXED-Expression [ : Constant-FIXED-Expression ]
//    | CharacterStringConstant§OfLength1 [ : CharacterStringConstant§OfLength1 ]
////////////////////////////////////////////////////////////////////////////////

index_section
    : constantFixedExpression ( ':' constantFixedExpression )?
    ;

////////////////////////////////////////////////////////////////////////////////
//  Block::=
//    BEGIN
//      [{ Declaration | Identification } ... ] [ Statement ... ]
//    END [ Identifier§Block ] ;
////////////////////////////////////////////////////////////////////////////////

block_statement:
    'BEGIN'
    ( scalarVariableDeclaration | structVariableDeclaration | arrayVariableDeclaration | dationDeclaration | lengthDefinition )*
    statement*
    'END' ID? ';'
    ;
////////////////////////////////////////////////////////////////////////////////

//  LoopStatement ::=
//    [ FOR Indicator§ControlVariable ] [ FROM Expression§InitialValue ] [ BY Expression§StepLength ]
//    [ TO Expression§EndValue ]
//    [ WHILE Expression§Condition ] REPEAT
//    [{ Declaration | Identification }... ]
//    [ Statement ... ] END [ Identifier§Loop ] ;
////////////////////////////////////////////////////////////////////////////////

loopStatement:
    loopStatement_for? loopStatement_from? loopStatement_by? loopStatement_to? loopStatement_while?
    'REPEAT'
       loopBody
    loopStatement_end ';'
    ;

loopBody:
    ( scalarVariableDeclaration | structVariableDeclaration | arrayVariableDeclaration | dationDeclaration | lengthDefinition)*
    statement*
    ;

loopStatement_for:
    'FOR' ID
     ;

loopStatement_from:
    'FROM' expression
     ;

loopStatement_by:
    'BY' expression
    ;

loopStatement_to:
    'TO' expression
     ;

loopStatement_while:
    'WHILE' expression
    ;

loopStatement_end:
    'END' ID?
    ;

////////////////////////////////////////////////////////////////////////////////

realtime_statement
    : task_control_statement
    | task_coordination_statement
    ;

////////////////////////////////////////////////////////////////////////////////

task_control_statement
    : taskStart
    | task_terminating
    | task_suspending
    | taskContinuation
    | taskResume
    | task_preventing
    ;

////////////////////////////////////////////////////////////////////////////////
// 2020-05-06: ID -> name for task control statements
task_terminating
    : 'TERMINATE' name? ';'
    ;

////////////////////////////////////////////////////////////////////////////////

task_suspending
    : 'SUSPEND' name? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// TaskContinuation ::=
//     [ SimpleStartCondition ] CONTINUE [ Name§Task ] [ Priority ]
//
//  SimpleStartCondition ::=
//      AT Expression§Time | AFTER Expression§Duration | WHEN Name§Interrupt
////////////////////////////////////////////////////////////////////////////////

taskContinuation
    : startCondition? 'CONTINUE' name? priority? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
//
// TaskDelay ::=
//      SimpleStartCondition RESUME;
//
// SimpleStartCondition ::=
//     AT Expression§Time | AFTER Expression§Duration | WHEN Name§Interrupt
////////////////////////////////////////////////////////////////////////////////

taskResume
    : startCondition 'RESUME' ';'
    ;

////////////////////////////////////////////////////////////////////////////////

task_preventing
    : 'PREVENT' name? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// TaskStart ::=
//      [ StartCondition ] ACTIVATE Name§Task [ Priority ];
////////////////////////////////////////////////////////////////////////////////

taskStart
    : startCondition? frequency? 'ACTIVATE' name  priority? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// Priority ::=
//   { PRIORITY | PRIO } Expression§WithPositiveIntegerAsValue
////////////////////////////////////////////////////////////////////////////////

priority
    : ( 'PRIORITY' | 'PRIO' ) expression
    ;

////////////////////////////////////////////////////////////////////////////////
// Frequency ::=
//   ALL Expression§Duration [ { UNTIL Expression§Time } | { DURING Expression§Duration } ]
////////////////////////////////////////////////////////////////////////////////

frequency
    : 'ALL' expression ( 'UNTIL' expression |  'DURING' expression )?
    ;

////////////////////////////////////////////////////////////////////////////////
// StartCondition ::=
//   AT Expression§Time [ Frequency ]
//   | AFTER Expression§Duration [ Frequency ]
//   | WHEN Name§Interrupt [ AFTER Expression§Duration ] [ Frequency ]
//   | Frequency
////////////////////////////////////////////////////////////////////////////////
// SimpleStartCondition ::=
//   AT Expression§Time | AFTER Expression§Duration | WHEN Name§Interrupt
////////////////////////////////////////////////////////////////////////////////

// need default visitor for startCondition
//startCondition													
//    :
//      'AFTER'   expression                                      # startConditionAFTER
//    | 'AT'      expression                                      # startConditionAT
//    | 'WHEN'    name  ( 'AFTER' expression)?                      # startConditionWHEN
//    ;
startCondition :
    (startConditionAFTER
    | startConditionAT
    | startConditionWHEN
    )
    ;

startConditionAFTER: 
    'AFTER'   expression
	;

startConditionAT: 
    'AT'   expression
	;

startConditionWHEN: 
    'WHEN'    name  ( 'AFTER' expression)?
	;

////////////////////////////////////////////////////////////////////////////////

task_coordination_statement
    : semaRequest
    | semaRelease
    | boltReserve
    | boltFree
    | boltEnter
    | boltLeave
    ;
    
// generic rule for semaphore and bolt operations, which have a list of names     
listOfNames : 
	name ( ',' name)* 
	;    

////////////////////////////////////////////////////////////////////////////////
// REQUEST Name§Sema [ , Name§Sema ]... ;
////////////////////////////////////////////////////////////////////////////////

semaRequest
    : 'REQUEST' listOfNames ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// RELEASE Name§Sema [ , Name§Sema ]...;
////////////////////////////////////////////////////////////////////////////////

semaRelease
    : 'RELEASE' listOfNames ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// TRY Name§Sema;
////////////////////////////////////////////////////////////////////////////////

semaTry
    : 'TRY' listOfNames
    ;


////////////////////////////////////////////////////////////////////////////////
// BoltDeclaration ::=
//   { DECLARE | DCL } Identifier or IdentifierList [ DimensionAttribute ] BOLT [ GlobalAttribute ] ;
//

boltDeclaration :
    ( 'DECLARE' | 'DCL' ) identifierDenotation 'BOLT' globalAttribute? ';'
    ;

// BoltSpecification ::=
//   { SPECIFY | SPC } Identifier or IdentifierList [ VirtualDimensionAttribute ]
//   BOLT { GlobalAttribute | IdentificationAttribute } ;
//


////////////////////////////////////////////////////////////////////////////////
// BoltStatement ::=
//      RESERVE Name§Bolt [ , Name§Bolt ] ... ;
//    | FREE    Name§Bolt [ , Name§Bolt ] ... ;
//    | ENTER   Name§Bolt [ , Name§Bolt ] ... ;
//    | LEAVE   Name§Bolt [ , Name§Bolt ] ... ;
////////////////////////////////////////////////////////////////////////////////

boltReserve:
     'RESERVE'  listOfNames ';'
      ;

////////////////////////////////////////////////////////////////////////////////

boltFree:
     'FREE' listOfNames ';'
      ;

////////////////////////////////////////////////////////////////////////////////

boltEnter:
     'ENTER'   listOfNames ';'
     ;

////////////////////////////////////////////////////////////////////////////////

boltLeave:
     'LEAVE'  listOfNames ';'
     ;

////////////////////////////////////////////////////////////////////////////////
// Interrupt-Anweisung ::=
//   { ENABLE | DISABLE | TRIGGER } Name§Interrupt ;
////////////////////////////////////////////////////////////////////////////////

interrupt_statement :
      enableStatement
    | disableStatement
    | triggerStatement
    ;

////////////////////////////////////////////////////////////////////////////////

io_statement:
      open_statement
    | close_statement
    | putStatement
    | getStatement
    | writeStatement
    | readStatement
    | sendStatement
    | takeStatement
    ;

////////////////////////////////////////////////////////////////////////////////

// OpenStatement ::=
//      OPEN Name§Dation [ BY OpenParameter [ , OpenParameter ] ... ] ;
//
// When executing the open statement, a data station with typology is positioned at its beginning.
// The open parameters serve to handle data stations containing identifyable files. E.g., a system
// defined data station Disk can possess a file TAB1, which is also maintained after terminating the program under
// this name. Later on, the same or another program can create a user defined data station Table on Disk,
// identified with file TAB1 in the open statement.
//
// OpenParameter ::=
//      IDF ( {Name§CharacterVariable | CharacterStringConstant } ) | RST (Name§ErrorVariable-FIXED) |
//      { OLD | NEW | ANY } |
//      { CAN | PRM }

open_statement:
    'OPEN' dationName ( 'BY' open_parameterlist )? ';'
    ;

////////////////////////////////////////////////////////////////////////////////

open_parameterlist:
    open_parameter (',' open_parameter)*
    ;

////////////////////////////////////////////////////////////////////////////////

open_parameter:
    open_parameter_idf
    | openClosePositionRST                 
    | open_parameter_old_new_any         
    | open_close_parameter_can_prm      
    ;

////////////////////////////////////////////////////////////////////////////////

open_parameter_idf:
    'IDF' '(' ( ID | StringLiteral ) ')'  	
    ;

//open_close_RST :
//	'RST' ( '(' ID ')' )
//	;


open_parameter_old_new_any:
      'OLD'                    
    | 'NEW'                   
    | 'ANY'                 
    ;

open_close_parameter_can_prm:
      'CAN'                
    | 'PRM'              
    ;

////////////////////////////////////////////////////////////////////////////////
// CloseStatement ::=
//   CLOSE Name§Dation [ BY CloseParameter [ , CloseParameter ] ... ] ;
////////////////////////////////////////////////////////////////////////////////

close_statement:
    'CLOSE' dationName ( 'BY' close_parameterlist)? ';'
    ;

////////////////////////////////////////////////////////////////////////////////

close_parameterlist:
    close_parameter (',' close_parameter)*
    ;

////////////////////////////////////////////////////////////////////////////////
// CloseParameter ::=
//   CAN | PRM | RST (Name§ErrorVariable-FIXED)
////////////////////////////////////////////////////////////////////////////////

close_parameter :
      open_close_parameter_can_prm
    | openClosePositionRST                 
     ;

//////////////////////////////////////////////////////////////////////////////////    
// let's treat all i/o statements (PUT,GET,READ,WRITE,TRAKE,SEND, CONVERT FROM/TO 
// identical here and verify in the semantic analysis if wrong elements are used
// --> allow non, one or multiple data 
// --> allow name, expression and arraySlice for all io statements
//
// and the same for the formats!

////////////////////////////////////////////////////////////////////////////////
// GetStatement ::=
//   GET [ { Name§Variable | Segment } [ , { Name§Variable | Segment } ] ... ] FROM Name§Dation [ BY FormatPosition [ , FormatPosition ] ... ] ;
////////////////////////////////////////////////////////////////////////////////

//getStatement :
//    'GET' ( ID ( ',' ID )* )?  'FROM' dationName  'BY' formatPosition ( ',' formatPosition )* ';'
//    ;

getStatement:
    'GET' ioDataList? 'FROM' dationName
    ( 'BY' listOfFormatPositions )? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// PutStatement ::=
//   PUT [ { Expression | ArraySlice } [ , { Expression | ArraySlice } ] ... ] TO Name§Dation [ BY FormatPosition [ , FormatPosition ] ... ] ;
////////////////////////////////////////////////////////////////////////////////
putStatement:
    'PUT' ioDataList? 'TO' dationName
    ( 'BY' listOfFormatPositions )? ';'
    ;


////////////////////////////////////////////////////////////////////////////////
// WriteStatement ::=
//  WRITE [ { Expression | ArraySlice } [ , { Expression | ArraySlice } ] ... ]
//     TO Name§Dation [ BY Position [ , Position ] ... ] ;
//
// ArraySlice ::=
//   Name§Field ( [ Index , ] ... Index : Index)
//
// Index ::=
//   Expression§WithIntegerAsValue
////////////////////////////////////////////////////////////////////////////////

writeStatement:
    'WRITE' ioDataList? 'TO' dationName
    ( 'BY' listOfFormatPositions )? ';'
    ;
    
//    'WRITE' ( expression ( ',' expression )* )? 
//	'TO' dationName
//	(  'BY' position ( ',' position )* )? ';'
//    ;

////////////////////////////////////////////////////////////////////////////////
// ReadStatement ::=
//   READ [ { Name§Variable | ArraySlice } [ , { Name§Variable | ArraySlice } ] ... ] FROM Name§Dation [ BY Position [ , Position ] ... ] ;
////////////////////////////////////////////////////////////////////////////////

readStatement :
    'READ' ioDataList? 'FROM' dationName
    ( 'BY' listOfFormatPositions )? ';'
    ;
//    'READ' ID  (','  ID  )* 
//	'FROM' dationName  
//	(  'BY' position ( ',' position )* )? ';'
//  ;

////////////////////////////////////////////////////////////////////////////////
// TakeStatement ::=
//   TAKE [ Name§Variable ] FROM Name§Dation
//     [ BY RST-S-CTRL-Format [ , RST-S-CTRL-Format ] ... ] ;
////////////////////////////////////////////////////////////////////////////////

takeStatement:
    //'TAKE' ID?  takeFrom ( 'BY'  take_send_rst_s_ctrl_format ( ',' take_send_rst_s_ctrl_format)* )? ';'
//    'TAKE' ID?  'FROM' dationName( 'BY'  take_send_rst )? ';'
//    ;
    'TAKE' ioDataList? 'FROM' dationName
    ( 'BY' listOfFormatPositions )? ';'
    ;


////////////////////////////////////////////////////////////////////////////////
// SendStatement ::=
//   SEND [ Expression ] TO Name§Dation
//    [ BY RST-S-CTRL-Format [ , RST-S-CTRL-Format ] ... ] ;
////////////////////////////////////////////////////////////////////////////////

sendStatement :
    //'SEND' expression? sendTo ( 'BY'  take_send_rst_s_ctrl_format ( ',' take_send_rst_s_ctrl_format)* )? ';'
    //'SEND' expression? 'TO' dationName ( 'BY'  take_send_rst )? ';'
    //;
    'SEND' ioDataList? 'TO' dationName
    ( 'BY' listOfFormatPositions )? ';'
    ;    
    
// obsolete 2020-02-24 (rm)
//take_send_rst :
//      'RST' '(' ID ')' 
//	;

// note 'name' is part of rule 'expression'
ioListElement:
	(expression | arraySlice)
	;
	
ioDataList:
	ioListElement (',' ioListElement) *
	;	
	
	
listOfFormatPositions:
	formatPosition ( ',' formatPosition )* 
	;

dationName: 
	name
	;

////////////////////////////////////////////////////////////////////////////////
// FormatPosition ::=
//   [ Factor ] { Format | Position } |
//   Factor ( FormatPosition [ , FormatPosition ] ... )
////////////////////////////////////////////////////////////////////////////////

formatPosition:
      factor? format                                 # factorFormat
    | factor? position                               # factorPosition
    | factor '(' listOfFormatPositions  ')'			 # factorFormatPosition
    ;

////////////////////////////////////////////////////////////////////////////////
// Factor ::=
//   ( Expression§IntegerGreaterZero ) | IntegerWithoutPrecision§GreaterZero
////////////////////////////////////////////////////////////////////////////////

factor:
    '(' expression ')' | integerWithoutPrecision
    ;

////////////////////////////////////////////////////////////////////////////////
// Format ::=
//   FixedFormat | FloatFormat | CharacterStringFormat | BitFormat | TimeFormat | DurationFormat | ListFormat | R-Format | RST (Name)
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
// Format ::=
//   { F | E} ( Expression [ , Expression [ , Expression ] ] ) | { B | B1 | B2 | B3 | B4 | A } [ ( Expression ) ]
//   | { T | D } ( Expression [ , Expression ] )
//   | LIST
//   | R ( Identifier§Format )
//   | S ( Name§LengthVariable-FIXED )
////////////////////////////////////////////////////////////////////////////////

format:
      fixedFormat
    | floatFormat
    | bitFormat
    | timeFormat
    | durationFormat
    | listFormat
    | characterStringFormat
    ;

////////////////////////////////////////////////////////////////////////////////
// Position ::=
//   RST ( Name§ErrorVariable-FIXED )
//   | { X | SKIP | PAGE } [ ( Expression ) ]
//   | { POS | ADV } ( Expression [ , Expression [ , Expression ] ] )
//   | { COL | LINE } ( Expression )
//   | SOP ( Name [ , Name [ , Name ] ] /∗ PositionVariables-FIXED ∗/ )
////////////////////////////////////////////////////////////////////////////////
absolutePosition:
       positionCOL
     | positionLINE
     | positionPOS
     | positionSOP
     ;

positionCOL:
       'COL' '(' expression ')'
	;

positionLINE:
     'LINE' '(' expression ')'                           
     ;

positionPOS:
     'POS' '(' ( (  expression ',' )? expression ',' )?
       expression ')'
     ;

positionSOP:
     'SOP' '(' ( ( ID ',' )? ID ',' )? ID ')'     
     ;


////////////////////////////////////////////////////////////////////////////////
// Position ::=
//   RST ( Name§ErrorVariable-FIXED )
//   | { X | SKIP | PAGE } [ ( Expression ) ]
//   | { POS | ADV } ( Expression [ , Expression [ , Expression ] ] )
//   | { COL | LINE } ( Expression )
//   | SOP ( Name [ , Name [ , Name ] ] /∗ PositionVariables-FIXED ∗/ )
////////////////////////////////////////////////////////////////////////////////
position:
      openClosePositionRST
    | relativePosition
    | absolutePosition
    ;

////////////////////////////////////////////////////////////////////////////////
// RelativePosition ::=
//   { X | SKIP | PAGE } [ (Expression) ] |
//   ADV ( [ [ Expression , ] Expression , ] Expression )
////////////////////////////////////////////////////////////////////////////////
relativePosition:
    | positionX
    | positionSKIP
    | positionPAGE
    | positionADV
    | positionEOF
    ;


openClosePositionRST :
	'RST' '(' name ')' 
	;

positionPAGE:
	'PAGE' ( '(' expression ')' )?
	;

positionSKIP:
	'SKIP' ( '(' expression ')' )?
	;

positionX:
	'X' ( '(' expression ')' )?
	;

positionADV:
    | 'ADV' '(' ( ( expression ',' )? expression ',' )?
      expression ')'                                     
    ;

positionEOF:
	'EOF'
	;
	
////////////////////////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////////////////////////
// FixedFormat ::=
//   F (FieldWidth [ , DecimalPositions [ , ScaleFactor ] ] )
////////////////////////////////////////////////////////////////////////////////

// no scaleFactor in OpenPEARL
//fixedFormat :
//    'F' '(' fieldWidth ( ',' decimalPositions ( ',' scaleFactor )? )? ')'
//    ;

fixedFormat:
    'F' '(' fieldWidth ( ',' decimalPositions )? ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// FieldWidth ::= Expression§WithPositiveIntegerAsValue
////////////////////////////////////////////////////////////////////////////////

fieldWidth:
    expression
    ;

////////////////////////////////////////////////////////////////////////////////
// FloatFormat ::=
// E[3] (FieldWidth [ , DecimalPositions [ , Significance ] ] )
//
// Significance ::= Expression§WithIntegerAsValue
////////////////////////////////////////////////////////////////////////////////

significance:
	expression
	;

floatFormat:
      'E'  '(' fieldWidth ( ',' decimalPositions ( ',' significance )? )? ')'   # floatFormatE
    | 'E3' '(' fieldWidth ( ',' decimalPositions ( ',' significance )? )? ')'   # floatFormatE3
    ;

////////////////////////////////////////////////////////////////////////////////
// BitFormat ::=
//      { B | B1 | B2 | B3 | B4 } [ ( Expression§NumberCharacters) ]
////////////////////////////////////////////////////////////////////////////////

bitFormat:
      ( 'B' | 'B1')  ( '(' numberOfCharacters ')' )?   # bitFormat1
    | 'B2' ( '(' numberOfCharacters ')' )?             # bitFormat2
    | 'B3' ( '(' numberOfCharacters ')' )?             # bitFormat3
    | 'B4' ( '(' numberOfCharacters ')' )?             # bitFormat4
    ;

////////////////////////////////////////////////////////////////////////////////

numberOfCharacters :
    expression
    ;

////////////////////////////////////////////////////////////////////////////////
// TimeFormat ::=
//      T ( FieldWidth [ , DecimalPositions ] )
////////////////////////////////////////////////////////////////////////////////

timeFormat:
    'T' '(' fieldWidth ( ',' decimalPositions )? ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// DurationFormat ::=
//      D ( FieldWidth [ , DecimalPositions ] )
////////////////////////////////////////////////////////////////////////////////


durationFormat:
    'D' '(' fieldWidth ( ',' decimalPositions )? ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// DecimalPositions ::= Expression§WithNonNegativeIntegerAsValue
////////////////////////////////////////////////////////////////////////////////

decimalPositions:
    expression
    ;

////////////////////////////////////////////////////////////////////////////////
// ScaleFactor ::= Expression§WithIntegerAsValue
////////////////////////////////////////////////////////////////////////////////

scaleFactor:
    expression
    ;

////////////////////////////////////////////////////////////////////////////////
// CharacterStringFormat ::=
//   A [ (Expression§NumberCharacters) ] | S (Name§NumberCharactersVariableFixed)
////////////////////////////////////////////////////////////////////////////////

characterStringFormat :
      'A' ( '(' fieldWidth ')' )?       # characterStringFormatA
    | 'S' '(' ID ')'                    # characterStringFormatS
    ;

////////////////////////////////////////////////////////////////////////////////

channel: ID;


index_array :
	expression
	;

arraySlice :
    name '(' startIndex ':' endIndex ')'
    ;

startIndex:
    listOfExpression
    ;

endIndex:
    expression; 

////////////////////////////////////////////////////////////////////////////////
// Position ::=
//   AbsolutePosition | RelativePosition | RST (Name§ErrorVariable-FIXED)
////////////////////////////////////////////////////////////////////////////////




////////////////////////////////////////////////////////////////////////////////

type
    : simple_type
    | type_realtime_object
    | typeTime
    | typeReference
    ;

////////////////////////////////////////////////////////////////////////////////
//  SimpleType ::=
//      TypeInteger | TypeFloatingPointNumber | TypeBitString | TypeCharacterString | TypeTime | TypeDuration
////////////////////////////////////////////////////////////////////////////////

simple_type
    : type_fixed
    | type_float
    | type_char
    | type_bit
    ;

////////////////////////////////////////////////////////////////////////////////

typeTime
    : type_clock
    | type_duration
    ;

////////////////////////////////////////////////////////////////////////////////

type_char : ( 'CHARACTER' | 'CHAR' ) ( '(' IntegerConstant ')' )?;

////////////////////////////////////////////////////////////////////////////////

type_fixed: 'FIXED' ( '(' IntegerConstant ')' )?;

////////////////////////////////////////////////////////////////////////////////

type_float: 'FLOAT' ( '(' IntegerConstant ')' )?;

////////////////////////////////////////////////////////////////////////////////

type_duration: ( 'DURATION' | 'DUR' );

////////////////////////////////////////////////////////////////////////////////

type_clock: 'CLOCK';

////////////////////////////////////////////////////////////////////////////////
type_bit: 'BIT' ( '(' IntegerConstant ')' )?;

////////////////////////////////////////////////////////////////////////////////

type_realtime_object
    : 'SEMA'
    | 'BOLT'
    | 'IRPT'
    | 'INTERRUPT'
    | 'SIGNAL'
    ;

////////////////////////////////////////////////////////////////////////////////
// SystemInterruptDeclaration ::=
//   Identifier§InterruptSystemName [ ( ListOfConstants ) ] [ Association ] ;
////////////////////////////////////////////////////////////////////////////////

//systemInterruptDeclaration
//    : ID [ ( ListOfConstants ) ] [ Association ] ';'
//    ;

////////////////////////////////////////////////////////////////////////////////
// InterruptSpecification ::=
//  { SPECIFY | SPC } OneIdentifierOrList { INTERRUPT | IRPT }
//  [ GlobalAttribute ];
////////////////////////////////////////////////////////////////////////////////

interruptSpecification
    : ( 'SPECIFY' | 'SPC' ) ID (',' ID)* ( 'INTERRUPT' | 'IRPT' ) globalAttribute? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// ListOfConstants ::=
//   ConstantParameter [, ConstantParameter ]...
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// ConstantParameter ::=
//   IntegerWithoutPrecision | BitStringConstant | CharacterStringConstant
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// Association ::=
//  --- { Identifier§UsernameOfConnectionProvider
//  | Identifier§ConnectionProviderSystemName [ ( ListOfConstants) ] }
//  [ - - - { Identifier§UsernameOfConnectionProvider
//  | Identifier§ConnectionProviderSystemName [ ( ListOfConstants) ] } ]...;
////////////////////////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////////////////////////
//  DationSpecification ::=
//    { SPECIFY | SPC } IdentifierDenotation TypeDation [ GlobalAttribute ] ;
////////////////////////////////////////////////////////////////////////////////

dationSpecification
//    : ( 'SPECIFY' | 'SPC' ) identifierDenotation specifyTypeDation globalAttribute? ';'
    : ( 'SPECIFY' | 'SPC' ) identifierDenotation typeDation globalAttribute? ';'
    ;

////////////////////////////////////////////////////////////////////////////////


/*

change in grammar 2019-10-12
  dationSpecification and dationDeclaration 
  - uses typeDation and specifyTypeDation, resp.
  - typeDation and specifyTapeDation were identical
  - classAttribute had an optional element 'SYSTEM' which is only possible 
    in dationSecification
  -> remove specifyTypeDation and use typeDation even in dationSpecification
  -> leave the classAttribute unchanged and filter this in the semantic
     analysis

specifyTypeDation
    : 'DATION' sourceSinkAttribute classAttribute
    ;
*/

////////////////////////////////////////////////////////////////////////////////
// DationDeclaration ::=
//    {DECLARE | DCL} IdentifierDenotation TypeDation [GlobalAttribute] CREATED (Name§SystemDefDation);
////////////////////////////////////////////////////////////////////////////////

dationDeclaration
    : ( 'DECLARE' | 'DCL' ) identifierDenotation typeDation  globalAttribute? 'CREATED' '(' ID  ')' ';'
    ;

////////////////////////////////////////////////////////////////////////////////
//  TypeDation ::=
//    DATION SourceSinkAttribute ClassAttribute [ Structure ] [ AccessAttribute ]
////////////////////////////////////////////////////////////////////////////////

typeDation:
    'DATION' sourceSinkAttribute classAttribute typology? accessAttribute?
    ;

////////////////////////////////////////////////////////////////////////////////
//  SourceSinkAttribute ::=
//    IN | OUT | INOUT
////////////////////////////////////////////////////////////////////////////////

sourceSinkAttribute
    : 'IN'                  # sourceSinkAttributeIN
    | 'OUT'                 # sourceSinkAttributeOUT
    | 'INOUT'               # sourceSinkAttributeINOUT
    ;

////////////////////////////////////////////////////////////////////////////////

systemDation
    : 'SYSTEM'
    ;

////////////////////////////////////////////////////////////////////////////////
//  ClassAttribute ::=
//    [ SYSTEM ]                       /* system dation */
//    ALPHIC                       |   /* PUT/GET */
//    BASIC TypeOfTransmissionData |   /* TAKE/SEND */
//    TypeOfTransmissionData           /* READ/WRITE */
////////////////////////////////////////////////////////////////////////////////

classAttribute
    : systemDation? ( alphicDation | basicDation typeOfTransmissionData| typeOfTransmissionData)
    ;

////////////////////////////////////////////////////////////////////////////////

alphicDation
    : 'ALPHIC'
    ;
////////////////////////////////////////////////////////////////////////////////

basicDation
    : 'BASIC'
    ;

////////////////////////////////////////////////////////////////////////////////
//  TypeOfTransmissionData ::=
//    ALL | SimpleType | CompoundType
////////////////////////////////////////////////////////////////////////////////

typeOfTransmissionData
    : 'ALL'                     # typeOfTransmissionDataALL
    | simpleType                # typeOfTransmissionDataSimpleType
//    | compoundType              # typeOfTransmissionDataCompoundType
    | typeStructure             # typeOfTransmissionDataCompoundType
    ;

////////////////////////////////////////////////////////////////////////////////
// AccessAttribute ::=
//  { DIRECT | FORWARD | FORBACK } [ NOCYCL | CYCLIC ] [ STREAM | NOSTREAM ]
////////////////////////////////////////////////////////////////////////////////

accessAttribute
    : ( 'DIRECT' | 'FORWARD' | 'FORBACK' ) ( 'NOCYCL' | 'CYCLIC' )? ( 'STREAM' | 'NOSTREAM' )?
    ;

////////////////////////////////////////////////////////////////////////////////
// Typology ::=
//      DIM( {⇤ |pi} [,pi[,pi]] ) [TFU[MAX]]
////////////////////////////////////////////////////////////////////////////////
/* grammar changed for easier parsing
 'TFU' ( 'MAX' )?  --> rule rule
*/

typology :
    'DIM'
    '('
        dimension1 ( ( ',' dimension2 ) ( ',' dimension3 )? )?
    ')' ( tfu )?
    ;

dimension1:
     '*'                      # dimension1Star
    | constantFixedExpression          # dimension1Integer
    ;

dimension2:
    constantFixedExpression          # dimension2Integer
    ;

dimension3:
    constantFixedExpression          # dimension3Integer
    ;

tfu:
   'TFU' ( tfuMax )?
   ;

tfuMax: 
   'MAX'
   ;

////////////////////////////////////////////////////////////////////////////////
// DimensionAttribute ::=
//  (BoundaryDenotation§FirstDimension [ , BoundaryDenotation§FurtherDimension ] ...)
////////////////////////////////////////////////////////////////////////////////

dimensionAttribute:
    '(' boundaryDenotation ( ',' boundaryDenotation )* ')'
    ;

boundaryDenotation:
    constantFixedExpression ( ':' constantFixedExpression )?
    ;

////////////////////////////////////////////////////////////////////////////////

indices:
    '(' expression ( ',' expression )* ')'
    ;

/* obsolete 2020-02-25 (rm)

////////////////////////////////////////////////////////////////////////////////
//  CompoundType ::=
//    IO-Structure | Identifier§ForNewTypeFromSimpleTypes
////////////////////////////////////////////////////////////////////////////////

compoundType:
    ioStructure | ID
    ;

////////////////////////////////////////////////////////////////////////////////
//  IO-Structure ::=
//   STRUCT [ IO-StructureComponent [ , IO-StructureComponent ] ... ]
////////////////////////////////////////////////////////////////////////////////

ioStructure
    : 'STRUCT' ( ioStructureComponent ( ',' ioStructureComponent )* )?
    ;

////////////////////////////////////////////////////////////////////////////////
//   IO-StructureComponent := IdentifierDenotation
//     { SimpleType | IO-Structure | Identifier§ForNewTypeFromSimpleTypes }
////////////////////////////////////////////////////////////////////////////////

ioStructureComponent
    : identifierDenotation ( simpleType | ioStructure | ID )+
    ;
*/


////////////////////////////////////////////////////////////////////////////////
// Ranks of the operators defined in PEARL
//
// rank      dyadic operators         evaluation order
// -------------------------------------------------------------
//  1        **, FIT, LWB, UPB        from the right to the left
//  2        *, /, ><, //, REM        from the left to the right
//  3        +, -, <>, SHIFT          from the left to the right
//  4        <, >, <=, >=             from the left to the right
//  5        ==, /=, IS, ISNT         from the left to the right
//  6        AND                      from the left to the right
//  7        OR, EXOR                 from the left to the right
//
// All monadic standard operators have rank 1.
////////////////////////////////////////////////////////////////////////////////

expression:
    primaryExpression                                     # baseExpression
    | op='ATAN' expression                                  # atanExpression
    | op='COS' expression                                   # cosExpression
    | op='EXP' expression                                   # expExpression
    | op='LN' expression                                    # lnExpression
    | op='SIN' expression                                   # sinExpression
    | op='SQRT' expression                                  # sqrtExpression
    | op='TAN' expression                                   # tanExpression
    | op='TANH' expression                                  # tanhExpression
    | op='ABS' expression                                   # absExpression
    | op='SIGN' expression                                  # signExpression
    | op='SIZEOF' ( expression | simpleType )               # sizeofExpression
    | op='NOT' expression                                   # notExpression
    | op='TOBIT' expression                                 # TOBITExpression
    | op='TOFIXED' expression                               # TOFIXEDExpression
    | op='TOFLOAT' expression                               # TOFLOATExpression
    | op='TOCHAR' expression                                # TOCHARExpression
    | op='ENTIER' expression                                # entierExpression
    | op='ROUND' expression                                 # roundExpression
    | op='CONT' expression                                  # CONTExpression
    | op='LWB' expression                                   # lwbMonadicExpression
    | op='UPB' expression                                   # upbMonadicExpression
    | op='NOW'                                              # nowFunction
    | op='DATE'                                             # dateFunction
    | op='TASK' ( '(' expression ')' )?                     # taskFunction
    | op='PRIO' ( '(' expression ')' )?                     # prioFunction
    | <assoc=right> expression op='**'  expression          # exponentiationExpression
    | <assoc=right> expression op='FIT' expression          # fitExpression
    | <assoc=right> expression op='LWB' expression          # lwbDyadicExpression
    | <assoc=right> expression op='UPB' expression          # upbDyadicExpression
    | op=('*'|'/') expression                               # unaryMultiplicativeExpression
    | op='-' expression                                     # unarySubtractiveExpression
    | op='+' expression                                     # unaryAdditiveExpression
    | expression op='*' expression                          # multiplicativeExpression
    | expression op='/' expression                          # divideExpression
    | expression op='//' expression                         # divideIntegerExpression
    | expression op='REM' expression                        # remainderExpression
    | expression op=('CAT'|'><') expression                 # catExpression
    | expression op='+' expression                          # additiveExpression
    | expression op='-' expression                          # subtractiveExpression
    | expression op=('CSHIFT'|'<>') expression              # cshiftExpression
    | expression op='SHIFT' expression                      # shiftExpression
    | expression op=( '<'|'LT') expression                  # ltRelationalExpression
    | expression op=( '<='|'LE') expression                 # leRelationalExpression
    | expression op=( '>'|'GT') expression                  # gtRelationalExpression
    | expression op=( '>='|'GE') expression                 # geRelationalExpression
    | expression op=( '=='|'EQ') expression                 # eqRelationalExpression
    | expression op=( '/='|'NE') expression                 # neRelationalExpression
    | expression op='IS' expression                      # isRelationalExpression
    | expression op='ISNT' expression               # isntRelationalExpression
    | expression op='AND' expression                        # AndExpression
    | expression op='OR' expression                         # OrExpression
    | expression op='EXOR' expression                       # ExorExpression
    | expression unaryLiteralExpression                     # unarySignedLiteralExpression
    ;

////////////////////////////////////////////////////////////////////////////////

unaryLiteralExpression
    : (numericLiteralPositive | numericLiteralNegative) ( op=('*'|'/') unaryExpression)?
    ;

////////////////////////////////////////////////////////////////////////////////

unaryExpression
    : op=('+'|'-')? primaryExpression
    ;

////////////////////////////////////////////////////////////////////////////////


// obsolete? (2020-02-26 (rm)
//expressionList:
//    expression (',' expression)*
//    ;

////////////////////////////////////////////////////////////////////////////////

numericLiteral:
    numericLiteralUnsigned | numericLiteralPositive | numericLiteralNegative
    ;

////////////////////////////////////////////////////////////////////////////////

numericLiteralUnsigned:
    IntegerConstant
    ;

////////////////////////////////////////////////////////////////////////////////

numericLiteralPositive:
    IntegerConstant
    ;

////////////////////////////////////////////////////////////////////////////////

numericLiteralNegative:
    '-' IntegerConstant
    ;

////////////////////////////////////////////////////////////////////////////////
// Name ::=
//    Identifier [ ( Index [ , Index ] ... ) ] [ . Name ]
////////////////////////////////////////////////////////////////////////////////

name:
    ID ( '(' listOfExpression ')' )? ( '.' name )?
    ;

listOfExpression:
    expression ( ',' expression )*
    ;

//name
//    : ID ( '(' index ( ',' index )? ')' )? ( '.' name )?
//    ;

////////////////////////////////////////////////////////////////////////////////
// Index ::=
//  Expression§WithIntegerAsValue
////////////////////////////////////////////////////////////////////////////////

index:
    expression
    ;

////////////////////////////////////////////////////////////////////////////////

primaryExpression:
    '(' expression ')'
    | name
    | literal
    | semaTry
    | stringSelection
    ;

////////////////////////////////////////////////////////////////////////////////
// ConstantExpression ::=
//   { + | - } FloatingPointNumber
//   | { + | - } DurationConstant
//   | ConstantFIXEDExpression
////////////////////////////////////////////////////////////////////////////////

constantExpression:
    floatingPointConstant
    | sign? durationConstant
    | constantFixedExpression
    ;

////////////////////////////////////////////////////////////////////////////////
// ConstantFIXEDExpression ::=
//   Term [ { + | - } Term ] ...
////////////////////////////////////////////////////////////////////////////////

constantFixedExpression:
    constantFixedExpressionTerm ( additiveConstantFixedExpressionTerm | subtractiveConstantFixedExpressionTerm) *
    ;

////////////////////////////////////////////////////////////////////////////////

additiveConstantFixedExpressionTerm
    : op='+' constantFixedExpressionTerm
    ;

////////////////////////////////////////////////////////////////////////////////

subtractiveConstantFixedExpressionTerm
    : op='-' constantFixedExpressionTerm
    ;

////////////////////////////////////////////////////////////////////////////////
// Term ::=
//   Factor [ {∗ | // | REM } Factor ] ...
////////////////////////////////////////////////////////////////////////////////

constantFixedExpressionTerm
    : constantFixedExpressionFactor ( multiplicationConstantFixedExpressionTerm | divisionConstantFixedExpressionTerm | remainderConstantFixedExpressionTerm)*
    ;

////////////////////////////////////////////////////////////////////////////////

multiplicationConstantFixedExpressionTerm
    : op='*' constantFixedExpressionFactor
    ;

////////////////////////////////////////////////////////////////////////////////

divisionConstantFixedExpressionTerm
    : op='//' constantFixedExpressionFactor
    ;

////////////////////////////////////////////////////////////////////////////////

remainderConstantFixedExpressionTerm
    : op='REM' constantFixedExpressionFactor
    ;

////////////////////////////////////////////////////////////////////////////////
// Factor ::=
//   [+ | -] {  Integer
//             | ( ConstantFIXEDExpression )
//             | TOFIXED { CharacterStringConstant§OfLength1 | BitStringConstant }
//             | Identifier§NamedFIXEDConstant
//           }
// [ FIT ConstantFIXEDExpression ]
////////////////////////////////////////////////////////////////////////////////

sign
    : '+'                       #signPlus
    | '-'                       #signMinus
    ;

constantFixedExpressionFactor
    : sign? (   fixedConstant
              | '(' constantFixedExpression ')'
              | ID )
      constantFixedExpressionFit?
    ;

////////////////////////////////////////////////////////////////////////////////

constantFixedExpressionFit
    : <assoc=right>  'FIT' constantFixedExpression
    ;

////////////////////////////////////////////////////////////////////////////////
// ConvertStatement ::=
//   ConvertToStatement | ConvertFromStatement
////////////////////////////////////////////////////////////////////////////////

convertStatement
    : convertToStatement
    | convertFromStatement
    ;

////////////////////////////////////////////////////////////////////////////////
// ConvertToStatement ::=
//   CONVERT Expression [ , Expression ] ... TO Name§CharacterStringVariable
//   [ BY FormatOrPositionConvert [ , FormatOrPositionConvert ] ... ] ;
////////////////////////////////////////////////////////////////////////////////

convertToStatement:
    'CONVERT' ioDataList? 'TO' name ( 'BY' listOfFormatPositions )? ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// ConvertFromStatement ::=
//   CONVERT Name§Variable [ , Name§Variable ] ... FROM Expression§CharacterString
//   [ BY FormatOrPositionConvert [ , FormatOrPositionConvert ] ... ] ;
////////////////////////////////////////////////////////////////////////////////
convertFromStatement:
	//'CONVERT' ioDataList? 'FROM' name ( 'BY' listOfFormatPositions )? ';'
	'CONVERT' ioDataList? 'FROM' expression ( 'BY' listOfFormatPositions )? ';'
	;


////////////////////////////////////////////////////////////////////////////////
// ListFormat ::=
//   LIST
////////////////////////////////////////////////////////////////////////////////

listFormat
    : 'LIST'
    ;

////////////////////////////////////////////////////////////////////////////////
// RFormat ::=
//   R ( Identifier§Format )
////////////////////////////////////////////////////////////////////////////////

rFormat
    : 'R' '(' ID ')'
    ;


////////////////////////////////////////////////////////////////////////////////

stringSlice
	: bitSlice
	| charSlice
	;

////////////////////////////////////////////////////////////////////////////////

bitSlice:
	  ID '.' 'BIT' '(' constantFixedExpression ')'                                                  #case1BitSlice
	| ID '.' 'BIT' '(' constantFixedExpression ( ':' constantFixedExpression ) ')'                  #case2BitSlice
	| ID '.' 'BIT' '(' expression ':' expression '+' IntegerConstant  ')'     					    #case3BitSlice
	;

////////////////////////////////////////////////////////////////////////////////
//
charSlice:
	  ID '.' ( 'CHAR' | 'CHARACTER' ) '(' expression ')'                                            #case1CharSlice
// 2020-03-17 (rm) the lexer does not distinguish between expression and constantFixedExpression
// map case2CharSlice to case4CharSlice	
//	| ID '.' ( 'CHAR' | 'CHARACTER' ) '(' constantFixedExpression ':' constantFixedExpression ')'   #case2CharSlice
	| ID '.' ( 'CHAR' | 'CHARACTER' ) '(' expression ':' expression '+' IntegerConstant  ')'        #case3CharSlice
	| ID '.' ( 'CHAR' | 'CHARACTER' ) '(' expression ':' expression ')'                             #case4CharSlice
	;

////////////////////////////////////////////////////////////////////////////////

literal:
    fixedConstant
    | floatingPointConstant
    | BitStringLiteral
    | StringLiteral
    | timeConstant
    | durationConstant
    | referenceConstant
    ;
    
referenceConstant:
	'NIL'
	;    

////////////////////////////////////////////////////////////////////////////////

fixedConstant
    : IntegerConstant  ( '(' fixedNumberPrecision ')' )?
    ;

////////////////////////////////////////////////////////////////////////////////

IntegerConstant
	:	DecimalIntegerConstant
	;

////////////////////////////////////////////////////////////////////////////////

fixedNumberPrecision
    : IntegerConstant
    ;

////////////////////////////////////////////////////////////////////////////////

StringLiteral
	:	'\'' StringCharacters? '\''
	;

fragment
StringCharacters
	:	(StringCharacter | EscapeSequence)+ 
	;

fragment
StringCharacter
	:	~['\r\n]
	| 	'\'\''
	;

fragment
EscapeSequence
	: '\'\\' (HexEscape | ' ' | [\r\n\t])* '\\\''
	;

fragment
HexEscape
	:  B4Digit B4Digit
	;

////////////////////////////////////////////////////////////////////////////////

CppStringLiteral
    : '"' CppSCharSequence? '"'
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
CppSCharSequence
    :   CppSChar+
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
CppSChar
    :   ~["\\\r\n]
    |   CppEscapeSequence
    |   '\\\n'   // Added line
    |   '\\\r\n' // Added line
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
CppEscapeSequence
    :   CppSimpleEscapeSequence
    |   OctalEscapeSequence
    |   HexadecimalEscapeSequence
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
CppSimpleEscapeSequence
    :   '\\' ['"?abfnrtv\\]
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
OctalEscapeSequence
    :   '\\' OctalDigit
    |   '\\' OctalDigit OctalDigit
    |   '\\' OctalDigit OctalDigit OctalDigit
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
HexadecimalEscapeSequence
    :   '\\x' HexadecimalDigit+
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
OctalDigit
    :   [0-7]
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
HexadecimalDigit
    :   [0-9a-fA-F]
    ;

////////////////////////////////////////////////////////////////////////////////

BitStringLiteral
	: '\'' B1Digit+ '\'' ( 'B' | 'B1' )
    | '\'' B2Digit+ '\'' 'B2'
    | '\'' B3Digit+ '\'' 'B3'
    | '\'' B4Digit+ '\'' 'B4'
	;

////////////////////////////////////////////////////////////////////////////////

fragment
DecimalIntegerConstant
	:	DecimalNumeral
	;

////////////////////////////////////////////////////////////////////////////////

fragment
DecimalNumeral
	:	Digits+
	;

////////////////////////////////////////////////////////////////////////////////

fragment
Digits
	:	Digit+
	;

////////////////////////////////////////////////////////////////////////////////

fragment
Digit
	:	'0'
	|   NonZeroDigit
	;

////////////////////////////////////////////////////////////////////////////////

fragment
NonZeroDigit
	:	[1-9]
	;

////////////////////////////////////////////////////////////////////////////////

fragment
Letter : [a-zA-Z] ;

////////////////////////////////////////////////////////////////////////////////
//  Constant ::= Integer
//      | FloatingPointNumber | BitStringConstant
//      | TimeConstant
//      | DurationConstant
//      | NIL
////////////////////////////////////////////////////////////////////////////////

constant:
      sign? ( fixedConstant | floatingPointConstant )
    | timeConstant
    | sign? durationConstant
    | bitStringConstant
    | stringConstant
    | 'NIL'
    ;

////////////////////////////////////////////////////////////////////////////////

stringConstant:
    StringLiteral
    ;

////////////////////////////////////////////////////////////////////////////////

bitStringConstant:
    BitStringLiteral
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
B1Digit
    : '0' | '1'
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
B2Digit
    : '0' | '1' | '2' | '3'
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
B3Digit
    : '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7'
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
B4Digit
    : '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' | 'A' | 'B' | 'C' | 'D' | 'E' | 'F'
    ;

////////////////////////////////////////////////////////////////////////////////
// TimeConstant ::=
//   Digit ... : Digit ... : Digit ... [ . Digit ... ]
//     : timeHour ':' timeMinute ':' timeSeconds
// Digit+ ('.' Digit+)?
// ':'  IntegerConstant ':' FloatingPointNumberWithoutPrecisionAndExponent
////////////////////////////////////////////////////////////////////////////////

timeConstant:
    IntegerConstant ':' IntegerConstant ':' ( IntegerConstant | floatingPointConstant )
    ;

////////////////////////////////////////////////////////////////////////////////

durationConstant:
      hours  minutes? seconds?
    | minutes seconds?
    | seconds
    ;

////////////////////////////////////////////////////////////////////////////////

hours:
    IntegerConstant 'HRS'
    ;

////////////////////////////////////////////////////////////////////////////////

minutes:
    IntegerConstant 'MIN'
    ;

////////////////////////////////////////////////////////////////////////////////

seconds:
    ( IntegerConstant | floatingPointConstant ) 'SEC'
    ;

////////////////////////////////////////////////////////////////////////////////

floatingPointConstant:
    FloatingPointNumber
    ;

////////////////////////////////////////////////////////////////////////////////

FloatingPointNumber:
    ( Digit+ '.' ( Digit+)? | '.' Digit+ ) Exponent? FloatingPointNumberPrecision?
    | Digit+ Exponent FloatingPointNumberPrecision?
    ;

////////////////////////////////////////////////////////////////////////////////

fragment
FloatingPointNumberPrecision:
    '(' IntegerConstant ')'
    ;

////////////////////////////////////////////////////////////////////////////////
// Exponent ::=
//      E [ + | - ] Digit ...
////////////////////////////////////////////////////////////////////////////////

fragment
Exponent
    : 'E' ( '+' | '-' )? Digit+
    ;

////////////////////////////////////////////////////////////////////////////////

FloatingPointNumberWithoutPrecisionAndExponent
    : Digit+ '.' ( Digit+)?
    ;

////////////////////////////////////////////////////////////////////////////////

cpp_inline
    : ( '__cpp__' | '__cpp' )  '(' CppStringLiteral+ ')' ';'
    ;

////////////////////////////////////////////////////////////////////////////////
// LengthDefinition::=
//      LENGTH { { FIXED | FLOAT } (precision)
//               | { BIT | CHARACTER | CHAR } (length) };
//
// Example:
//      PROBLEM;
//          LENGTH FIXED(15);
//          LENGTH FLOAT(53);
//
//          DCL A FIXED,        /* A is of type FIXED(15) */
//              X FLOAT,        /* X is of type FLOAT(53) */
//              Y FLOAT(23);    /* Y is of type FLOAT(23) */
//
////////////////////////////////////////////////////////////////////////////////

lengthDefinition
    : 'LENGTH' lengthDefinitionType '(' IntegerConstant ')' ';'
    ;

////////////////////////////////////////////////////////////////////////////////

lengthDefinitionType
    : 'FIXED'                          #lengthDefinitionFixedType
    | 'FLOAT'                          #lengthDefinitionFloatType
    | 'BIT'                            #lengthDefinitionBitType
    | ( 'CHARACTER' | 'CHAR' )         #lengthDefinitionCharacterType
    ;
////////////////////////////////////////////////////////////////////////////////

 precision
    : IntegerConstant
    ;

////////////////////////////////////////////////////////////////////////////////

 length
     : IntegerConstant
     ;

////////////////////////////////////////////////////////////////////////////////

//
// Whitespace and comments
//

////////////////////////////////////////////////////////////////////////////////

BlockComment
    :   '/*' .*? '*/'
        -> channel(HIDDEN)
    ;

////////////////////////////////////////////////////////////////////////////////

LineComment
    :   '!' ~[\r\n]*
        -> channel(HIDDEN)
    ;

////////////////////////////////////////////////////////////////////////////////

Whitespace
    :   [ \t]+
        -> channel(HIDDEN)
    ;

////////////////////////////////////////////////////////////////////////////////

Newline
    :   (   '\r' '\n'?
        |   '\n'
        )
        -> channel(HIDDEN)
    ;

////////////////////////////////////////////////////////////////////////////////

STRING: '"' (~'"')* '"'
    ;

////////////////////////////////////////////////////////////////////////////////

//INCLUDE_TOKEN:
//    '#INCLUDE' Whitespace? STRING ';'
//    {
//        try {
//            System.out.println( "include file:" + getText());
//            ANTLRFileStream inputStream = new ANTLRFileStream("/home/marcel/repositories/openpearl-code/openpearl-code/testsuite/build/TEST.PRL");
//            org.smallpearl.compiler.OpenPearlLexer subLexer = new org.smallpearl.compiler.OpenlPearlLexer(inputStream);
//            subLexer.setFilename("/home/marcel/repositories/openpearl-code/openpearl-code/testsuite/build/TEST.PRL");
//            selector.push(sublexer);
//            selector.retry();
//            nextToken();
//        }
//        catch(Exception ex) {
//             System.out.println("Error:" + ex.getMessage());
//             System.exit(-2);
//        }
//
//        System.out.println( "include file:" + getText());
//        System.out.println( _input);
//        nextToken();
//    }
//    ;

////////////////////////////////////////////////////////////////////////////////

// handle characters which failed to match any other token
ErrorCharacter :
    .
    ;


////////////////////////////////////////////////////////////////////////////////
////////////////////////////// END OF FILE /////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

