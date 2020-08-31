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
#ifndef STRINGDATIONCONVERT_INCLUDED
#define STRINGDATIONCONVERT_INCLUDED
/**
\file

\brief a kind of dation upon a string for the CONVERT statement

*/
#include "RefCharSink.h"
#include "RefCharSource.h"
#include "Rst.h"
#include "IOFormats.h"

namespace pearlrt {
   /**
     \addtogroup io_common
     @{
   */

   /**
     \brief Provides the methods for the formatting from and to character string

     The i/o-operations are done via a source/sink object which decouples
     the i/o from the formatting statements.

     In case of violating the string boundaries with an i/o statement,
     an exception is thrown, without performing the violating i/o statement.
     If an RST-value is set, the try-catch-block will update the RST-value
     and exit the CONVERT.

     The complete operation is treated by a list of data elements and a list
     of format elements. See IODataList and IOFormatList about details.
     For expression results intermediate variable must be defined locally and
     used in the lists. The evaluation of the expressions must be done after
     creation of the lists and the invocation of the put- or get-method.


   PEARL Example

   \code
   Start: TASK MAIN
      DCL x FIXED(15) INIT(2);
      DCL string CHAR(30);
      CONVERT 'X=', x TO string BY A, F(3);
      CONVERT  x FROM string BY X(3), F(3);
   END;
   \endcode

    C++
   \code

   Task(start,255,09 {
    ...    
    {  // CONVERT TO start 
        pearlrt::RefCharacter rc(string);
                                    // true indicates output
        pearlrt::StringDationConvert strDation(rc, true);
            
        {
           // declare local variables 
           // setup data and format lists
           // evaluate local variables
           strDation.convertTo(me, dataList, formatList);
        }
    } // convert end

    {  // CONVERT FROM start 
        pearlrt::RefCharacter rc(string);
                                   // false indicates input
        pearlrt::StringDationConvert strDation(rc, false);
            
           // declare local variables 
           // setup data and format lists
           // evaluate local variables
           strDation.convertFrom(me, dataList, formatList);
    } // convert end
   \endcode

   */
   class StringDationConvert: public IOFormats, public Rst {

   private:
      RefCharacter* string;

      RefCharSink   sink;
      RefCharSource source;

      Fixed<31> currentPosition;
      bool isOutput;

      int formatItem;   // current item in the format list
      TaskCommon* me;
      IOFormatList * formatList;      

   public:
      /**
        create a StringDationConvert object for a CONVERT statement

        \param string pointer to the RefCharacter object containing 
                      the soure/target string
        \param isOutput flag to select source(=false) or sink(=true)
      */
      StringDationConvert(RefCharacter* string, bool isOutput);

     private:
    /**
       return a character back to the input source
       \param c the character to be returned
       */
      void dationUnGetChar(const char c);

     /**
      check if enough space/data is available for the operation.
      The read/write pointer is not modified.
      By this trick, the subsequent i/o-operation will not starve 
      on characters -- the operation will stop with an error (exception)
     

      \param n number of bytes which are wanted to read or write

      \throws CharacterTooLongSignal if the string has less space
      */
      void checkCapacity(Fixed<31> n);

     /**
        treat one output job entry, which must be a positioning element

        \param me pointer to the calling task
        \param jobFormat  pointer to the current entry
        \returns 0, if done normally<br>
                 1, if record wasd left
        */
      int toPositioningFormat(TaskCommon * me, IOFormatEntry * jobFormat);

      /**
        treat one input job entry, which must be a positioning element

        \param me pointer to the calling task
        \param jobFormat  pointer to the current entry
        \returns 0, if done normally<br>
                 1, if record wasd left
        */
      int fromPositioningFormat(TaskCommon * me, IOFormatEntry * jobFormat);


      /**
      apply all positioning formats until then nest data format is reached
      */
      void applyAllPositioningFormats( LoopControl& formatLoop, bool directionTo);
      
     public:
      /**
      X-format for output

      \param n number of spaces to write
      */
      void toX(Fixed<31> n);

      /**
      X-format for input

      \param n number of characters to skip
      */
      void fromX(Fixed<31> n);

      /**
      ADV-format for in-/output

      \param n delta to new  position.  
      */
      void adv(Fixed<31> n);

      /**
      POS-format for in-/output

      \param n new position
      */
      void pos(Fixed<31> n);

      /**
      SOP-format for in/output

      \param n current postion
      */
      void sop(Fixed<31> &n);

      /**
      process an io job with put

      \param me pointer to the calling task
      \param dataEntries is a pointer to the data entries
      \param formatEntries is a pointer to the format  entries
      */
      void convertTo(TaskCommon *me, IODataList *dataEntries,
               IOFormatList * formatEntries);

      /**
      process an io job with get

      \param me pointer to the calling task
      \param dataEntries is a pointer to the data entries
      \param formatEntries is a pointer to the format  entries
      */
      void convertFrom(TaskCommon *me, IODataList *dataEntries,
               IOFormatList * formatEntries);
   };

   /** @} */
}
#endif
