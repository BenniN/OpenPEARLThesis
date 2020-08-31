/*
 [The "BSD license"]
 Copyright (c) 2012-2013 Rainer Mueller
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

\brief input formating  for the data type CLOCK

The clock is based on the gettimeofday()-service of the linux system.
Internally, a clock variable contains the number of micro seconds since start
of a new day. Without special treatment of a time zone, GMT is used.


\author R. Mueller

*/

#include <stdio.h>
#include <ctype.h>
#include <inttypes.h>

#include "Clock.h"
#include "GetClock.h"
#include "Signals.h"
#include "GetHelper.h"
#include "Log.h"


namespace pearlrt {

   int GetClock::fromT(Clock&c,
                       const Fixed<31> w,
                       const Fixed<31> d,
                       Source & source) {
      int width = w.x;
      int decimals = d.x;
      uint64_t hours = 0;
      int min = 0;
      double sec;
      int c1;
      double timeValue;
      //                0123456789012345678901234567890123
      char logText[] = "illegal T-format field (at: xxxxx)";
      int setCharsAt = 28;
      int charsToSet = 5;
      bool errorWithLog = true;

      if (width <= 0) {
         Log::debug("fromT: width <= 0");
         throw theClockFormatSignal;
      }

      if (decimals < 0) {
         Log::debug("fromT: width < 0");
         throw theClockFormatSignal;
      }
//printf("w=%d d= %d\n", width, decimals);

      GetHelper helper(w, &source);
      helper.setDelimiters(GetHelper::EndOfLine);

      if (helper.skipSpaces() == 0) {
         if (helper.readInteger(&hours, 2) > 0) {
            if (helper.readString(":") != 0) {
               Log::error("T-format: colon after hours expected");
               errorWithLog = false;
            } else if (helper.readFixedInteger(&min, 2) > 0) {
               if (min >= 60) {
                  Log::error("T-format: minutes too large");
                  errorWithLog = false;
               } else if (min < 60 && helper.readString(":") != 0) {
                  Log::error("T-format: colon after minutes expected");
                  errorWithLog = false;
               } else {
                  // read seconds
                  width = helper.getRemainingWidth();
//printf("h=%d min = %d\n", hours, min);

                  if (helper.readSeconds(&sec) > 0) {
                     if (sec < 60) {
                        timeValue = sec;
                        hours %= 24;
                        timeValue += ((hours * 60) + min) * 60;
                        c = Clock(timeValue);

                        if (helper.skipSpaces() < 0) {
                           return 0;
                        }
                     }
                  } else {
                     Log::error("T-format: no value for second");
                     errorWithLog = false;
                  }
               }
            }
         } else {
            Log::error("T-format: no value for hours");
            errorWithLog = false;
         }
      }

      // discard remaining input bytes
      width = helper.getRemainingWidth();

      while (width > 0) {
         width --;
         c1 = source.getChar();

         if (errorWithLog) {
            if (charsToSet > 0) {
               logText[setCharsAt++] = c1;
               charsToSet --;
            }
         }
      }

      while (errorWithLog && charsToSet > 0) {
         logText[setCharsAt++] = ' ';
         charsToSet --;
      }

      if (errorWithLog) {
         Log::info(logText);
      }

      throw theClockValueSignal;
   }
}
