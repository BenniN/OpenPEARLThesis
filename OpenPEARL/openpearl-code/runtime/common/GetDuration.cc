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

\brief input formating  for the data type DURATION


\author R. Mueller

*/

#include <stdio.h>
#include <inttypes.h>
#include <ctype.h>

#include "Duration.h"
#include "GetDuration.h"
#include "Signals.h"
#include "GetHelper.h"
#include "Log.h"


namespace pearlrt {


   int GetDuration::fromD(Duration& dur ,
                          const Fixed<31> w,
                          const Fixed<31> d,
                          Source & source) {
      int width = w.x;
      int decimals = d.x;
      uint64_t number;
      Fixed63 durF63;
      uint64_t hours = 0;
      int  min = 0;
      int sec = 0;
      int usec = 0;
      bool hasHours = false, hasMinutes=false, hasSeconds = false, hasNumber = false;
      int c1;
      int sign = 1 ;
      //                0123456789012345678901234567890123
      char logText[] = "D-format: illegal field (at: xxxxx)";
      int setCharsAt = 29;
      int charsToSet = 5;
      bool errorExitWithoutLog=false;
      bool errorExitWithC1=false;

      enum {NUMBER = 1, HRS = 2, MIN = 4, POINT = 8, 
 	    SEC = 16, SPACE = 32};
      int possibleElements = NUMBER | SPACE;
      int remainingElements = HRS | MIN | SEC | POINT;

      if (width <= 3) {
         Log::debug("fromD: width <= 3");
         throw theDurationFormatSignal;
      }

      if (decimals < 0) {
         Log::debug("fromD: decimals < 0");
         throw theDurationFormatSignal;
      }

      GetHelper helper(w, &source);
      helper.setDelimiters(GetHelper::EndOfLine);

      if (helper.skipSpaces() != 0) {
         goto errorExit;
      }

      if (helper.readString("-") == 0) {
         sign = -1;
      }
      if (helper.readString("+") == 0) {
         sign = 1;
      }

      do {
         if (possibleElements & SPACE) {
            if (helper.skipSpaces() != 0) {
               // end of field reached
               possibleElements = 0;
            }
         }
   
         if (possibleElements & NUMBER) {
            if (helper.readInteger(&number, width) > 0) {
               hasNumber = true;
               possibleElements = SPACE | remainingElements; 
               if (number > UINT32_MAX) {
                  Log::error("D-format: number too large");
		  errorExitWithoutLog = true;
                  goto errorExit;
               }
            } else {
               Log::error("D-format: number expected");
	       errorExitWithoutLog = true;
               goto errorExit;
            } 
         } else {
            if (possibleElements & (HRS | MIN | SEC | POINT)) {
               c1 = helper.readChar();
               switch (c1) {
               default:
                  errorExitWithC1 = true;
                  goto errorExit;

               case 'H':
                  if (helper.readString("RS") != 0) {
                     goto errorExit;
                  }
                  hours = number;
                  hasNumber = false;
                  hasHours = true;
                  remainingElements &= ~ HRS;
                  possibleElements = SPACE | NUMBER | POINT; 
                  break;

               case 'M':
                  if (helper.readString("IN") != 0) {
                     goto errorExit;
                  }

		  if (number >= 60) {
		     Log::error("D-format: minutes too large");
		     errorExitWithoutLog = true;
		     goto errorExit;
		  }
                  min = number;
                  hasNumber = false;
                  hasMinutes = true;
                  hasHours = true;
                  remainingElements &= ~ MIN;
                  remainingElements &= ~ HRS;
                  possibleElements = SPACE | NUMBER | POINT; 
                  break;

               case 'S':
                  if (helper.readString("EC") != 0) {
                     goto errorExit;
                  }

		  if (number >= 60) {
		     Log::error("D-format: seconds too large");
		     errorExitWithoutLog = true;
		     goto errorExit;
		  }

                  remainingElements &= ~ (HRS | MIN | SEC | POINT);
                  possibleElements = 0;
                  sec = number;
                  hasNumber = false;
                  hasSeconds = true;
                  hasMinutes = true;
                  hasHours = true;
                  break;

               case '.':
                  sec = number;
                  c1 = helper.readChar();
                  decimals = 0;
                  while (isdigit(c1) && decimals < 6) {
                     usec *= 10;
                     usec += (c1 - '0');
                     c1 = helper.readChar();
                     decimals ++;
                  }
                  if (decimals == 6) {
                     if (isdigit(c1) && c1 > '4') {
                        usec += 1;
                     }
                     while (isdigit(c1)) {
                        c1 = helper.readChar();
                    }
                  }
                  while (decimals <6) {
                    usec*=10;
                    decimals ++;
                  } 
                  while ( c1 == ' ') { 
                     c1 = helper.readChar();
                  }
		  if (number >= 60) {
		     Log::error("D-format: seconds too large");
		     errorExitWithoutLog = true;
		     goto errorExit;
		  }
                  if (c1 == 'S') {
                     if (helper.readString("EC") != 0) {
                        goto errorExit;
                     }

                     hasSeconds = true;
                     hasMinutes = true;
                     hasHours = true;
                     possibleElements = 0;
                  } else {
		     Log::error("D-format: no SEC token");
	             errorExitWithoutLog = true;
                     goto errorExit;
		  }
               }
            }
         }
      } while (possibleElements );

      if (!(hasHours || hasMinutes || hasSeconds || hasNumber)) {
          Log::error("D-format: no data in field");
	  errorExitWithoutLog = true;
	  goto errorExit;
      }

      if (!hasSeconds && hasNumber) {
          Log::error("D-format: SEC missing");
	  errorExitWithoutLog = true;
	  goto errorExit;
      }

      // let's calculate in Fixed63
      try {
         durF63 = Fixed63(hours);
         durF63 *= 60;
         durF63 += min;
         durF63 *= 60;
         durF63 += sec;
         dur = Duration(durF63.get(), usec, sign);
      } catch (Signal &s) {
          Log::error("D-format: value too large");
          throw theDurationValueSignal;
      } 
      
      if ( helper.skipSpaces() == -1) {
         // end of field reached with spaces
         return 0;
      }

      // else preform error exit routine

errorExit:
      // format error at all else cases

      if (errorExitWithC1) {
        logText[setCharsAt++] = c1;
        charsToSet --;
      }

      width = helper.getRemainingWidth();

      while (width > 0) {
         width --;
         c1 = source.getChar();
         if (charsToSet > 0) {
            logText[setCharsAt++] = c1;
            charsToSet --;
         }
      }

      while (charsToSet > 0) {
         logText[setCharsAt++] = ' ';
         charsToSet --;
      }

      if (!errorExitWithoutLog) {
         Log::error(logText);
      }
      throw theDurationValueSignal;
   }
}




