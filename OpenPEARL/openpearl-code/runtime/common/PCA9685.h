/*
 [A "BSD license"]
 Copyright (c) 2019 Rainer Mueller
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
#ifndef PCA9685PROVIDER_H_INCLUDED
#define PCA9685PROVIDER_H_INCLUDED
/**
\file

\brief Basic system device for the PCA9685 I2C basic dation

*/

#include "SystemDationB.h"
#include "Fixed.h"
#include "Signals.h"
#include "I2CProvider.h"

#include <stdint.h>

namespace pearlrt {
   /**
   \addtogroup io_common_drivers
   @{
   */

   /**
   \brief Basic system device for an i2c element pca9685  basic dation

      This device works only together with PCA9685Channel, which allows
      single channels to become set.
      The device PCA9685 treats common stuff for all channels, like
      ic bus adress and prescler.

   */

   class PCA9685 {

   private:
      int16_t addr;
      int16_t prescaler;

      I2CProvider * provider;

   public:
      /**
      constructor to create the bit group and set the
      bits to output direction

      \param provider reference to the i2cbus object
      \param addr the i2c bus adress

      \throws DationParamSignal in case of init failure

      */
      PCA9685(I2CProvider * provider, int addr, int prescaler);

      /**
      send  a  value to the device

      if the value is less or equal 0, the channel is set off

      if the value is larger than 4095, the channel ist set ON

      \param channel denotes the desired channel
      \param offValue denotes the time slot to switch the channel off

      */
      void writeChannel(int channel, int offValue);
   };
   /** @} */
}
#endif
