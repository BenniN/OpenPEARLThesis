/*
 [The "BSD license"]
 Copyright (c) 2015-2015 Rainer Mueller
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
\brief implementation of TS-userdation

*/
#include "Dation.h"
#include "UserDation.h"
#include "DationTS.h"
#include "Signals.h"
#include "Log.h"
#include "SystemDationB.h"
#include "Fixed.h"
#include "compare.h"

namespace pearlrt {

   DationTS::DationTS(SystemDationB* parent,
                      int params) {

      // upcast is safe, since downcast only appears
      // only in dationRead and dationWrite  in this class
      system = parent;

      dationStatus = CLOSED;

      if (params & (NOCYCL | CYCLIC | FORWARD | DIRECT | FORBACK)) {
         Log::error("DationTS: illegal declartion parameters");
         throw theInternalDationSignal;
      }

      dationParams = params;
   }
   void DationTS::internalDationOpen(int p,
                                     RefCharacter * rc) {
      // open system dation
      if (p & IDF) {
         // pass filename if specified by IDF
         work = system->dationOpen(rc->getCstring(), dationParams);
      } else {
         // no filename specified by IDF --> pass NULL as name
         work = system->dationOpen(NULL, dationParams);
      }

      systemDation = work;

      internalOpen();
      dationStatus = OPENED;
   }

   void DationTS::closeSystemDation(int dationParams) {
      work-> dationClose(dationParams);
   }

   void DationTS::internalOpen() {
   }

   void DationTS::internalClose() {
   }

   void DationTS::dationRead(void * data, size_t size) {
      assertOpen();

      if (!(dationParams & (IN | INOUT))) {
         Log::error("DationTS: dation not opened as input");
         throw theInternalDationSignal;
      }

      if (!(system->capabilities() & (IN | INOUT))) {
         Log::error("DationTS: device does not support read");
         throw theInternalDationSignal;
      }

      work->dationRead(data, size);
   }

   void DationTS::dationWrite(void* data, size_t size) {
      assertOpen();

      if (!(dationParams & (OUT | INOUT))) {
         Log::error("DationTS: Only writing is allowed");
         throw theInternalDationSignal;
      }

      if (!(system->capabilities() & (OUT | INOUT))) {
         Log::error("DationTS: device does not support write");
         throw theInternalDationSignal;
      }
      work->dationWrite(data, size);
   }

   bool DationTS::allowMultipleIORequests() {
       return false;
   }

   void DationTS::beginSequenceHook(TaskCommon * me) {
   }

   void DationTS::endSequenceHook(void) {
   }

   void DationTS::send(TaskCommon*me,
                        IODataList * dataList, IOFormatList * formatList) {

      size_t nbrOfBytes;
      char * startAddress;

      try {
         beginSequence(me, Dation::OUT);

         // execute formatlist first  -- only RST is allowed
         if (formatList && formatList->nbrOfEntries > 0) {
            rst(formatList->entry[0].fp1.fxxPtr.voidPtr,
                formatList->entry[0].fp1.fxxPtr.size);
 	 } 
         if (dataList->entry[0].param1.numberOfElements <= 0) {
            Log::error("array slice select %d elements",
              dataList->entry[0].param1.numberOfElements);
            throw theBadArraySliceSignal;
         }

	 nbrOfBytes = dataList->entry[0].getSize();
 	 nbrOfBytes *= dataList->entry[0].param1.numberOfElements;
         startAddress  = (char*)(dataList->entry[0].dataPtr.inData);
         startAddress += dataList->entry[0].getStartOffset();
         dationWrite(startAddress, nbrOfBytes);

         endSequence(me);
      } catch (Signal &s) {
         if (! updateRst(&s)) {
            endSequence(me);
            throw;
         }

         endSequence(me);
      }
   }

   void DationTS::take(TaskCommon*me,
                       IODataList * dataList, IOFormatList * formatList) {

      size_t nbrOfBytes;

      try {
         beginSequence(me, Dation::IN);

         // execute formatlist first  -- only RST is allowed
         if (formatList && formatList->nbrOfEntries > 0) {
            rst(formatList->entry[0].fp1.fxxPtr.voidPtr,
                formatList->entry[0].fp1.fxxPtr.size);
 	 } 

         if (dataList->entry[0].param1.numberOfElements <= 0) {
            Log::error("array slice select %d elements",
              dataList->entry[0].param1.numberOfElements);
            throw theBadArraySliceSignal;
         }

	 nbrOfBytes = dataList->entry[0].getSize();
 	 nbrOfBytes *= dataList->entry[0].param1.numberOfElements;

         dationRead(dataList->entry[0].dataPtr.inData, nbrOfBytes);

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
