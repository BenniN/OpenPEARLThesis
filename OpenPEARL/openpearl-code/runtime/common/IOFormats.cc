/*
 [A "BSD license"]
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
\brief implementation of  I/O-Formats for PG-userdation and CONVERT

*/
#include "IOFormats.h"
#include "Sink.h"
#include "Source.h"
#include "Fixed.h"
#include "PutHelper.h"
#include "PutClock.h"
#include "GetClock.h"
#include "PutDuration.h"
#include "GetDuration.h"

namespace pearlrt {

   void IOFormats::setupIOFormats(Sink * _sink, Source * _source) {
      sink = _sink;
      source = _source;
   }

   void IOFormats::toA(void *s, size_t len, Fixed<31> w) {
      checkCapacity(w);
      RefCharacter rc;
      rc.setWork(s, len);
      PutHelper::doPutChar(w.x, &rc, sink);
   }

   void IOFormats::fromA(void *s, size_t len, Fixed<31> w) {
      checkCapacity(w);
      GetHelper helper(w, source);
      helper.setDelimiters(GetHelper::EndOfLine);

      RefCharacter rc;
      rc.setWork(s, len);

      helper.readCharacterByA(&rc);
   }

   void IOFormats::toFloatF(void *s, size_t index,
                            size_t len,
                            const Fixed<31> w,
                            const Fixed<31> d) {
      checkCapacity(w);

      if (len == 23) {
         Float<23> * f = (Float<23>*)s;
         f += index;
         PutFloat<23>::toF(*f, w, d, *sink);
      } else if (len == 52) {
         Float<52> * f = (Float<52>*)s;
         f += index;
         PutFloat<52>::toF(*f, w, d, *sink);
      } else {
         Log::error("unsupported length of float F-format (len=%zu)", len);
         throw theInternalDationSignal;
      }
   }

   void IOFormats::toFloatE(void *s, size_t index, size_t len,
                            const Fixed<31> w,
                            const Fixed<31> d,
                            const Fixed<31> significance,
 			    const int expSize) {
      checkCapacity(w);

      if (len == 23) {
         Float<23> * f = (Float<23>*)s;
         f += index;
         PutFloat<23>::toE(*f, w, d,significance, expSize, *sink);
      } else if (len == 52) {
         Float<52> * f = (Float<52>*)s;
         f += index;
         PutFloat<52>::toE(*f, w, d,significance, expSize, *sink);
      } else {
         Log::error("unsupported length of float E-format (len=%zu)", len);
         throw theInternalDationSignal;
      }
   }

   void IOFormats::fromFloatE(void *s, size_t index, size_t len,
                            const Fixed<31> w,
                            const Fixed<31> d,
                            const Fixed<31> significance,
 			    const int expSize) {
      checkCapacity(w);

      if (len == 23) {
         Float<23> * f = (Float<23>*)s;
         f += index;
         GetFloat<23>::fromE(*f, w, d,significance, *source);
      } else if (len == 52) {
         Float<52> * f = (Float<52>*)s;
         f += index;
         GetFloat<52>::fromE(*f, w, d,significance, *source);
      } else {
         Log::error("unsupported length of float E-format (len=%zu)", len);
         throw theInternalDationSignal;
      }
   }

   void IOFormats::fromFloatF(void *s, size_t index,
                              size_t len,
                              const Fixed<31> w,
                              const Fixed<31> d) {
      checkCapacity(w);

      if (len == 23) {
         Float<23> * f = (Float<23>*)s;
         f += index;
         GetFloat<23>::fromF(*f, w, d, *source);
      } else if (len == 52) {
         Float<52> * f = (Float<52>*)s;
         f += index;
         GetFloat<52>::fromF(*f, w, d, *source);
      } else {
         Log::error("unsupported length of float F-format (len=%zu)", len);
         throw theInternalDationSignal;
      }
   }

   void IOFormats::toFixedF(void *s, size_t index,
                            size_t len,
                            const Fixed<31> w,
                            const Fixed<31> d) {
      checkCapacity(w);

      if (len <= 7) {
         Fixed<7> * f = (Fixed<7>*)s;
         f += index;
         PutFixed<7>::toF(*f, w, d, *sink);
      } else if (len <= 15) {
         Fixed<15> * f = (Fixed<15>*)s;
         f += index;
         PutFixed<15>::toF(*f, w, d, *sink);
      } else if (len <= 31) {
         Fixed<31> * f = (Fixed<31>*)s;
         f += index;
         PutFixed<31>::toF(*f, w, d, *sink);
      } else if (len <= 63) {
         Fixed<63> * f = (Fixed<63>*)s;
         f += index;
         PutFixed<63>::toF(*f, w, d, *sink);
      } else {
         Log::error("unsupported length of fixed F-format (len=%zu)", len);
         throw theInternalDationSignal;
      }
   }

   void IOFormats::fromFixedF(void *s, size_t index,
                              size_t len,
                              const Fixed<31> w,
                              const Fixed<31> d) {
      checkCapacity(w);

      if (len <= 7) {
         Fixed<7> * f = (Fixed<7>*)s;
         f += index;
         GetFixed<7>::fromF(*f, w, d, *source);
      } else if (len <= 15) {
         Fixed<15> * f = (Fixed<15>*)s;
         f += index;
         GetFixed<15>::fromF(*f, w, d, *source);
      } else if (len <= 31) {
         Fixed<31> * f = (Fixed<31>*)s;
         f += index;
         GetFixed<31>::fromF(*f, w, d, *source);
      } else if (len <= 63) {
         Fixed<63> * f = (Fixed<63>*)s;
         f += index;
         GetFixed<63>::fromF(*f, w, d, *source);
      } else {
         Log::error("unsupported length of fixed F-format (len=%zu)", len);
         throw theInternalDationSignal;
      }
   }

   void IOFormats::toBit(void *s, size_t index,
                         size_t len, int base,
                         const Fixed<31> w) {
      checkCapacity(w);

      if (len <= 8) {
         BitString<8> * f = (BitString<8>*)s;
         f += index;
         PutBits<1>::toBit(f->x, len, w.x, base, *sink);
      } else if (len <= 16) {
         BitString<16> * f = (BitString<16>*)s;
         f += index;
         PutBits<2>::toBit(f->x, len, w.x, base, *sink);
      } else if (len <= 32) {
         BitString<32> * f = (BitString<32>*)s;
         f += index;
         PutBits<4>::toBit(f->x, len, w.x, base, *sink);
      } else if (len <= 64) {
         BitString<64> * f = (BitString<64>*)s;
         f += index;
         PutBits<8>::toBit(f->x, len, w.x, base, *sink);
      } else {
         Log::error("unsupported length of fixed B-format (len=%zu)",
		 (unsigned long) len);
         throw theInternalDationSignal;
      }
   }


   void IOFormats::fromBit(void *s, size_t index,
                           size_t len, int base,
                           const Fixed<31> w) {
      checkCapacity(w);

      if (len <= 8) {
	 BitString<8> *f = (BitString<8>*)s;
         f += index;
         GetBits<1>::fromBit(f->x, len, w.x, base, *source);
      } else if (len <= 16) {
	 BitString<16> *f = (BitString<16>*)s;
         f += index;
         GetBits<2>::fromBit(f->x, len, w.x, base, *source);
      } else if (len <= 32) {
	 BitString<32> *f = (BitString<32>*)s;
         f += index;
         GetBits<4>::fromBit(f->x, len, w.x, base, *source);
      } else if (len <= 64) {
	 BitString<64> *f = (BitString<64>*)s;
         f += index;
         GetBits<8>::fromBit(f->x, len, w.x, base, *source);
      } else {
         Log::error("unsupported length of fixed B-format (len=%zu)",
		 (unsigned long) len);
         throw theInternalDationSignal;
      }
   }

   void IOFormats::toT(const Clock f,
                       const Fixed<31> w,
                       const Fixed<31> d) {
      checkCapacity(w);
      PutClock::toT(f, w, d, *sink);
   }

   void IOFormats::fromT(Clock & f,
                         const Fixed<31> w,
                         const Fixed<31> d) {
      checkCapacity(w);
      GetClock::fromT(f, w, d, *source);
   }

   void IOFormats::toD(const Duration f,
                       const Fixed<31> w,
                       const Fixed<31> d) {
      checkCapacity(w);
      PutDuration::toD(f, w, d, *sink);
   }

   void IOFormats::fromD(Duration & f,
                         const Fixed<31> w,
                         const Fixed<31> d) {
      checkCapacity(w);
      GetDuration::fromD(f, w, d, *source);
   }


   int IOFormats::putDataFormat(TaskCommon * me, IODataEntry * dataEntry,
                                size_t index, size_t loopOffset,
                                IOFormatEntry * fmtEntry) {
      int returnValue = 0;

      switch (dataEntry->dataType.baseType) {
      default:
         printf("put unsupported baseType %d\n",
                dataEntry->dataType.baseType);
         printf("fmt entry: format=%d data=%p type=%d width=%d, datasize=%d\n",
                fmtEntry->format,
                dataEntry->dataPtr.inData,
                dataEntry->dataType.baseType,
                dataEntry->dataType.dataWidth,
                dataEntry->param1.numberOfElements);
         break;

      case IODataEntry::CHAR:
         if (fmtEntry->format == IOFormatEntry::A ||
             fmtEntry->format == IOFormatEntry::LIST) {
              toA((char*)(dataEntry->dataPtr.inData) + loopOffset,
                dataEntry->dataType.dataWidth,
                (Fixed<31>)(dataEntry->dataType.dataWidth));
         } else if (fmtEntry->format == IOFormatEntry::Aw) {
              toA((char*)(dataEntry->dataPtr.inData) + loopOffset,
                dataEntry->dataType.dataWidth,
                fmtEntry->fp1.f31);
         } else {
              Log::error("type mismatch in A format");
              throw theDationDatatypeSignal;
         }
         break;

      case IODataEntry::CHARSLICE:
         { int lwb,upb;
           lwb = dataEntry->param2.charSliceLimits.lwb.x - 1;
           upb = dataEntry->param2.charSliceLimits.upb.x - 1;

           if (lwb < 0 || lwb >= dataEntry->dataType.dataWidth) {
		Log::error("lwb (%d) out of range",lwb+1); 
                throw theCharacterIndexOutOfRangeSignal;
           }
           if (upb < 0 || upb >= dataEntry->dataType.dataWidth) {
		Log::error("upb (%d) out of range",upb+1); 
                throw theCharacterIndexOutOfRangeSignal;
           }
           if (upb < lwb) {
		Log::error("upb >= lwb violation (lwb:upb=%d:%d)",lwb+1,upb+1); 
                throw theCharacterIndexOutOfRangeSignal;
           }

           if (fmtEntry->format == IOFormatEntry::A ||
             fmtEntry->format == IOFormatEntry::LIST) {
              toA((char*)(dataEntry->dataPtr.inData) + loopOffset +  lwb,
                  upb-lwb+1,
                  (Fixed<31>)(upb-lwb +1));
           } else if (fmtEntry->format == IOFormatEntry::Aw) {
              toA((char*)(dataEntry->dataPtr.inData) + loopOffset + lwb,
                  upb-lwb+1,
                  fmtEntry->fp1.f31);
           } else {
              Log::error("type mismatch in A format");
              throw theDationDatatypeSignal;
           }
         }
         break;

      case IODataEntry::FIXED:
         if (fmtEntry->format == IOFormatEntry::F) {
            toFixedF((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                     dataEntry->dataType.dataWidth,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            int width=dataEntry->dataType.dataWidth;
            width = width/3.32+2;
            toFixedF((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                     dataEntry->dataType.dataWidth,
                     width);
         } else {
            Log::error("type mismatch in F format");
            throw theDationDatatypeSignal;
         }

         break;


      case IODataEntry::FLOAT:
         if (fmtEntry->format == IOFormatEntry::F) {
            toFloatF((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                     dataEntry->dataType.dataWidth,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::E2) {
            toFloatE((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                     dataEntry->dataType.dataWidth,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31,
                     fmtEntry->fp3.f31,
		     2);
         } else if (fmtEntry->format == IOFormatEntry::E3) {
            toFloatE((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                     dataEntry->dataType.dataWidth,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31,
                     fmtEntry->fp3.f31,
		     3);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            int width = dataEntry->dataType.dataWidth;
            width = width/3.32+3;
            toFloatE((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                     dataEntry->dataType.dataWidth,
                     width, width-7, width-6, 2);
         } else {
            Log::error("type mismatch in F format");
            throw theDationDatatypeSignal;
         }

         break;

      case IODataEntry::BIT:
         // treat B1, B2, B3, B4, B1w, B2w, B3w, B4w
      {
         int base, length;
         Fixed<31> width;

         switch (fmtEntry->format) {
         default:
            Log::error("type mismatch in B format");
            throw theDationDatatypeSignal;

         case IOFormatEntry::LIST:
         case IOFormatEntry::B1:
            base = 1;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base ;
            break;

         case IOFormatEntry::B1w:
            base = 1;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         
	case IOFormatEntry::B2:
            base = 2;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base ;
            break;

         case IOFormatEntry::B2w:
            base = 2;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         
	case IOFormatEntry::B3:
            base = 3;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base ;
            break;

         case IOFormatEntry::B3w:
            base = 3;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         
	case IOFormatEntry::B4:
            base = 4;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base;
            break;

         case IOFormatEntry::B4w:
            base = 4;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         }

         toBit((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
               length, base, width);
      }
      break;

      case IODataEntry::CLOCK:
         if (fmtEntry->format == IOFormatEntry::T) {
            toT(*((pearlrt::Clock*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            toT(*((pearlrt::Clock*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     8,0);
         } else {
            Log::error("type mismatch in T format");
            throw theDationDatatypeSignal;
         }

         break;


      case IODataEntry::DURATION:
         if (fmtEntry->format == IOFormatEntry::D) {
            toD(*((pearlrt::Duration*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            toD(*((pearlrt::Duration*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     20,0);
         } else {
            Log::error("type mismatch in D format");
            throw theDationDatatypeSignal;
         }

         break;


      case IODataEntry::InduceData:
         Signal::throwSignalByRst(fmtEntry->fp1.intValue);
         break;
      }

      return returnValue;

   }

   int IOFormats::getDataFormat(TaskCommon * me, IODataEntry * dataEntry,
                                size_t index, size_t loopOffset, IOFormatEntry * fmtEntry) {
      int returnValue = 0;

      switch (dataEntry->dataType.baseType) {
      default:
         printf("get unsupported format %d\n", fmtEntry->format);
         printf("fmt entry: format=%d data=%p type=%d width=%d, datasize=%d\n",
                fmtEntry->format,
                dataEntry->dataPtr.inData,
                dataEntry->dataType.baseType,
                dataEntry->dataType.dataWidth,
                dataEntry->param1.numberOfElements);
         break;

      case IODataEntry::CHAR:
         if (fmtEntry->format == IOFormatEntry::A ||
             fmtEntry->format == IOFormatEntry::LIST) {
            fromA((char*)(dataEntry->dataPtr.inData) + loopOffset,
                  dataEntry->dataType.dataWidth,
                  (Fixed<31>)(dataEntry->dataType.dataWidth));
         } else if (fmtEntry->format == IOFormatEntry::Aw) {
            fromA((char*)(dataEntry->dataPtr.inData) + loopOffset,
                  dataEntry->dataType.dataWidth,
                  fmtEntry->fp1.f31);
         } else {
            Log::error("type mismatch in A format");
            throw theDationDatatypeSignal;
         }

         break;

      case IODataEntry::FIXED:
         if (fmtEntry->format == IOFormatEntry::F) {
            fromFixedF((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                       dataEntry->dataType.dataWidth,
                       fmtEntry->fp1.f31,
                       fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            int width=dataEntry->dataType.dataWidth;
            width = width/3.32+2;
            fromFixedF((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                       dataEntry->dataType.dataWidth,
                       width);
         } else {
            Log::error("type mismatch in F format");
            throw theDationDatatypeSignal;
         }

         break;


      case IODataEntry::FLOAT:
         if (fmtEntry->format == IOFormatEntry::F) {
            fromFloatF((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                       dataEntry->dataType.dataWidth,
                       fmtEntry->fp1.f31,
                       fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::E2) {
            fromFloatE((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                       dataEntry->dataType.dataWidth,
                       fmtEntry->fp1.f31,
                       fmtEntry->fp2.f31,
                       fmtEntry->fp3.f31,2);
         } else if (fmtEntry->format == IOFormatEntry::E3) {
            fromFloatE((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                       dataEntry->dataType.dataWidth,
                       fmtEntry->fp1.f31,
                       fmtEntry->fp2.f31,
                       fmtEntry->fp3.f31,3);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            int width = dataEntry->dataType.dataWidth;
            width = width/3.32+3;
            fromFloatE((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
                       dataEntry->dataType.dataWidth,
                     width, width-7, width-6, 2);
         } else {
            Log::error("type mismatch in F format");
            throw theDationDatatypeSignal;
         }

         break;

      
      case IODataEntry::BIT:
         // treat B1, B2, B3, B4, B1w, B2w, B3w, B4w
      {
         int base, length;
         Fixed<31> width;

         switch (fmtEntry->format) {
         default:
            Log::error("type mismatch in B format");
            throw theDationDatatypeSignal;

         case IOFormatEntry::LIST:
         case IOFormatEntry::B1:
            base = 1;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base ;
            break;

         case IOFormatEntry::B1w:
            base = 1;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         
	case IOFormatEntry::B2:
            base = 2;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base ;
            break;

         case IOFormatEntry::B2w:
            base = 2;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         
	case IOFormatEntry::B3:
            base = 3;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base ;
            break;

         case IOFormatEntry::B3w:
            base = 3;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         
	case IOFormatEntry::B4:
            base = 4;
            length = dataEntry->dataType.dataWidth;
            width = (length + base - 1) / base;
            break;

         case IOFormatEntry::B4w:
            base = 4;
            length = dataEntry->dataType.dataWidth;
            width = fmtEntry->fp1.f31;
            break;
         }

         fromBit((char*)(dataEntry->dataPtr.inData) + loopOffset, index,
               length, base, width);
      }
      break;

      case IODataEntry::CLOCK:
         if (fmtEntry->format == IOFormatEntry::T) {
            fromT(*((pearlrt::Clock*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            fromT(*((pearlrt::Clock*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     8,0);
         } else {
            Log::error("type mismatch in T format");
            throw theDationDatatypeSignal;
         }

         break;


      case IODataEntry::DURATION:
         if (fmtEntry->format == IOFormatEntry::D) {
            fromD(*((pearlrt::Duration*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     fmtEntry->fp1.f31,
                     fmtEntry->fp2.f31);
         } else if (fmtEntry->format == IOFormatEntry::LIST) {
            fromD(*((pearlrt::Duration*)
		   ((char*)(dataEntry->dataPtr.inData) + loopOffset)+ index) ,
                     20,0);
         } else {
            Log::error("type mismatch in D format");
            throw theDationDatatypeSignal;
         }
         break;


      case IODataEntry::InduceData:
         Signal::throwSignalByRst(fmtEntry->fp1.intValue);
         break;
      }

      return returnValue;

   }
}


