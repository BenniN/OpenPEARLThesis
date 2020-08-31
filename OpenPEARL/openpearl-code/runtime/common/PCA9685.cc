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
#include "PCA9685.h"
#include "Task.h"
#include "Log.h"
#include "Signals.h"
#include "Fixed.h"
#include <iostream>

/**
 \brief Implementation of the PCA9685 i2c-device  basic  systemdation


*/
namespace pearlrt {

   PCA9685::PCA9685(I2CProvider * provider, int addr, int prescaler) {
      static uint8_t defaultValue[] = {0, 0x20, 0x04};
      static uint8_t sleepMode[] = {0, 0x30};
      static uint8_t setPrescaler[] = {0xfe, 0 };

      this->provider = provider;

      if (addr < 0x40 || addr > 0x7f) {
         Log::error("PCA9685: illegal addres (%x)", addr);
         throw theDationParamSignal;
      }

      if (prescaler < 3 || prescaler > 255) {
         Log::error("PCA9685: Prescaler out of range");
         throw theOpenFailedSignal;
      }

      this->addr = addr;
      this->prescaler = prescaler;

      setPrescaler[1] = prescaler;

      // set mode 1 register to 0, which is the default
      //   with auto increment enabled and ALL_CALL disabled
      // set mode 2 register to 0x04m, which is the default value
      // set register 0xfe to the prescaler value
      // and switch back to default read register
      try {

         provider->writeData(addr, 2, sleepMode);
//printf("send sleep: %02x %02x\n", sleepMode[0], sleepMode[1]);
         provider->writeData(addr, 2, setPrescaler);
//printf("send prescaler: %02x %02x\n", setPrescaler[0], setPrescaler[1]);
         provider->writeData(addr, 3, defaultValue);
//printf("send default: %02x %02x %02x\n",
//   defaultValue[0], defaultValue[1], defaultValue[2]);
         Task::delayUs(500); // need 500us delay before oscillator is stable
      } catch (WritingFailedSignal s) {
         Log::error("PCA9685: Dation not ready");
         throw theInternalDationSignal;
      }

   }

   void PCA9685::writeChannel(int channel, int offValue) {

      uint8_t setChannel[5];

      if (offValue < 0) {
         offValue = 0;
      }

      setChannel[0] = 0x06 + channel * 4;
      setChannel[1] = 0;
      setChannel[3] = offValue & 0x0ff;
      setChannel[4] = (offValue >> 8) & 0x0f;


      // only the low 4 bit of the high are allowed
      // and send low byte first
      if (offValue <= 4095) {
         setChannel[2] = 0x0;  // clear always on
      } else {
         setChannel[2] = 0x10;   // always on
      }

      provider->writeData(addr, 5, setChannel);
//printf("write channel: %02x %02x %02x %02x %02x\n",
//setChannel[0],setChannel[1],setChannel[2],setChannel[3],setChannel[4]);
   }

}
