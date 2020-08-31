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

/**
\file 
\brief implementation of  StringDationConvert

*/
#include "StringDationConvert.h"
#include "Signals.h"
#include "Log.h"
#include "Fixed.h"

namespace pearlrt {

   static const pearlrt::Fixed<31> one (1);

   StringDationConvert::StringDationConvert(RefCharacter * string,
                                            bool isOutput)
 : sink(*string), source(*string) {
      this->string = string;
      this->isOutput = isOutput;

      setupIOFormats(&sink, &source);

      if (isOutput) {
          // clear target string
          string->clear();
	  string->fill();
          string->clear();
          sink.pos(0);
      } else {
          source.rewind();
      }
   }

   void StringDationConvert::dationUnGetChar(const char c) {
      source.unGetChar(c);
   }

   void StringDationConvert::checkCapacity(Fixed<31> n) {
      // check 0 or negativ, which must be accepted to produce the
      // correct Signal from the I/O formats
      if (n.x <= 0) return;
      if (string->getCurrent()+n.x > string->getMax() ) {
          Log::error("attempt to read/write past the string limits");
          throw theCharacterTooLongSignal;
      }
   }

   void StringDationConvert::toX(Fixed<31> n) {
       int i;

       checkCapacity(n);
       if (n.x > 0) {
           for (i=0; i<n.x; i++) {
              sink.putChar(' ');
           }
       } else {
          adv(n);
       }
   }

   void StringDationConvert::fromX(Fixed<31> n) {
       adv(n);
   }

   void StringDationConvert::adv(Fixed<31> n) {
       checkCapacity(n);
       if (isOutput) {
          Fixed<31> pos(sink.sop());
          pos = pos + n; 
          sink.pos(pos.x);
       } else {
          Fixed<31> pos(source.sop());
          pos = pos + n; 
          source.pos(pos.x);
       }
   }

   void StringDationConvert::pos(Fixed<31> n) {
       try {
          n = n - one;   // adjust numbering: PRL uses 1..n; runtime 0..
          if (isOutput) {
             sink.pos(n.x);
          } else {
             source.pos(n.x);
          }
       } catch(CharacterTooLongSignal s) {
          throw theDationIndexBoundSignal;
       }
   }

   void StringDationConvert::sop(Fixed<31> &n) {
       if (isOutput) {
          Fixed<31> pos(sink.sop());
          n = pos;
       } else {
          Fixed<31> pos(source.sop());
          n = pos;
       }
       n = n + one; // adjust numbering; PRL uses 1..n, runtime: 0...
   }


   int StringDationConvert::toPositioningFormat(TaskCommon * me,
                                         IOFormatEntry * fmtEntry) {
      int returnValue = 0;

      switch (fmtEntry->format) {
      default:
         Log::error("CONVERT: unsupported format %d", fmtEntry->format);
         throw theInternalDationSignal;

      case IOFormatEntry::X:
         toX(fmtEntry->fp1.f31);
         break;

      case IOFormatEntry::ADV1:
         adv(fmtEntry->fp1.f31);
         break;

      case IOFormatEntry::POS1:
         pos(fmtEntry->fp1.f31);
         break;

      case IOFormatEntry::SOP1:
         {
            Fixed<31> help;
            sop(help);
	    assignInt32ToFixedViaVoidPointer(fmtEntry->fp1.fxxPtr.voidPtr,
             				   fmtEntry->fp1.fxxPtr.size,
					   help.x);
         }
         break;

      case IOFormatEntry::RST:
         rst(fmtEntry->fp1.fxxPtr.voidPtr,
             fmtEntry->fp1.fxxPtr.size);
         break;

      case IOFormatEntry::InduceFormat:
         Signal::throwSignalByRst(fmtEntry->fp1.intValue);
         break;
      }

      return returnValue;
   }

   int StringDationConvert::fromPositioningFormat(TaskCommon * me,
                                           IOFormatEntry * fmtEntry) {
      int returnValue = 0;

      switch (fmtEntry->format) {
      default:
         Log::error("CONVERT: unsupported format %d", fmtEntry->format);
         throw theInternalDationSignal;
         break;

      case IOFormatEntry::X:
         fromX(fmtEntry->fp1.f31);
         break;

      case IOFormatEntry::ADV1:
         adv(fmtEntry->fp1.f31);
         break;

      case IOFormatEntry::POS1:
         pos(fmtEntry->fp1.f31);
         break;

      case IOFormatEntry::SOP1:
         {
            pearlrt::Fixed<31> help;
            sop(help);
	    assignInt32ToFixedViaVoidPointer(fmtEntry->fp1.fxxPtr.voidPtr,
             				   fmtEntry->fp1.fxxPtr.size,
					   help.x);
         }
         break;

      case IOFormatEntry::RST:
         rst(fmtEntry->fp1.fxxPtr.voidPtr,
             fmtEntry->fp1.fxxPtr.size);
         break;

      case IOFormatEntry::InduceFormat:
         Signal::throwSignalByRst(fmtEntry->fp1.intValue);
         break;
      }

      return returnValue;
   }

   void StringDationConvert::convertTo(TaskCommon * me,
                      IODataList * dataList, IOFormatList * formatList) {

      int dataElement;
      formatItem = -1;  // will be incremented before first use
      this->me = me;
      this->formatList = formatList;

      rstVoidPointer = NULL;

      // create a loop control structure for the format list treatment
      LoopControl formatLoop(formatList->nbrOfEntries, true);
      LoopControl dataLoop(dataList->nbrOfEntries, false);

      try {

         // get first data element
         dataElement = dataLoop.next();

         while (dataElement < (int)dataList->nbrOfEntries) {
            // test for begin of loop, repeatedly
            while (dataList->entry[dataElement].dataType.baseType ==
                   IODataEntry::LoopStart) {

               dataElement = dataLoop.enter(
                                dataList->entry[dataElement].dataType.dataWidth,
                                dataList->entry[dataElement].param1.numberOfElements,
                                dataList->entry[dataElement].dataPtr.offsetIncrement);
            }

     	    applyAllPositioningFormats(formatLoop,true);
            if (dataList->entry[dataElement].param1.numberOfElements <= 0) {
               Log::error("array slice select %d elements",
                 dataList->entry[dataElement].param1.numberOfElements);
                throw theBadArraySliceSignal;
            }

            // treat arrays of simple types
            for (int32_t dataIndex = 0;
                  dataIndex < (dataList->entry[dataElement].param1.numberOfElements);
                  dataIndex++) {

               putDataFormat(me, &dataList->entry[dataElement],
                             dataIndex,
                             dataLoop.getOffset(),
                             &formatList->entry[formatItem]);
              if ( dataIndex +1 < 
                   (dataList->entry[dataElement].param1.numberOfElements)) {
     	          applyAllPositioningFormats(formatLoop,true);
	      }
            }

            // end of array od simple type
            // take next data element
            dataElement = dataLoop.next();
         }

         // treat pending positioning format elements
         formatItem ++;

         while (formatList->entry[formatItem].format ==
                IOFormatEntry::LoopStart) {
            formatItem = formatLoop.enter(
                            formatList->entry[formatItem].fp1.intValue,
                            formatList->entry[formatItem].fp2.intValue);
         }

         // treat pending format elements after last data element
         while ((size_t)formatItem < formatList->nbrOfEntries) {
            if (formatList->entry[formatItem].format <=
                  IOFormatEntry::IsPositioning) {
               break;
            }

            toPositioningFormat(me, &formatList->entry[formatItem]);
            formatItem++;

            while (formatList->entry[formatItem].format ==
                   IOFormatEntry::LoopStart) {
               formatItem = formatLoop.enter(
                               formatList->entry[formatItem].fp1.intValue,
                               formatList->entry[formatItem].fp2.intValue);
            }
         }

      } catch (Signal &s) {
         if (! updateRst(&s)) {
            throw;
         }
      }
   }

   void StringDationConvert::applyAllPositioningFormats(LoopControl & formatLoop,
             bool directionTo) {
      formatItem = formatLoop.next();

      while (formatList->entry[formatItem].format ==
                      IOFormatEntry::LoopStart) {
         formatItem = formatLoop.enter(
                          formatList->entry[formatItem].fp1.intValue,
                          formatList->entry[formatItem].fp2.intValue);
      }

      while (formatList->entry[formatItem].format >=
                      IOFormatEntry::IsPositioning) {
         if (directionTo) {
            toPositioningFormat(me, &formatList->entry[formatItem]);
         } else {
            fromPositioningFormat(me, &formatList->entry[formatItem]);
         } 

         formatItem = formatLoop.next();

         while (formatList->entry[formatItem].format ==
                         IOFormatEntry::LoopStart) {
            formatItem = formatLoop.enter(
                formatList->entry[formatItem].fp1.intValue,
               formatList->entry[formatItem].fp2.intValue);
         }
      }
   }

   void StringDationConvert::convertFrom(TaskCommon * me,
                      IODataList * dataList, IOFormatList * formatList) {

      int dataElement;
      formatItem = -1;  // will be incremented before first use
      this->me = me;
      this->formatList = formatList;

      rstVoidPointer = NULL;

      // create a loop control structure for the format list treatment
      LoopControl formatLoop(formatList->nbrOfEntries, true);
      LoopControl dataLoop(dataList->nbrOfEntries, false);

      try {

         // get first data element
         dataElement = dataLoop.next();

         while (dataElement < (int)dataList->nbrOfEntries) {
            // test for begin of loop, repeatedly
            while (dataList->entry[dataElement].dataType.baseType ==
                   IODataEntry::LoopStart) {
               dataElement = dataLoop.enter(
                                dataList->entry[dataElement].dataType.dataWidth,
                                dataList->entry[dataElement].param1.numberOfElements,
                                dataList->entry[dataElement].dataPtr.offsetIncrement);
            }

            // treat arrays of simple types
     	    applyAllPositioningFormats(formatLoop,false);

            if (dataList->entry[dataElement].param1.numberOfElements <= 0) {
                  Log::error("array slice select %d elements",
                      dataList->entry[dataElement].param1.numberOfElements);
                  throw theBadArraySliceSignal;
            }

            for (int32_t dataIndex = 0;
                  dataIndex < (dataList->entry[dataElement].param1.numberOfElements);
                  dataIndex++) {

               getDataFormat(me, &dataList->entry[dataElement],
                             dataIndex,
                             dataLoop.getOffset(),
                             &formatList->entry[formatItem]);
               if (dataIndex +1 < 
                   dataList->entry[dataElement].param1.numberOfElements) {
     	          applyAllPositioningFormats(formatLoop,false);
               }
            }

            // end of array od simple type
            // take next data element
            dataElement = dataLoop.next();
         }

         // treat pending positioning format elements
         formatItem ++;

         while (formatList->entry[formatItem].format ==
                IOFormatEntry::LoopStart) {
            formatItem = formatLoop.enter(
                            formatList->entry[formatItem].fp1.intValue,
                            formatList->entry[formatItem].fp2.intValue);
         }

         // treat pending format elements after last data element
         while ((size_t)formatItem < formatList->nbrOfEntries) {
            if (formatList->entry[formatItem].format <=
                  IOFormatEntry::IsPositioning) {
               break;
            }

            fromPositioningFormat(me, &formatList->entry[formatItem]);
            formatItem++;

            while (formatList->entry[formatItem].format ==
                   IOFormatEntry::LoopStart) {
               formatItem = formatLoop.enter(
                               formatList->entry[formatItem].fp1.intValue,
                               formatList->entry[formatItem].fp2.intValue);
            }
         }

      } catch (Signal &s) {
         if (! updateRst(&s)) {
            throw;
         }

      }
   }
}
