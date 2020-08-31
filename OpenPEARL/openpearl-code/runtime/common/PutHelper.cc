/*
 [A "BSD license"]
 Copyright (c) 2012-2016 Rainer Mueller
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

\brief helper functions for Put-processing

\author R. Mueller

*/

#include <stdio.h>
#include <ctype.h>

#include "PutHelper.h"
#include "Signals.h"
#include "RefChar.h"
#include "Log.h"


namespace pearlrt {

   /* helper routine, to reduce the memory footprint of the code   */
   /* doing the copy stuff is in a function and not as template code */
   void PutHelper::doPutChar(int width, RefCharacter * rc, Sink * sink) {
      int length;
      int i;

      if (width <= 0) {
         Log::info("width < 0");
         throw theCharacterFormatSignal;
      }

      length = (width < (int)(rc->getMax())) ? width : (int)(rc->getMax());

      for (i = 0; i < length; i++) {
         sink->putChar(rc->getCharAt(i));
      }

      for (i = length; i < width; i++) {
         sink->putChar(' ');
      }

      return;
   }

   const Float<52> PutHelper::binExpValues[] = {
      (Float<52>)(1e256), (Float<52>)(1e128), (Float<52>)(1e64),
      (Float<52>)(1e32), (Float<52>)(1e16), (Float<52>)(1e8),
      (Float<52>)(1e4), (Float<52>)(1e2), (Float<52>)(1e1)
   };

   const int PutHelper::nbrBinExpValues = sizeof(PutHelper::binExpValues) /
                                          sizeof(PutHelper::binExpValues[0]);
}
