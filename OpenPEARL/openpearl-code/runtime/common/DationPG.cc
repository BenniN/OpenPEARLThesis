/*
 [A "BSD license"]
 Copyright (c) 2012-2013 Holger Koelle
 Copyright (c) 2014-2017 Rainer Mueller
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
\brief implementation of  PG-userdation

*/
#include "Dation.h"
#include "UserDation.h"
#include "DationPG.h"
#include "Signals.h"
#include "Log.h"
#include "DationDim.h"
#include "SystemDationNB.h"
#include "SystemDationNBSink.h"
#include "SystemDationNBSource.h"
#include "IOFormats.h"
#include "Fixed.h"
#include "PutHelper.h"
#include "PutClock.h"
#include "GetClock.h"
#include "PutDuration.h"
#include "GetDuration.h"


namespace pearlrt {

   DationPG::DationPG(SystemDationNB* parent,
                      int params,
                      DationDim * dimensions,
                      void * tfuRecord)
      : UserDationNB(parent, params, dimensions,
                     UserDationNB::ALPHIC) {
      dationStatus = CLOSED;

#warning "wieso hat DationPG kein CYCLIC support?"

      if (params & CYCLIC) {
         Log::error("DationPG: does not support CYCLIC");
         throw theDationParamSignal;
      }

      stepSize = Fixed<31>(1);
      tfuBuffer.setupRecord(dim->getColumns().x, (char*)tfuRecord, ' ');
   }

   void DationPG::dationRead(void * destination, size_t size) {
      char * d = (char*)destination;
      assertOpen();

      if (!(dationParams & (IN | INOUT))) {
         Log::error("DationPG: dation not opened as input");
         throw theDationParamSignal;
      }

      if (!(systemDation->capabilities() & (IN | INOUT))) {
         Log::error("DationPG: device does not support read");
         throw theDationParamSignal;
      }

      // get first character from unget buffer
      *d = source.getChar();

      // read remaining from device
      if (size > 1) {
         systemDation->dationRead(d + 1, size - 1);
      }
   }

   void DationPG::dationWrite(void * destination, size_t size) {
      char * d = (char*)destination;
      assertOpen();

      if (!(dationParams & (OUT | INOUT))) {
         Log::error("DationPG: Only writing is allowed");
         throw theDationParamSignal;
      }

      if (!(systemDation->capabilities() & (OUT | INOUT))) {
         Log::error("DationPG: device does not support write");
         throw theDationParamSignal;
      }

      // send data to sink
      for (size_t i = 0; i < size; i++) {
         sink.putChar(*d);
         d++;
      }
   }

   void DationPG::dationSeek(const Fixed<31> & p, const int dationParam) {
      assertOpen();
      source.forgetUnGetChar();
      ((SystemDationNB*)systemDation)->dationSeek(p, dationParam);
   }


   void DationPG::dationUnGetChar(const char c) {
      source.unGetChar(c);
   }

   void DationPG::internalOpen() {
      tfuBuffer.setSystemDation((SystemDationNB*)systemDation);
      /*
            } else {
               if (dationParams & (OUT | INOUT)) {
                  sink.setSystemDationNB((SystemDationNB*)systemDation);
               }

               if (dationParams & (IN | INOUT)) {
                  source.setSystemDationNB((SystemDationNB*)systemDation);
               }
            }
            setupIOFormats(&sink, &source);
      */
      setupIOFormats(&tfuBuffer, &tfuBuffer);
   }

   void DationPG::internalClose() {
   }

   void DationPG::checkCapacity(Fixed<31> n) {
      // check 0 or negativ, which must be accepted to produce the
      // correct Signal from the I/O formats
      if (n.x <= 0) return;

      // move the read/write pointer.
      // this method will throw an exception if there is too little space
      adv(n);
   }

   void DationPG::applyAllPositioningFormats(LoopControl & formatLoop,
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

   void DationPG::put(TaskCommon * me,
                      IODataList * dataList, IOFormatList * formatList) {

      int dataElement;

      try {
         beginSequence(me, Dation::OUT);
         this->formatItem = -1;
         this->formatList = formatList;
         this->me = me;
 
         // create a loop control structure for the format list treatment
         LoopControl formatLoop(formatList->nbrOfEntries, true);
         LoopControl dataLoop(dataList->nbrOfEntries, false);


         // get first data element
         dataElement = dataLoop.next();

         while (dataElement < (int)dataList->nbrOfEntries) {
            // test for begin of loop, repeatedly for nested loops
            while (dataList->entry[dataElement].dataType.baseType ==
                   IODataEntry::LoopStart) {

               dataElement = dataLoop.enter(
                                dataList->entry[dataElement].dataType.dataWidth,
                                dataList->entry[dataElement].param1.numberOfElements,
                                dataList->entry[dataElement].dataPtr.offsetIncrement);
            }

	    applyAllPositioningFormats(formatLoop, true);

            if (dataList->entry[dataElement].param1.numberOfElements <= 0) {
                Log::error("array slice select %d elements",
                  dataList->entry[dataElement].param1.numberOfElements);
                throw theBadArraySliceSignal;
            }
            // treat all data entries, which are  simple types or arrays of simple types
            // structs were unrolled by the compiler
            for (int32_t dataIndex = 0;
                  dataIndex < (dataList->entry[dataElement].param1.numberOfElements);
                  dataIndex++) {
               putDataFormat(me, &dataList->entry[dataElement],
                             dataIndex,
                             dataLoop.getOffset(),
                             &formatList->entry[formatItem]);
               if ( dataIndex +1 <
                   (dataList->entry[dataElement].param1.numberOfElements)) {
                  applyAllPositioningFormats(formatLoop, true);
               }

            }

            // end of array of simple type
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

         endSequence(me);

      } catch (Signal &s) {
         if (s.whichRST() == theTerminateRequestSignal.whichRST()) {
           endSequence(me);
           me->terminate(me); 
         }
         if (! updateRst(&s)) {
            endSequence(me);
            throw;
         }

         endSequence(me);
      }
   }


   void DationPG::get(TaskCommon * me,
                      IODataList * dataList, IOFormatList * formatList) {

      int dataElement;
      try {
         beginSequence(me, Dation::IN);
         this->formatList = formatList;
         this->formatItem = -1;
         this->me = me;

         // create a loop control structure for the format list treatment
         LoopControl formatLoop(formatList->nbrOfEntries, true);
         LoopControl dataLoop(dataList->nbrOfEntries, false);

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

            applyAllPositioningFormats(formatLoop,false);

            if (dataList->entry[dataElement].param1.numberOfElements <= 0) {
               Log::error("array slice select %d elements",
                 dataList->entry[dataElement].param1.numberOfElements);
               throw theBadArraySliceSignal;
            }

            // treat arrays of simple types
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

         endSequence(me);
      } catch (Signal &s) {
         if (s.whichRST() == theTerminateRequestSignal.whichRST()) {
           endSequence(me);
           me->terminate(me); 
         }
         if (! updateRst(&s)) {
            endSequence(me);
            throw;
         }

         endSequence(me);
      }
   }
}
