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

\brief get-formatting of types BIT(x)

\author R. Mueller

This module contains the input formatting von BitStrings
for B1,B2,B3 and B4-format.
*/

#include <stdint.h>
//#include <inttypes.h>     // for PRI64-macros

#include "Source.h"
#include "BitString.h"
#include "Signals.h"
#include "GetBitString.h"

//using namespace std;
namespace pearlrt {
   static const int dataMask[] = {0x01, 0x03, 0x07, 0x0f};
   static const char hexChar[] = {'0', '1', '2', '3', '4', '5', '6', '7',
                                  '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

   void GetBits<1>::fromBit(Bits<1>::BitType & bits, int len, int width,
                          int base, Source & source) {

        BitString<64>::DataType b;
        GetBits<8>::fromBit(b, len, width, base, source);
// printf("as Bit(64): b: %" PRIx64 " len=%d, width=%d base=%d\n", b, len, width, base);
        bits = b >> (64-8);
   }

   void GetBits<2>::fromBit(Bits<2>::BitType& bits, int len, int width,
                          int base, Source & source) {
        BitString<64>::DataType b;
        GetBits<8>::fromBit(b, len, width, base, source);
        bits = b >> (64-16);
   }

   void GetBits<4>::fromBit(Bits<4>::BitType& bits, int len, int width,
                          int base, Source & source) {
        BitString<64>::DataType b;
        GetBits<8>::fromBit(b, len, width, base, source);
        bits = b >> (64-32);
   }


   void GetBits<8>::fromBit(Bits<8>::BitType& bits, int len, int width,
                          int base, Source & source) {

	uint64_t value;
	Fixed<31> w(width);
	GetHelper helper(w,&source);
        if (base < 4) {
	   helper.readB123(&value,len,base);
        } else {
           helper.readB4(&value,len);
        } 

        bits = value;
   }
}

