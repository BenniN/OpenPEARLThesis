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
#ifndef BME280_H_INCLUDED
#define BME280_H_INCLUDED
/**
\file

\brief Basic system device for the BM280 I2C basic dation

*/

#include "SystemDationB.h"
#include "Fixed.h"
#include "Signals.h"
#include "I2CProvider.h"
//#include "bosch/bme280.h"

#include <stdint.h>

namespace pearlrt {
   /**
   \addtogroup io_common_drivers
   @{
   */

   /**
   \brief Basic system device for an i2c element bme280  basic dation

      This device provides a simple input of a struct with
      current temperature, pressure and humidity

   */

   class BME280: public SystemDationB {

   private:
      // int16_t addr;
      //      I2CProvider * provider;
      //struct bme280_dev dev;
      int dev[20];
      uint8_t selectedSensors;  // bitmap of selected sensor components 

      int tMeasurement;     // measurement time accoring oversampling in us
      void internalDationOpen();
      void internalDationClose();

      int8_t read_sensor_data_forced_mode();

   public:
      /**
      The BME280 sensor may be used in different scenarios. The selection 
      of suitable filter, oversampling and sleep times deside about
      accuracy and response time. Some measurements may be switched off
      if they are not required.
      
      The time for a measurement depends on the oversampling settings.
      This time is calculated in the driver. In forced mode, this time
      is injected between start on conversion and readinf the data.
     
      \param provider reference to the i2cbus object
      \param addr the i2c bus adress
      \param tempOs oversampling for temperature measurement<br>
                    0: no measurement<br>
		    1: each measurement
		    2: 2x oversampling
		    3: 4x oversampling
		    4: 8x oversampling
		    5: 16x oversampling
      \param pressOs oversampling for pressure measurement<br>
                    0: no measurement<br>
		    1: each measurement<br>
		    2: 2x oversampling<br>
		    3: 4x oversampling<br>
		    4: 8x oversampling<br>
		    5: 16x oversampling
      \param humOs oversampling for humidity measurement<br>
                    0: no measurement<br>
		    1: each measurement<br>
		    2: 2x oversampling<br>
		    3: 4x oversampling<br>
		    4: 8x oversampling<br>
		    5: 16x oversampling
      \param iir   IIR filter setting<br>
                    0 : off<br>
                    1 : 2 measurements<br>
                    2 : 4 measurements<br>
                    3 : 8 measurements<br>
                    4 : 16 measurements
      \param tSleep  Sleep time between measurements<br>
                    0: forced mode<br>
                    1: 0.5ms <br>
                    2: 62.5ms <br>
                    3: 125ms <br>
                    4: 250ms <br>
                    5: 500ms <br>
                    6: 1000ms <br>
                    7: 10ms <br>
                    8: 20ms 

      \throws DationParamSignal in case of init failure

      */
      BME280(I2CProvider * provider, int addr, 
             int tempOs, int pressOs, int humOs, int iir,
             int tSleep);

      /**
      Open the  dation

      \param openParam open parameters if given
      \param idf pointer to IDF-value if given
      \returns pointer to the SampleDationB object itself as working
               object in the user dation

      \throws OpenFailedSignal, if  dation is not closed and rst is not given
      */
      BME280* dationOpen(const char* idf = 0, int openParam = 0);

      /**
      Close the sample basic dation

      \param closeParam close parameters if given
      */
      void dationClose(int closeParam = 0);

      /**
      read  a struct value from the device. The value is the temperature
      in 100th of egree. The pressure is in Pascal and the 
      himidty in ....


      \param data points to the storage location of the data
      \param size denotes the number of bytes of the output data

      \throws DationParamSignal, if size is not matching
      \throws DationNotOpenSignal, if  dation is not opened
      */
      void dationRead(void * data, size_t size);

      /**
      send  a  value to the device
      \param data points to the storage location of the data
      \param size denotes the number of bytes of the output data

      \param data points to the storage location of the data
      \param size denotes the number of bytes of the output data

      \throws DationParamSignal, in any case
      */
      void dationWrite(void * data, size_t size);

      /**
      obtain the capabilities of the device

      This method returns :
            IN

      does not return:
            IDF  PRM CAN NEW  OLD ANY INOUT OUT

      \returns available commands of the device
      */
      int capabilities();
   };
   /** @} */
}
#endif

