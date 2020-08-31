/*
 [The "BSD license"]
 Copyright (c) 2012-2014 Rainer Mueller
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
\brief The base class for all PEARL signals.

*/

#include <stdlib.h>
#include "Signals.h"
#include "Log.h"




namespace pearlrt {

#ifndef DOXYGEN_SHOULD_SKIP_THIS
#include "Signals.hcc"
#endif
   void Signal::throwSignalByRst(int rst) {
      size_t i;

      for (i = 0; i < nbrOfSignals; i++) {
         //if sig with searched rst is found throw the sig
         if (signalVector[i]->whichRST() == rst) {
            throw *(signalVector[i]);
         }
      }

      Log::error("signal with RST=%d not found in signal list", rst);
      throw theInternalSignalsSignal;
   }


   Signal::Signal() {
      type = (char*)"Signal";
      rstNum = 0;
   }


   const char* Signal::which(void) {
      return type;
   }

   int Signal::whichRST(void) {
      return rstNum;
   }

   void Signal::induce(void) {
      currentRst = rstNum;
      throw *this;
   }

   void Signal::induce(int r) {
      currentRst = r;
      throw *this;
   }

   int Signal::getCurrentRst(void) {
      return currentRst;
   }
}

