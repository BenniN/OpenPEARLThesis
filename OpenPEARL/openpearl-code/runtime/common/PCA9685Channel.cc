/*
 [A "BSD license"]
 Copyright (c) 2019      Rainer Mueller
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


#include "Dation.h"
#include "PCA9685Channel.h"
#include "PCA9685.h"
#include "Log.h"
#include "Signals.h"
#include "Fixed.h"
#include <iostream>

/**
 \brief Implementation of the PCA9685 i2c-device  basic  systemdation


*/
namespace pearlrt {

   PCA9685Channel::PCA9685Channel(PCA9685 * provider, int channel) {

      dationStatus = CLOSED;
      this->provider = provider;


      if (channel < 0 || channel > 15) {
         Log::error("PCA9685Channel: illegal channel (%d)", channel);
         throw theDationParamSignal;
      }

      this->channel = channel;

      // leave the device unchanged
   }

   PCA9685Channel* PCA9685Channel::dationOpen(const char * idf, int params) {

      if (idf) {
         Log::error("PCA9685Channel: no IDF allowed");
         throw theDationParamSignal;
      }

      if (params & ~(RST | IN | OUT | INOUT)) {
         Log::error("PCA9685Channel: only RST allowed");
         throw theDationParamSignal;
      }

      if (dationStatus != CLOSED) {
         Log::error("PCA9685Channel: Dation already open");
         throw theOpenFailedSignal;
      }


      dationStatus = OPENED;
      return this;
   }

   void PCA9685Channel::dationClose(int params) {

      if (dationStatus != OPENED) {
         Log::error("PCA9685Channel: Dation not open");
         throw theDationNotOpenSignal;
      }

      if (params & ~(RST | IN | OUT | INOUT)) {
         Log::error("PCA9685Channel: only RST allowed");
         throw theDationParamSignal;
      }

      dationStatus = CLOSED;
   }

   void PCA9685Channel::dationWrite(void* data, size_t size) {
      int16_t offValue;

      //check size of parameter!
      // it is expected that a Fixed<15> object is passed
      // with a maximum of 16 bits. This fits into 2 byte.
      // Therefore size must be 2
      if (size != sizeof(Fixed<15>)) {
         Log::error("PCA9685Channel: Fixed<15> expected (got %d byte data)", (int)size);
         throw theDationParamSignal;
      }

      if (dationStatus != OPENED) {
         Log::error("PCA9685Channel: Dation not open");
         throw theDationNotOpenSignal;
      }

      offValue = *(int16_t*)data;
      provider->writeChannel(channel, offValue);

      return;
   }

   void PCA9685Channel::dationRead(void* data, size_t size) {
      Log::error("PCA9685Channel: no read supported");
      throw theInternalDationSignal;


   }

   int PCA9685Channel::capabilities() {
      int cap =  OUT;
      return cap;
   }
}
