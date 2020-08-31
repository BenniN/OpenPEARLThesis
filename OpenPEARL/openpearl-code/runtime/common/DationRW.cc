/*
 [The "BSD license"]
 Copyright (c) 2012-2013 Holger Koelle
 Copyright (c) 2014-2014 Rainer Mueller
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
\brief implementation of Linux specific RW-userdation

*/
#include "Dation.h"
#include "UserDation.h"
#include "DationRW.h"
#include "Signals.h"
#include "Log.h"
#include "DationDim.h"
#include "DationDim1.h"
#include "DationDim2.h"
#include "DationDim3.h"
#include "SystemDationNB.h"
#include "Fixed.h"
#include "compare.h"

namespace pearlrt {

   DationRW::DationRW(SystemDationNB* parent,
                      int params,
                      DationDim * dimensions,
                      const Fixed<15> stepsize,
                      void * tfuRecord)
      : UserDationNB(parent, params, dimensions,
                     UserDationNB::TYPE) {

      stepSize = stepsize;

      if (tfuRecord && params & Dation::STREAM) {
         Log::error("DationRW: TFU and STREAM conflicts");
         throw theInternalDationSignal;
      }

      // setup the tfu buffer object
      // calculate the record size:
      //    perform a safe multiplication and take the int value
      // set the data area
      // set the padding element
      tfuBuffer.setupRecord((dim->getColumns() * stepsize).x,
                            (char*)tfuRecord,
                            0);

      dationStatus = CLOSED;
   }

   void DationRW::internalOpen() {
      tfuBuffer.setSystemDation((SystemDationNB*)systemDation);
   }

   void DationRW::internalClose() {
   }

   void DationRW::dationRead(void * data, size_t size) {
      assertOpen();

      if (!(dationParams & (IN | INOUT))) {
         Log::error("DationRW: dation not opened as input");
         throw theDationParamSignal;
      }

      if (!(systemDation->capabilities() & (IN | INOUT))) {
         Log::error("DationRW: device does not support read");
         throw theDationParamSignal;
      }

      if (dationParams & NOCYCL) {
         adv(size / stepSize.x);
         //systemDation->dationRead(data, size);
         tfuBuffer.read(data, size);
      } else {
         // dimension is limited. This is enshured by the tests in ctor
         // and CYLIC is set
         Fixed<31> zero(0);
         Fixed<31> cap = dim->getCapacity();
         Fixed<31> remaining = cap - dim->getIndex();
         Fixed<31> nbrOfElements = size / stepSize.x;
         char * d = (char*) data;

         // read first chunk
         if ((nbrOfElements > remaining).getBoolean()) {
            adv(remaining.x);
//            systemDation->dationRead(d, remaining.x * stepSize.x);
            tfuBuffer.read(d, (remaining * stepSize).x);
            d += remaining.x * stepSize.x;
            dationSeek(0, dationParams);
            nbrOfElements = nbrOfElements - remaining;
         }

         // read remaining chunks
         while ((nbrOfElements > zero).getBoolean()) {
            if ((nbrOfElements > cap).getBoolean()) {
               adv(cap.x);
               // systemDation->dationRead(d, cap.x * stepSize.x);
               tfuBuffer.read(d, (cap * stepSize).x);
               d += cap.x * stepSize.x;
               dationSeek(0, dationParams);
               nbrOfElements = nbrOfElements - cap;
            } else {
               adv(nbrOfElements.x);
               //systemDation->dationRead(d, nbrOfElements.x * stepSize.x);
               tfuBuffer.read(d, (nbrOfElements * stepSize).x);
               d += nbrOfElements.x * stepSize.x;
               nbrOfElements = zero;
            }
         }
      }
   }

   void DationRW::dationWrite(void* data, size_t size) {

      assertOpen();

      if (!(dationParams & (OUT | INOUT))) {
         Log::error("DationRW: Only writing is allowed");
         throw theDationParamSignal;
      }

      if (!(systemDation->capabilities() & (OUT | INOUT))) {
         Log::error("DationRW: device does not support read");
         throw theDationParamSignal;
      }

      if (dationParams & NOCYCL) {
         adv(size / stepSize.x);
         //systemDation->dationWrite(data, size);
         tfuBuffer.write(data, size);
      } else {
         // dimension is limited. This is enshured by the tests in ctor
         Fixed<31> zero(0);
         Fixed<31> cap = dim->getCapacity();
         Fixed<31> remaining = cap - dim->getIndex();
         Fixed<31> nbrOfElements = size / stepSize.x;
         char * d = (char*) data;

         // write first chunk
         if ((nbrOfElements > remaining).getBoolean()) {
            adv(remaining.x);
            //systemDation->dationWrite(d, remaining.x * stepSize.x);
            tfuBuffer.write(d, (remaining * stepSize).x);
            d += remaining.x * stepSize.x;
            dationSeek(0, dationParams);
            nbrOfElements = nbrOfElements - remaining;
         }

         // read remaining chunks
         while ((nbrOfElements > zero).getBoolean()) {
            if ((nbrOfElements > cap).getBoolean()) {
               adv(cap.x);
               //systemDation->dationWrite(d, cap.x * stepSize.x);
               tfuBuffer.write(d, (cap * stepSize).x);
               d += cap.x * stepSize.x;
               dationSeek(0, dationParams);
               nbrOfElements = nbrOfElements - cap;
            } else {
               adv(nbrOfElements.x);
               //systemDation->dationWrite(d, nbrOfElements.x * stepSize.x);
               tfuBuffer.write(d, (nbrOfElements * stepSize).x);
               d += nbrOfElements.x * stepSize.x;
               nbrOfElements = zero;
            }
         }
      }
   }

   void DationRW::dationSeek(const Fixed<31> & p, const int dationParam) {
      ((SystemDationNB*)systemDation)->dationSeek(p, dationParam);
   }

   void DationRW::dationUnGetChar(const char c) {
      ((SystemDationNB*)systemDation)->dationUnGetChar(c);
   }

   void DationRW::write(TaskCommon*me,
                        IODataList * dataList, IOFormatList * formatList) {

      size_t formatItem;
      size_t dataElement;
      size_t nbrOfBytes;
      char * startAddress;

      try {
         beginSequence(me, Dation::OUT);

         // execute formatlist first
         for (formatItem = 0; formatItem < formatList->nbrOfEntries;
               formatItem ++) {

            if (formatList->entry[formatItem].format <
                  IOFormatEntry::IsPositioning) {
               Log::error("non positioning element in format list");
               throw theInternalDationSignal;
            }

            toPositioningFormat(me, &formatList->entry[formatItem]);
         }

         for (dataElement = 0; dataElement < dataList->nbrOfEntries;
               dataElement++) {
	    if (dataList->entry[dataElement].param1.numberOfElements <= 0) {
              Log::error("array slice select %d elements",
	    	dataList->entry[dataElement].param1.numberOfElements);
              throw theBadArraySliceSignal;
            }

	    nbrOfBytes = dataList->entry[dataElement].getSize();
	    nbrOfBytes *= dataList->entry[dataElement].param1.numberOfElements;
            startAddress  = (char*)(dataList->entry[dataElement].dataPtr.inData);
            startAddress += dataList->entry[dataElement].getStartOffset();
            dationWrite(startAddress, nbrOfBytes);
         }

         endSequence(me);
      } catch (Signal &s) {
         if (! updateRst(&s)) {
            endSequence(me);
            throw;
         }

         endSequence(me);
      }
   }

   void DationRW::read(TaskCommon*me,
                       IODataList * dataList, IOFormatList * formatList) {

      size_t formatItem;
      size_t dataElement;
      size_t nbrOfBytes;

      try {
         beginSequence(me, Dation::IN);

         // execute formatlist first
         for (formatItem = 0; formatItem < formatList->nbrOfEntries;
               formatItem ++) {

            if (formatList->entry[formatItem].format <
                  IOFormatEntry::IsPositioning) {
               Log::error("non positioning element in format list");
               throw theInternalDationSignal;
            }
            fromPositioningFormat(me, &formatList->entry[formatItem]);
         }

         for (dataElement = 0; dataElement < dataList->nbrOfEntries;
               dataElement++) {
            if (dataList->entry[dataElement].param1.numberOfElements <= 0) {
              Log::error("array slice select %d elements",
                dataList->entry[dataElement].param1.numberOfElements);
              throw theBadArraySliceSignal;
            }
	    nbrOfBytes = dataList->entry[dataElement].getSize();
	    nbrOfBytes *= dataList->entry[dataElement].param1.numberOfElements;

            dationRead(dataList->entry[dataElement].dataPtr.inData,
                       nbrOfBytes);
         }

         endSequence(me);
      } catch (Signal &s) {
         if (! updateRst(&s)) {
            endSequence(me);
            throw;
         }

         endSequence(me);
      }
   }
}
