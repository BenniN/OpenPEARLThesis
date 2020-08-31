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
#ifndef PCA9685CHANNEL_H_INCLUDED
#define PCA9685CHANNEL_H_INCLUDED
/**
\file

\brief Basic system device for the PCA9685 I2C basic dation

*/

#include "SystemDationB.h"
#include "Fixed.h"
#include "Signals.h"
#include "PCA9685.h"

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

   class PCA9685Channel: public SystemDationB {

   private:
      int16_t channel;

      PCA9685 * provider;

      void internalDationOpen();
      void internalDationClose();

   public:
      /**
      constructor to create access to one cghannel of
      the 16 channels of the PCA9685 device

      \param provider reference to the PCA9685 object
      \param channel the channel

      \throws DationParamSignal in case of init failure

      */
      PCA9685Channel(PCA9685 * provider, int channel);

      /**
      Open the  dation

      \param openParam open parameters if given
      \param idf pointer to IDF-value if given
      \returns pointer to the SampleDationB object itself as working
               object in the user dation

      \throws OpenFailedSignal, if  dation is not closed and rst is not given
      */
      PCA9685Channel* dationOpen(const char* idf = 0, int openParam = 0);

      /**
      Close the sample basic dation

      \param closeParam close parameters if given
      */
      void dationClose(int closeParam = 0);

      /**
      read data from the device


      \param data points to the storage location of the data
      \param size denotes the number of bytes of the output data

      \throws DationParamSignal, in any  case
      */
      void dationRead(void * data, size_t size);

      /**
      send  a  value to the device

      The "onValue" is always 0, only the offValue is used here

      The offValue must be [0..4095]

      \param data points to the storage location of the data
      \param size denotes the number of bytes of the output data

      \throws DationParamSignal, in  case of illegal values
      */
      void dationWrite(void * data, size_t size);

      /**
      obtain the capabilities of the device

      This method returns :
            OUT

      does not return:
            IDF  PRM CAN NEW  OLD ANY INOUT IN

      \returns available commands of the device
      */
      int capabilities();
   };
   /** @} */
}
#endif
