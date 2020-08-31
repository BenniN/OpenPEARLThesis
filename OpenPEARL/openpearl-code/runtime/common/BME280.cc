/*
 [A "BSD license"]
 Copyright (c) 2016      Rainer Mueller
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

#include "BME280.h"
#include "I2CProvider.h"
#include "Dation.h"
#include "BME280.h"
#include "Log.h"
#include "Signals.h"
#include "Fixed.h"
#include "Task.h"
#include <iostream>

#include "bosch/bme280.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <fcntl.h>

namespace pearlrt {
extern "C" {
static int8_t user_i2c_read(void * provider, uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len)
{
  ((I2CProvider*)provider)->writeData(id, 1, &reg_addr);
  ((I2CProvider*)provider)->readData(id,len,data);
  return 0;
}

static void user_delay_ms(uint32_t period)
{
  if ( Task::delayUs(period*1000) ) {
      printf("interrupted in delayUs\n");
  }
}

static int8_t user_i2c_write(void* provider, uint8_t id, uint8_t reg_addr, uint8_t *data, uint16_t len)
{
  int8_t buf[256];
  if (len > 255) {
     Log::error("BME280 buffer too small");
     throw theInternalDationSignal;
  }
  buf[0] = reg_addr;
  memcpy(buf +1, data, len);
  ((I2CProvider*)provider)->writeData(id, len+1, (uint8_t*)buf);
  return 0;
}



};


   BME280::BME280(I2CProvider * provider, int addr,
                  int tempOs, int pressOs, int humOs, int iir,
                  int tSleep) {
      static const int sleepUs[] = {500, 62500, 125000,
                                    250000, 500000, 1000000, 10000, 20000};
      struct bme280_dev *bme = (struct bme280_dev*)&dev;

      dationStatus = CLOSED;
printf("bme280_dev %d bytes\n", sizeof(struct bme280_dev));
printf("fake dev %d bytes\n", sizeof(dev));
//#if sizeof(dev) < sizeof(struct bme280_dev)
//   #error "faked struct bme280_dev in BME280.h is too small"
//#endif
      if (addr < 0x76 || addr > 0x77) {
         Log::error("BME280: illegal adress (%x)", addr);
         throw theDationParamSignal;
      }

  //    this->addr = addr;
  bme->dev_id = addr;    //BME280_I2C_ADDR_PRIM;
  bme->intf = BME280_I2C_INTF;
  bme->read = user_i2c_read;
  bme->write = user_i2c_write;
  bme->delay_ms = user_delay_ms;
  bme->provider = provider;

  bme->settings.osr_h = humOs;
  bme->settings.osr_p = pressOs;
  bme->settings.osr_t = tempOs;
  bme->settings.filter = iir;

  selectedSensors = 0;
  tMeasurement = 1250;  // offset 1.25ms
  if (tempOs) {
      tMeasurement += (1<<tempOs) * 2300;
      selectedSensors |= BME280_OSR_TEMP_SEL;
  }
  if (pressOs) {
      tMeasurement += (1<<pressOs) * 2300 + 575;
      selectedSensors |= BME280_OSR_PRESS_SEL;
  }
  if (humOs) {
      tMeasurement += (1<<humOs) * 2300 + 575;
      selectedSensors |= BME280_OSR_HUM_SEL;
  }

  printf("tMeasurement=%d us \n", tMeasurement);
  if (tSleep == 0) {
    printf("forced mode\n");
  } else {
    printf("tSleep=%d us\n",  sleepUs[tSleep-1]);
   }
  }

   BME280* BME280::dationOpen(const char * idf, int params) {
      int8_t rslt = BME280_OK;

      struct bme280_dev *bme = (struct bme280_dev*)&dev;

      if (idf) {
         Log::error("BME280: no IDF allowed");
         throw theDationParamSignal;
      }

      if (params & ~(RST | IN | OUT | INOUT)) {
         Log::error("BME280: only RST allowed");
         throw theDationParamSignal;
      }

      if (dationStatus != CLOSED) {
         Log::error("BME280: Dation already open");
         throw theOpenFailedSignal;
      }



      try {
          rslt = bme280_init((struct bme280_dev*)&dev);
      } catch (WritingFailedSignal s) {
         Log::error("BME280: Dation not ready");
         throw theOpenFailedSignal;
      }
      if (rslt != BME280_OK) {
         Log::error("BME280: Dation not ready");
         throw theOpenFailedSignal;
      }

      rslt = bme280_set_sensor_settings(selectedSensors, bme);

      dationStatus = OPENED;
      return this;
   }

   void BME280::dationClose(int params) {

      if (dationStatus != OPENED) {
         Log::error("BME280: Dation not open");
         throw theDationNotOpenSignal;
      }

      if (params & ~(RST | IN | OUT | INOUT)) {
         Log::error("BME280: only RST allowed");
         throw theDationParamSignal;
      }

      dationStatus = CLOSED;
   }

   void BME280::dationWrite(void* data, size_t size) {
      Log::error("BME280: no write supported");
      throw theInternalDationSignal;
   }

   void BME280::dationRead(void* data, size_t size) {
      struct bme280_dev *bme = (struct bme280_dev*) dev;
      int8_t rslt;
      struct bme280_data comp_data;
      Fixed<31> * result = (Fixed<31>*) data;

      //check size of parameter!
      // it is expected that a Fixed<15> object is passed
      // with a maximum of 16 bits. This fits into 2 byte.
      // Therefore size must be 2

      if (size != 3*sizeof(Fixed<31>)) {
         Log::error("BME280: 3xFixed<31> expected (got %d byte data)", (int)size);
         throw theDationParamSignal;
      }

      if (dationStatus != OPENED) {
         Log::error("BME280: Dation not open");
         throw theDationNotOpenSignal;
      }


      rslt = bme280_set_sensor_mode(BME280_FORCED_MODE, bme);
      /* Wait for the measurement to complete and print data @25Hz */
      bme->delay_ms(40);
      rslt = bme280_get_sensor_data(selectedSensors, &comp_data, bme);
      *result++ = Fixed<31>(comp_data.temperature);
      *result++ = Fixed<31>(comp_data.pressure);
      *result   = Fixed<31>(comp_data.humidity);
   }

   int BME280::capabilities() {
      int cap =  IN;
      return cap;
   }
}



