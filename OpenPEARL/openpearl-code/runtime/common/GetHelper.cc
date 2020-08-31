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

\brief helper functions for Get-processing

\author R. Mueller

*/

#include <stdint.h>
#include <limits.h>   //  INT_MAX
#include <stdio.h>
#include <ctype.h>
#include <math.h>
#include <cfloat>  // DBL_MAX_10_EXP
#include <inttypes.h>  // printf uint64_t

#include "GetHelper.h"
#include "RefChar.h"
#include "Signals.h"
#include "Fixed63.h"
#include "Log.h"


namespace pearlrt {


   GetHelper::GetHelper(const Fixed<31> w, Source * s) {
      width = w.x;
      source = s;
      delimiter = 0;
   }

   int GetHelper::getRemainingWidth() {
      return width;
   }

   int GetHelper::skipSpaces() {
      int c1;

      if (width > 0) {
         do {
            c1 = readChar();
         } while (c1 == ' ' && width > 0);

         if (c1 < 0) {
            // field delimiter reached
            width ++;
            return -2;
         }

         if (c1 != ' ') {
            source->unGetChar(c1);
            width ++;
            return 0;
         }
      }

      return -1;
   }

   void GetHelper::discardRemaining() {
      int c1 = 0;

      while (width > 0 && c1 >= 0) {
         c1 = readChar();
      }
   }

   int GetHelper::readInteger(uint64_t * x, int digits) {
      int ch;
      int digitsProcessed = 0;
      *x = 0;
      ch = readChar();

      if (!isdigit(ch)) {
         if (ch > 0) {
            source->unGetChar(ch);
            width ++;
         }

         return digitsProcessed;
      }

      *x = ch - '0';
      digits --;
      digitsProcessed ++;

      while (digits > 0 && width > 0) {
         ch = readChar();

         if (!isdigit(ch)) {
            if (ch > 0) {
               source->unGetChar(ch);
               width ++;
            }

            return digitsProcessed;
         }

         // stop reading at MAX_INT
         ch -= '0';

         if ((UINT64_MAX - ch) / 10 >= *x) {
            *x *= 10;
            *x += ch;
            digitsProcessed ++;
         } else {
            source->unGetChar(ch+'0');
            width ++;
            return digitsProcessed;
         }
      }

      return digitsProcessed;
   }

   int GetHelper::readFixedInteger(int * x, int digits) {
      char ch;
      int digitsProcessed = 0;
      *x = 0;

      while (digits > 0 && width > 0) {
         ch = readChar();

         if (!isdigit(ch)) {
            if (ch > 0) {
               source->unGetChar(ch);
               width ++;
            }

            return digitsProcessed;
         }

         // stop reading at MAX_INT
         ch -= '0';

         if ((INT_MAX - ch) / 10 >= *x) {
            *x *= 10;
            *x += ch;
            digitsProcessed ++;
         } else {
            source->unGetChar(ch + '0');
            width ++;
            return digitsProcessed;
         }
      }

      return digitsProcessed;
   }

   int GetHelper::readMantissa(double * x, const int w, const int decimals)  {
      /* we read 18 significant digits and skip remaining digits
         since they do not contribute to the precission at all.
         18 digits fit easily into two 32-bit integers.
         the location of the decimal point is used at the calculation
         of the result
      */
      int32_t high = 0; 	   // first 9 digits
      int32_t low = 0;             // last 9 digits
      int decimalPointPosition = -1; // no decimal point detected yet
      int ch;
      int digitsProcessed = 0;

      while (digitsProcessed < 9 && width > 0) {
         ch = readChar();

         if (ch == '0' && high == 0) {
            // ignore leading zeros 
         } else if (isdigit(ch)) {
            high = high * 10 + ch - '0';
            digitsProcessed ++;
         } else if (ch == '.' && decimalPointPosition < 0) {
            decimalPointPosition = digitsProcessed;
         } else {
            if (ch > 0) { // end field markers are not returned to the input
               source->unGetChar(ch);
               width ++;
            }

            goto endSampling;
         }

      }

      if (digitsProcessed == 9 && width > 0) {
         while (digitsProcessed < 18) {
            ch = readChar();

            if (isdigit(ch)) {
               low = low * 10 + ch - '0';
               digitsProcessed ++;
            } else if (ch == '.' && decimalPointPosition < 0) {
               decimalPointPosition = digitsProcessed;
            } else {
               if (ch > 0) { // end field markers are not returned to the input
                  source->unGetChar(ch);
                  width ++;
               }

               goto endSampling;
            }

         }
      }

      if (digitsProcessed == 18 && width > 0) {
         // discard remaining digits
         do {
            ch = readChar();

            if (isdigit(ch)) {
               digitsProcessed ++;
            } else if (ch == '.' && decimalPointPosition < 0) {
               decimalPointPosition = digitsProcessed;
            } else {
               if (ch > 0) { // end field markers are not returned to the input
                  source->unGetChar(ch);
                  width ++;
               }

               goto endSampling;
            }
         } while (ch > 0); // until end of field or goto endSampling
      }

endSampling:
/*
      if (skipSpaces() == 0) {
         discardRemaining();
         Log::info("F: illegal character in field");
         throw theFixedValueSignal;
      }
*/
      if (decimalPointPosition < 0) {
         decimalPointPosition = digitsProcessed - decimals;
      }

      if (digitsProcessed <= 9) {
         *x = high;
      } else if (digitsProcessed <= 18) {
         *x = high * pow10(digitsProcessed - 9) + low;
      } else {
         * x = (high * 1e9 + low)*pow10(digitsProcessed-18);
      }

      *x *= pow10(decimalPointPosition - digitsProcessed);
      return digitsProcessed;
   }

   int GetHelper::readSeconds(double * x) {
      int postPointDigits=0;
      int c1;
      double result=0.0; 
      int digitsProcessed = 0;

      do {
         c1 = readChar();

         if (isdigit(c1)) {
            result = result*10+(c1-'0');
            digitsProcessed ++;
         }
      } while (isdigit(c1) && width>0);

      if (width > 0) {
         if (c1 == '.') {
            // get decimals 
            do {
              c1 = readChar();
              if (isdigit(c1)) {
                 result = result*10+(c1 - '0');
                 digitsProcessed ++;
                 postPointDigits ++;
              } else {
                 if (c1 > 0) { // end field markers are not returned to the input
                    source->unGetChar(c1);
                    width ++;
                 }
                 if (c1 != ' ') {
                    return -1;
                 }
              }
            } while (isdigit(c1) && width>0 && postPointDigits<6);
         } else if (c1 == ' ') {
            // no decimal point 
         } else if (c1 != '.') {
            if (c1 > 0) { // end field markers are not returned to the input
                source->unGetChar(c1);
                width ++;
             }
             // eof is ok! return -1;
         }
      } 


      while (isdigit(c1) && width>0) {
           c1 = readChar();  // discard decimals beyond 1 micro second
      }

      while (postPointDigits > 0) {
          result /= 10.0;
          postPointDigits --;
      }
      if (result < 60.0) {
        *x = result;
        return (digitsProcessed);
      }
      return -1;   // number too large
   }

   int GetHelper::readString(const char * s) {
      int ch;

      while (width > 0 && *s) {
         ch = readChar();

         if (ch < 0 && *s > 0) {
            return -1;
         }

         if (ch != *s) {
            if (ch > 0) { // end of field markers are not returned to input
               source->unGetChar(ch);
               width++;
            }

            return -1;
         }

         s++;
      }

      if (*s == 0) {
         return 0;
      } else {
         return -1;
      }
   }

   void GetHelper::setDelimiters(int del) {
      delimiter = del;
   }

   int GetHelper::readChar() {
      int retVal = 0, retVal1 = 0;

      try {
         retVal = source->getChar();
      } catch (NoMoreCharactersSignal & s) {
         if (delimiter & EndOfFile) {
            return -1;
         } else {
            return -2;
         }
      } catch (TerminateRequestSignal s) {
         throw;
      } 

      if ((delimiter & DoubleSpace) && retVal == ' ') {
         try {
            retVal1 = source->getChar();
         } catch (TerminateRequestSignal s) {
            throw;
         } catch (NoMoreCharactersSignal & s) {
            if (delimiter & EndOfFile) {
               return -1;
            }

            if (retVal1 == ' ') {
               return -1;
            } else {
               source->unGetChar(retVal1);
            }
         }
      }

      if ((delimiter & EndOfLine) && retVal == '\n') {
         source->unGetChar(retVal);
         return -1;
      }

      if ((delimiter & Comma) && retVal == ',') {
         return -1;
      }

      width --;
      return retVal;
   }

   void GetHelper::readCharacterByA(RefCharacter * rc) {
      int c;   // characters or field delimiters to read
      int nbrOfCharsToTreat = rc->getMax();

      if (width <= 0) {
         throw theCharacterFormatSignal;
      }

      rc->clear();
      rc->fill();

      while (width > 0) {
         c = readChar();

         if (c == -1 || c == -2) {
            // eor    or   eof --> finish reading
            width = 0;
         } else {
            if (nbrOfCharsToTreat > 0) {
               rc->add(c);
               nbrOfCharsToTreat --;
            }
         }
      }

      return;
   }

   void GetHelper::readB4(uint64_t * value, int nbrOfBitsToSample) {
      int sampledBits = 0;
      int shifts;
      int cc, c;
      uint64_t result = 0;
//printf("readB4(nbrOfBitsToSample=%d\n", nbrOfBitsToSample);

      if (skipSpaces() == 0) {
         do {
            c = readChar();

            if (c > 0 && isxdigit(c))  {
               //if (sampledBits < nbrOfBitsToSample) {
                  cc = c - '0';

                  if (cc > 9) {
                     cc -= 'A' - '9' - 1;
                  }

                  result <<= 4;
                  result |= cc;
                  sampledBits += 4;
               //}
            } else if (c < 0 || c == ' ') {
               // do nothing - is treated in while condition
               // we need this empty clause to avoid the else-clause
               // in this case
            } else {
               Log::error("B-format: illegal character (0x%x)",c);
               throw theBitValueSignal;
            }
         } while (isxdigit(c)  &&
          sampledBits < nbrOfBitsToSample && getRemainingWidth() > 0);

         if (skipSpaces() == 0) {
           c = readChar();
           if (isxdigit(c)) {
              Log::error("B-format: bit string too long");
           } else {
              Log::error("B-format: illegal character (0x%x)",c);
           }
           discardRemaining();
           throw theBitValueSignal;
         }
         
         while (sampledBits > 0 && (sampledBits < nbrOfBitsToSample)) {
            // delimiter detected
            result <<= 4;
            sampledBits += 4;
//printf("   fill with zeros: value = 0x%" PRIx64 "\n", result);
         } 

//printf("bits to ignore on the right %d\n", shifts);
         shifts = (4 - (nbrOfBitsToSample % 4)) % 4;
         result <<= 64-sampledBits + shifts;
//printf("adjusted to the left: %" PRIx64 "\n", result);


         uint64_t mask = 0;
         for (int i=0; i< shifts; i++) {
            mask <<= 1;
            mask |= 1;
         }
         mask <<= 64-sampledBits;
//printf("   value = 0x%" PRIx64 " mask = 0x%" PRIx64 "\n", result,mask);
         if (result & mask) {
           Log::error("B-format: bit string too long");
           throw theBitValueSignal;
         }

         *value = result;
         return;
      }
      Log::error("B-format: field empty");
      throw theBitValueSignal;
   }


   void GetHelper::readB123(uint64_t * value, int nbrOfBitsToSample,
                            const int base) {
      int sampledBits = 0;
      int c;
      int shifts;
      uint64_t result;
      char maxDigit = '1';;
      if (base == 1) {
          maxDigit = '1';
      } else if (base == 2) {
          maxDigit = '3';
      } else if (base == 3) {
          maxDigit = '7';
      } 
//printf("*** GetHelper::readB123()\n");
      result = 0;
//printf("GetHelper::readB123: maxDigit=%c base=%d nbrBitsToSample = %d width=%d\n",
//      maxDigit, base, nbrOfBitsToSample,getRemainingWidth());
          
      if (skipSpaces() == 0) {
         do {
            c = readChar();

            if (c  >= '0' && c <= maxDigit)  {
               result <<= base;
               result |= c - '0';
               sampledBits +=base;
            } else if (c < 0 || c == ' ') {
               // do nothing - is treated in while condition
               // we need this empty clause to avoid the else-clause
               // in this case
            } else {
               Log::error("B-format: illegal character (0x%x)",c);
               discardRemaining();
               throw theBitValueSignal;
            }
         } while ((c >= '0' && c <= maxDigit)  &&
          sampledBits < nbrOfBitsToSample && getRemainingWidth() > 0);

         if (skipSpaces() == 0) {
           c = readChar();
           if (c>='0' && c <=maxDigit) {
              Log::error("B-format: bit string too long");
           } else {
              Log::error("B-format: illegal character (0x%x)",c);
           }
           discardRemaining();
           throw theBitValueSignal;
         }
         
         while (sampledBits > 0 && (sampledBits < nbrOfBitsToSample)) {
            // delimiter detected
            result <<= base;
            sampledBits += base;
//printf("   fill with zeros: value = 0x%" PRIx64 "\n", result);
         } 

         result <<= 64-sampledBits;
//printf("adjust to the left: %" PRIx64 "\n", result);

         shifts = (base - (nbrOfBitsToSample % base)) % base;
//printf("bits to ignore on the right %d\n", shifts);

         uint64_t mask = 0;
         for (int i=0; i< shifts; i++) {
            mask <<= 1;
            mask |= 1;
         }
         mask <<= 64-sampledBits;
//printf("   value = 0x%" PRIx64 " mask = 0x%" PRIx64 "\n", result,mask);
         if (result & mask) {
           Log::error("B-format: bit string too long");
           throw theBitValueSignal;
         }

         *value = result;
         return;
      }

      Log::error("B-format: field empty");
      throw theBitValueSignal;
   }



   void GetHelper::readFixedByF(Fixed63 * f, int d) {
      bool goOn = true;
      bool decimalPointFound = false;
      int digitsProcessed  = 0;
      int postPointDigits = 0;
      int c;
      int sign = 1;
      int scale = 0;
      const Fixed63 ten((Fixed63::Fixed63_t)10);
      const Fixed63 zero;
      Fixed63 result ((Fixed63::Fixed63_t)0);

      if (skipSpaces() == 0) {
         try {
            if (readString("-") == 0) {
               sign = -1;
            }

            while (goOn && width > 0) {
               c = readChar();

               if (isdigit(c)) {
                  result *= ten;
                  result += Fixed63((Fixed63::Fixed63_t)(c - '0'));
                  digitsProcessed ++;

                  if (decimalPointFound) {
                     postPointDigits ++;
                  }
               } else if (c == '.' && decimalPointFound == false) {
                  decimalPointFound = true;
               } else if (c < 0 || c == ' ') {
                  // eor
                  goOn = false;
               } else {
                  // other character
                  discardRemaining();
                  throw theFixedValueSignal;
               }
            }

            if (skipSpaces() == 0) {
               discardRemaining();
               Log::info("F: illegal character in field");
               throw theFixedValueSignal;
            }

            // if d > 0 : the value must be divided    d times by 10

            if (!decimalPointFound) {
               // no decimal point found, maybe decimals
	       // are specified in format
               scale = d;
            } else {
               // decimal point found
               scale = postPointDigits;
            }

            if (scale > 0) {
               // discard decimals but leave the first survive
               // for rounding
               while (scale > 1) {
                  result /= ten;
                  scale --;
               }

	       // perform rounding
               result += Fixed63((Fixed63::Fixed63_t)(5));
               result /= ten;
            }

            result *= Fixed63((Fixed63::Fixed63_t)(sign));
            *f = result;
            return;
         } catch (ArithmeticSignal s) {
            // arithmetic error
            // *f = zero;
            discardRemaining();
            throw theFixedRangeSignal;
         } catch (TerminateRequestSignal s) {
            throw;
         }
      }

      Log::error("F-format: no data in field");
      throw theFixedValueSignal;
   }

   void GetHelper::readFloatByF(Float<52> * value, int d) {
      int sign = 1;
      double x;

      if (skipSpaces() == 0) {
        if (readString("-") == 0) {
          sign = -1;
        }
        if (readMantissa(&x, width, d) > 0) {
           if (skipSpaces() == 0) {
              discardRemaining();
              Log::info("F: illegal character in field");
              throw theFixedValueSignal;
           }
           value->x = x * sign;
           return;
        } else if (skipSpaces() == 0) {
           discardRemaining();
           Log::info("F: illegal character in field");
           throw theFixedValueSignal;
        } 
     }

     Log::error("F-format: no data in field");
     throw theFixedValueSignal;
     // specification changed
     // no data in field --> this is 0 be specification
     //value->x = 0.0;
   }

   double GetHelper::pow10(int exp) {
      // we regard the binary representation of exp
      // each bit corresponts to 10^(2*i) = (10^2)^i
      // let the compiler create the possible base factors
      static double powersOf10[] = {10, 100, 1.e4, 1.e8, 1.e16, 1.e32,
                                    1.e64, 1.e128, 1.e256
                                   };
      double result = 1.0;
      bool invert=false;
      double * expFactor;
      if (exp < 0) {
         exp = -exp;
         invert = true;
      }
      if (exp > DBL_MAX_10_EXP) {
         result = INFINITY;
      } else {
         for (expFactor = powersOf10; exp != 0; expFactor += 1, exp >>= 1) {
            if (exp & 0x01) {
               result *= *expFactor;
            }
         }
      }
      if (invert) {
         result = 1.0/result;
      }
      return result;
   }

   void GetHelper::readFloatByE(Float<52> * value) {
      int sign = 1;
      int expSign = 0;
      uint64_t expValue;
      int ch; 
      double x;

      if (skipSpaces() == 0) {
        if (readString("-") == 0) {
          sign = -1;
        }
        if (readMantissa(&x, width, 0) > 0) {
           // need at least 2 characters eg. E1
           if (width > 2 && readString("E") == 0) {
              // treat exponent
              ch = readChar();
              if (ch > 0) {
                switch (ch) {
                   case '+': expSign = 1;
                             ch = readChar();
                             break;
                   case '-': expSign = -1;
                             ch = readChar();
                             break;
                }
                if (isdigit(ch)) {
                  source->unGetChar(ch);
                  width ++;
                }
                if (ch < 0) {
                   discardRemaining();
                   Log::error("E-format: no exponent found");
                   throw theExpValueSignal;
                }                   
                if (readInteger(&expValue, width) == 0) {
                   if (expSign != 0) {
                      source->unGetChar(ch);
                      width ++;
                   }
                   discardRemaining();
                   Log::error("E-format: no exponent found");
                   throw theExpValueSignal;
                }
                if (skipSpaces() == 0) {
                   discardRemaining();
                   Log::info("E: illegal character in field");
                   throw theExpValueSignal;
                }

                if (expSign < 0) {
                   value->x = x * sign / pow10(expValue);
                } else {
                   value->x = x * sign * pow10(expValue);
                }
                return; 
              } 
           } else {
              if (skipSpaces() != 0) {
                 value->x = x * sign;
                 return;
              }
           }
        }
        discardRemaining();
        Log::info("E: illegal character in field");
        throw theExpValueSignal;
     }

     // no data in field 
     Log::error("E-format: no data in field");
     throw theExpValueSignal;
   }

}


