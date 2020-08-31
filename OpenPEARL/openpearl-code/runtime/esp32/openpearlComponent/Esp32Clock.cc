/*
 [The "BSD license"]
 Copyright (c) 2015 Rainer Mueller
 Copyright (c) 2018 Michael Kotzjan
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
\brief Implementation of clock source for the ESP32 board

*/

#include "Log.h"
#include <time.h>
//#include <cstdarg>
#include "FreeRTOS.h"
#include "timers.h"
#include "time_addons.h"
#include "Esp32Clock.h"
#include "FreeRTOSClock.h"

#include "esp_freertos_hooks.h"

static void (*tickHook)(void) = pearlrt::FreeRTOSClock::tick;

extern "C" {
   /**
   vApplicationTickHook

   This function is called by FreeRTOS in each timer tick.
   This implementation performs the timeout operation for the
   tick based timers in the ESP32 port of OpenPEARL

   The concrete operations are set when the concrete time base is
   selected. This is done in the ctor of the class Esp32Clock.
   */
#if 0
   void vApplicationTickHook(void) {
      if (tickHook) {
         // invoke the current tick hook function if one is set
         (*tickHook)();
      }
   }
#endif

}

namespace pearlrt {

   bool Esp32Clock::clockSelected = false;

   bool Esp32Clock::isClockSelected() {
      return clockSelected;
   }

   Esp32Clock::Esp32Clock(const int typeOfClock) {
      //uint64_t now;
      static const struct tm  defaultDate = {0, 0, 0,	// sec, min, hour,
                1, 0, 2016 - 1900,			// mday, mon, year
                5, 0, 0					// wday, yday, isdst
      }; // Fr 1.1.2016 0:00:00

      if (clockSelected) {
         Log::error("Esp32Clock: already a clock source selected");
         throw 0;
         //throw theInternalConfigurationSignal;
      }

      switch (typeOfClock) {
      default:
         Log::error("Esp32Clock: Illegal selector %d", typeOfClock);
         throw 0;

      //throw theInternalConfigurationSignal;

      case 0: // only systick
         // no absolute time from RTC desired

         // lets assume to be midnight of 1.1.2016 right now
         FreeRTOSClock::set(&defaultDate);

         // tell time.c to take the current time from this function
         // and set the tickHook function pointer to the according method
         tickHook = FreeRTOSClock::tick;
         FreeRTOSClock::registerTimeBase();
         esp_register_freertos_tick_hook(tickHook);
         break;
      }

      clockSelected = true;
   }
}
