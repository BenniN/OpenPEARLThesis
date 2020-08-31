/*
 [A "BSD license"]
 Copyright (c) 2017 Rainer Mueller
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

#ifndef IOJOB_INCLUDED
#define IOJOB_INCLUDED

/**
\file

\brief classes for the definition of the structures for IOJobs
*/

#include <stdint.h>

#include "Fixed.h"
#include "TaskCommon.h"
#include "SystemDationNBSink.h"

namespace pearlrt {
   class IODataEntry;
   class IOFormatEntry;
   class IOFormatList;

   /**
   \addtogroup io_common_helper
   @{
   */

   /**
   helper class for loops in format definitions and data lists
   */
   class LoopControl {
#define MAX_LOOP_LEVEL 11         ///< maximum depths of the loop contruct
   private:
      int loopLevel;
      int numberOfItems;
      bool workCyclic;
      int item;    ///< current item
      struct {
         int lastItem;
         int startItem;
         int loops;
         size_t sizeOfBody;
         size_t offsetIncrement;
      } loopControl[MAX_LOOP_LEVEL];
   public:
      /**
      ctor for a loop control structure

      \param numberOfItems is the number of entries of the list.
      \param cyclic  if true, evaluate the elements of the list cyclic for 
                     PUT/GET/CONVERT.
      */
      LoopControl(int numberOfItems, bool cyclic);

      /** return the next index of the list

      \returns next index of the list. Maybe this index is a StartLoop element.
              In this case the next level of loop must be entered
      */
      int next();

      /**
      enter a new level of nested loops.

      \param nbrOfItems in the loop body. The StartLoop element
                      of this loop does not count
      \param nbrOfRepetitions number of iterations of this loop. This element
             must be > 0
      \param sizeOfBody is the size of a data structure if an array of data
             structures should be transferred

      \returns the index of the next element of the list to treat
      */
      int enter(int nbrOfItems, int nbrOfRepetitions, size_t sizeOfBody=0);

      /**
      get the current offset of the current data element in a loop for 
      data structure elements
      \returns the current number of bytes to add for this loop iteration
      */
      size_t getOffset();
   };

   /**
   \brief list of data element for one io statement

   the i/o is treated by data and format lists, which contain all statement
   concerning one PEARL I/O statement like READ, WRITE, PUT, GET, TAKE or SEND.

   A job consists of a vector of data entries and a vector of format entries.
   If more data entries than format entruies exist, the treatment of
   vector of format entries is restarted.

   The required access to the PEARL data elements is realized via void
   pointers.

   For details about defining an IODataEntry and IOFormatEntry
   please refer to their documentation.
   Expressions in the datalist must be evaluated before starting the io job.
   For each expression a result variable must be introduced.

   STRUCTs must be rolled out by the compiler downto the simple types.

   Example:<br>
    x and width are FIXED(15) and y is a FLOAT(52)

    \code
    PUT 'X=',x, 'Y=', y+1 TO so BY A,F(5), X(width+2),A(4), E(13,6),SKIP;
    \endcode

    The code for the data list and result variables should look like:
    \code
    { // create local block
    Fixed<15> resultOfExpr1;

    IODataEntry dataEntries[] = {
       { // index 0
          .datatype={CHAR,2},
          .dataPtr{.outData=&CONST_CHAR_1234}
       },
       { // index 1
          .datatype={IODataEntry::FIXED,15},
          .dataPtr{.outData=&_x}
       },
       { // index 2
          .datatype={IODataEntry::CHAR,2},
          .dataPtr{.outData=&CONST_CHAR_4567}
       },
       { // index 3
          .datatype={IODataEntry::FLOAT,52},
          .dataPtr{.outData=&resultOfExpr1}
       },
    };
    // add a try-catch block for each expression in data items
    try {
       resultOfExpr1 = _y + CONST_FIXED_POS_1_31;
    } catch (Signal & s) {
       // modify the corresponding data entry to throw the signal
       dataEntries[3].dataType.baseType = IODataEntry::InduceData;
       dataEntries[3].dataType.dataWidth = s.whichRST();
    }

    IODataList dataList={sizeof(dataEntries)/sizeof(dataEntries[0]),
                         dataEntries);

    // .. setup format list
    // .. invoke put
    } // close local block
   \endcode
   */
   class IODataList {
   public:
      /** the number of entries in the complete job
      */
      size_t nbrOfEntries;

      /**
      the individial entries will be passed as an array.
      */
      IODataEntry * entry;
   };

   /**
   \brief list of format elements for one io statement

   the i/o is treated by data and format lists, which contain all statement
   concerning one PEARL I/O statement like READ,WRITE,PUT,GET,TAKE or SEND.

   A job consists of a vector of data entries and a vector of format entries.
   If more data entries than format entries exist, the treatment of
   vector of format entries is restarted.

   The required access to the PEARL data elements is realized via void
   pointers.

   For details about defining an IODataEntry and IOFormatEntry
   please refer to their documentation.

   Example:
    Assume x and width as FIXED(15), and y as FLOAT(52)

    \code
    PUT 'X=',x, 'Y=', y+1 TO so BY A,F(5), X(width+2),A(4), E(13,6),SKIP;
    \endcode

    The code for the format list and result  variables should look like:
    \code
    { // open local block
    // .. setup format list
    Fixed<31> resultOfExpr2;

    IOFormatEntry formatEntries[] = {
       { // index 0
          .format=IOFormatEntry::A,
       },
       { // index 1
          .format=IOFormatEntry::Fw,
          .fp1={.f31=CONST_FIXED_POS_5_31,
       },
       { // index 2
          .format=IOFormatEntry::X,
          .fp1={.f31=resultOfExpr2},
       },
       { // index 3
          .format=IOFormatEntry::Aw,
          .fp1={.f31=CONST_FIXED_POS_4_31,
       },
       { // index
          .format=IOFormatEntry::E2,
          .fp1={.f31=CONST_FIXED_POS_13_31,
          .fp2={.f31=CONST_FIXED_POS_6_31,
          .fp2={.f31=CONST_FIXED_POS_7_31,
       },
       { // index 5
          .format=IOFormatEntry::SKIP,
          .fp1={.f31=CONST_FIXD_POS_1_31},
       },
    };

    // add a try-catch block for each expression in format items
    try {
      resultOfExpr2 = width+(pearlrt::Fixed<31>)(2);
    } catch (Signal & s) {
       // modify format the corresponding  entry to throw the signal
       formatEntries[2].format = IOFormatEntry::InduceFormat;
       formatEntries[2].intValue = s.whichRST();
    }
    // .. invoke put
    } // close local block
    \endcode

   */
   class IOFormatList {
   protected:
      /**
      return the next format item in the format list

      The method treats all repetitions and cyclic behavior
      as required in PUT/GET statements

      \param formatItem is the index of the last treated item
      \param loopStatus contains the nested loop control data
      \returns the next format item to treat
      */
//      size_t getNextElement(size_t formatItem,
//                            FormatLoop * loopStatus);

   public:
      /** the number of entries in the complete job
      */
      size_t nbrOfEntries;

      /**
      the individial entries will be passed as an array.
      */
      IOFormatEntry * entry;
   };

   /**
   \brief specification of a data element inside of an io job.

   Example:
   reading a value into the FLOAT(52) variable.
   Note that the variable one with type size_t has the value 1.
   \code
   {
      .dataType={IODataEntry::FLOAT,52},
      .dataPtr.inData = _x,
      .numberOfElements = one,
   }
   \endcode

   */
   class IODataEntry {
   public:
      /**
      the identifiers of the base type of an IODataEntry

      The type information is completed with the dataWidth element
      for the templated simple data types.

      */
      enum IODataType  {
         CHAR, ///< CHAR types
         FLOAT, ///< FLOAT types
         FIXED, ///< FIXED types
         BIT,   ///< BIT types
         /**
         a char slice must be treated differently

         dataPtr is the pointer to the first character<br>
         param1.nbrOfElements is the number of chars to transmit<br>
         */
         CHARSLICE,
         CLOCK, ///< CLOCK types
         DURATION, ///< DURATION types
         /** a virtual type, which enabled loops on parts
             of the data list

             dataType.dataWidth contains the number of data list element
                for the loop<br>
             dataPtr.offsetIncrement contains the number of bytes to increment
                      the base address in each loop <br>
             param1.numberOfElements contains the number of iterations
         */
         LoopStart,

         /** a virtual type, which is used to inject an
                  PEARL signal, if the evaluation of an expression
                  for data transfers was not possible.

                  fp1.intValue must contain the signal number
              */
         InduceData,

      };

      /** the number of entries in the complete job
      */
      /** the type of the data element
      */
      struct {
         /** the base type is one of the enum values CHAR, BIT,.., CLOCK*/
         IODataType baseType: 8;
         /** this is the number of bits for the types which
         exists in several widths, otherwise may be unitialized<br>
         for loops in data, this element is the number of data items of
         the loop body */
         unsigned int dataWidth: 24;
      } dataType;

      /**
      Pointer to the data which should be transferred from/to the peripheral.

      Depending on the direction, ether the component inData for input
      or outData schould be used.
      Input data need a non constant pointer. For output data, the
      const void pointer should be used to be compatible with real constants
      */
      union {
         void * inData;		///< non constant pointer for input
         const void * outData;     ///< constant pointer for output
         /** number of bytes to increment the pointer to the data in each
             loop iteration
         */
         size_t   offsetIncrement; 
      } dataPtr;                   ///< the data pointer component

      /**
      number of elements

      For arrays  in PUT and GET, we need the number of data elements
      of the array.
      For scalar values this item must be 1.

      For data loops, this value is the number of repetitions

      For READ and WRITE, this element contains the number of bytes
      to transfer.
      */
      union { 
         /** number of data elements */
         int32_t numberOfElements;
      } param1;  ///< start of array or slice

      union {
          /** the end value for loops */
          size_t * end;
          struct {
             Fixed<15> lwb;
             Fixed<15> upb;
          } charSliceLimits; // type CHARSLICE
      } param2;  ///< end of array or slice  

     /**
     deliver the size of one element of the given IODataType
     */
     size_t getSize();

     /**
     check limits for CHARSLIZEs and deliver the start offsettart

     \throws  CharacterIndexOutOfRangeSignal, if [lwb,upb] violates
            the range of the base CHAR variable
     \returns 0, if the DataEntry is no CHARSLIZE<br>
              lwb-field for CHARSLICE
     */
    size_t getStartOffset();
   };

   /**
   \brief specification of a format element inside of an io job.

   Example:
   reading a value into the FLOAT(52) variable x with the
   format F(13,6)
   \code
   {
      .format=IOFormatEntry::F,
      .fp1.outParam=&CONST_FIXED31_13,
      .fp2.outParam=&CONST_FIXED31_6
   }
   \endcode
   */
   class IOFormatEntry {
   public:
      /**
      the format identifiers of an IOFormatEntry
      */
      enum IOFormat  {
         /** the A-format without width specification */
         A,
         /** the A-format with width specification<br>
         fp1.f31 must refer the field width */
         Aw,

         /** F-format for FIXED and FLOAT types with
                       width and precision<br>
         fp1.f31 must refer the field width <br>
         fp2.f31 must refer the number of decimals
         */
         F,

         /** E or E2-format for FLOAT types with
                       width and precision
         fp1.f31 must refer the field width <br>
         fp2.f31 must refer the number of decimals
         */

         E2,
         /** E or E2-format for FLOAT types with
                       width, precision and significance
         fp1.f31 must refer the field width <br>
         fp2.f31 must refer the number of decimals<br>
         fp3.f31 must refer the number of significant digits
         */

         E3,
         /** E3-format for FLOAT types with
                       width, precision and significance
         fp1.f31 must refer the field width <br>
         fp2.f31 must refer the number of decimals<br>
         fp3.f31 must refer the number of significant digits
         */

         /** the B or B1 format without width specification */
         B1,
         /** the B2 format without width specification */
         B2,
         /** the B3 format without width specification */
         B3,
         /** the B4 format without width specification */
         B4,
         /** the B or B1 format width width specification <br>
         fp1.f31 must refer the field width */
         B1w,
         /** the B2 format width width specification<br>
         fp1.f31 must refer the field width  */
         B2w,
         /** the B3 format width width specification<br>
         fp1.f31 must refer the field width  */
         B3w,
         /** the B4 format width width specification<br>
         fp1.f31 must refer the field width  */
         B4w,

         /** T format with width and decimals<br>
         fp1.f31 must refer the field width<br>
         fp2.f31 must refer the number of decimals  */
         T,

         /** D format with width and decimals<br>
         fp1.f31 must refer the field width <br>
         fp2.f31 must refer the number of decimals */
         D,

         /** LIST format will use the default format
             depending on the data type */
         LIST,


         /** a virtual type for the repetition of format elements
             like in (3)((5)(F(4), X(3)),SKIP)
             which represents 3 lines with 5 fixed values and 3 spaces

             fp1.intValue must contain the number of enveloped
                  format elements (this entry is not counted)<br>
             fp2.f31 must contain the number of repetitions
         */
         LoopStart,

         /**
         for treatment of the io-entries we must differ
         between data entries and formating entries.
         Data entries will be treated inside the IOFormat class
         Format entries will be treated inside the
         UserDationNB class.
         RST entries must be executed before the first data entry.
         Thus is in the group 'positioning' <br>
         The element 'IsPositioning' is never used by the applications
         */
         IsPositioning,

         /**
         the RST format entry

         fp1.fxxPtr.voidPtr must refer the rst variable<br>
         fp1.fxxPtr.size contains the dataSize
         */
         RST,

         /** X positioning<br>
         fp1.f31 must contain the field width
         */
         X,

         /** SKIP positioning <br>
         fp1.f31 must contain the number of lines to terminate */
         SKIP,

         /** PAGE positioning <br>
         fp1.f31 must contain the number of pages to skip */
         PAGE,

         /** EOF positioning <br>
         EOF is a macro in C++ in cstdio */
         EOFPOS,

         /** advance  ADV 1-dimensional<br>
         fp1.outParam contain refer the number of columns to skip */
         ADV1,

         /** advance  ADV 2-dimensional<br>
         fp1.f31 must contain the number of rows to skip<br>
         fp2.f31 must contain the number of columns to skip */
         ADV2,

         /** advance  ADV 3-dimensional<br>
         fp1.f31 must contain the number of pages to skip<br>
         fp2.f31 must contain the number of rows to skip<br>
         fp3.f31 must contain the number of columns to skip */
         ADV3,

         //-- absolute positioning

         /** COL positioning<br>
         fp1.f31 must contain the target column number*/
         COL,

         /** LINE positioning<br>
         fp1.f31 must contain the target line number */
         LINE,

         /** POS 1-dimensional<br>
         fp1.f31 must contain the target column number*/
         POS1,

         /** POS 2-dimensional<br>
         fp1.f31 must contain the target row number <br>
         fp2.f31 must contain the target column number */
         POS2,

         /** POS 3-dimensional<br>
         fp1.f31 must contain the target page number<br>
         fp2.f31 must contain the target row number <br>
         fp3.f31 must contain the target column number */
         POS3,

         /** SOP 1-dimensional<br>
         fp1.fxxPtr.voidPtr must refer the variable for the  column number<br>
         fp1.fxxPtr.size contains the size of the FIXED variable*/
         SOP1,

         /** SOP 2-dimensional<br>
         fp1.fxxPtr.voidPtr must refer the variable for the row/line number<br>
         fp1.fxxPtr.size contains the size of the FIXED variable<br>
         fp2.fxxPtr.voidPtr must refer the variable for the  column number<br>
         fp2.fxxPtr.size contains the size of the FIXED variable*/
         SOP2,

         /** SOP 3-dimensional<br>
         fp1.fxxPtr.voidPtr must refer the variable for the page number<br>
         fp1.fxxPtr.size contains the size of the FIXED variable<br>
         fp2.fxxPtr.voidPtr must refer the variable for the row/line number<br>
         fp2.fxxPtr.size contains the size of the FIXED variable<br>
         fp3.fxxPtr.voidPtr must refer the variable for the column number<br>
         fp3.fxxPtr.size contains the size of the FIXED variable*/
         SOP3,

         /** a virtual format entry, which is used to inject a PEARL
             signal, if the evaluation of an expression of a
             format item failed

           fp1.intValue must contain the signal number
         */
         InduceFormat
      };

      /**
      the requested io format.
      */
      IOFormat format;

      /**
      The format elements may contain 1 to 3 parameters.

      Unused parameters may be left uninitialized.

      Format elements like RST or SOP need a parameter to store data
      from the dation status. Other parameters are used read only by
      the formatting like the width of an F-format
      */
      union FormatParameter {
         /** FIXED(31) value to be used for the width in F-format */
         Fixed<31>  f31;
         /** pointer to a FIXED(xx) value to receive results like in SOP */
         struct {
            /** start address of the value  */ 
            void * voidPtr;  
            /** number of bits (xx) f the FIXED value */
            int  size;
         } fxxPtr;
         /** an int value used only in InduceFormat item to specify the
             signal number  */
         int intValue;
      } fp1,  ///< first format parameter
      fp2,  ///< second format parameter
      fp3;  ///< third format parameter
   };
   /* @} */
}
#endif

