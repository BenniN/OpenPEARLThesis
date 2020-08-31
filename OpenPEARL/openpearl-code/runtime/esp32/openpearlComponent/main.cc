/*
 [The "BSD license"]
 Copyright (c) 2013-2014 Florian Mahlecke
 Copyright (c) 2014 Rainer Mueller
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
	\brief OpenPEARL entry

	system start

\mainpage

The OpenPEARL system for the ESP board is based on the
FreeRTOS scheduler.

Most of the classes are plattform independent.
This documentation consists of the plattform dependent and plattform
independent parts.


*/


//#include <debug_frmwrk.h>


/* Scheduler includes. */
#include <FreeRTOS.h>
#include <queue.h>
#include <task.h>
#include <semphr.h>

#include "driver/gpio.h"

#include "GenericTask.h"
#include "Signals.h"
#include "TaskList.h"
#include "Task.h"
//#include "Post.h"
#include "Log.h"
#include "Clock.h"
//#include "chip.h"
#include "service.h"
#include "Esp32Clock.h"

// read options from menuconfig
#include "../../configuration/include/autoconf.h"

static void testException() {
   try {
      printf("check exceptions\n");
      throw 2;
      printf("expection not treated\n");
   } catch (int e) {
      printf("got int exception %d\n", e);
   }
}
extern "C" {
   extern int _write(int fd, char * ptr, int len);
   void blink_task(void *pvParameter) {
      gpio_num_t blink_gpio =  GPIO_NUM_2;
      printf("task:blink started\n");


      testException();

      /* Configure the IOMUX register for pad BLINK_GPIO (some pads are
         muxed to GPIO on reset already, but some default to other
         functions and need to be switched to GPIO. Consult the
         Technical Reference for a list of pads and their default
         functions.)
      */
      gpio_pad_select_gpio(blink_gpio);
      /* Set the GPIO as a push/pull output */
      gpio_set_direction(blink_gpio, GPIO_MODE_OUTPUT);

      while (1) {
         /* Blink off (output low) */
         gpio_set_level(blink_gpio, 0);
         vTaskDelay(1000 / portTICK_PERIOD_MS);
         /* Blink on (output high) */
         gpio_set_level(blink_gpio, 1);
         vTaskDelay(1000 / portTICK_PERIOD_MS);
      }
   }
};

using namespace pearlrt;
/*-----------------------------------------------------------*/
/**
main entry to the OpenPearl application

Show the list of defined tasks, activate all "MAIN"-tasks
and start FreeRTOS-scheduler

\returns nothing - will never return!
*/
extern "C" {
   void  app_main() {
      char line[40];

      printf("OpenPEARL started \n");

      // test for clock initialization

      if (!Esp32Clock::isClockSelected()) {
         printf("set default clock source\n");
         Esp32Clock dummy(0);  // the object may be discarded immediately
      }


      printf("set log level \n");
      Log::getInstance()->setLevel(0x0c);
//      Log::getInstance()->setLevel(0x0f);

      // start background service task
      init_service();

      /*
       * This task starts all PEARL90 main tasks, afterwards the
       * task suspends itself until another task resume it
       */
      printf("task list \n");
      Log::info("Defined Tasks");

      // format with sprintf, since Log does not allow format parameters
      sprintf(line, "%-10.10s %4s %s", "Name", "Prio", "isMain");
      printf("%s\n", line);
      Log::info(line);
      TaskList::Instance().sort(); // sort taskList

      for (int i = 0; i < TaskList::Instance().size(); i++) {
         Task *t = TaskList::Instance().getTaskByIndex(i);

         // format with sprintf, since Log does not allow format parameters
         sprintf(line, "%-10.10s  %3d  %2d", t->getName(),
                 (t->getPrio()).x,
                 t->getIsMain());
         Log::info(line);
         printf("%s\n", line);
         t->init();
      }


      if (TaskList::Instance().size() == 0) {
         printf("no task defined --> exit.\n");
         //exit(1); //!! no exit on esp32
      }

      /*****************init end*******************/
      //activate all threads which declared with "main"
      Log::info("start all main-threads");
      printf("start all main-threads\n");

      for (int i = 0; i < TaskList::Instance().size();  i++) {
         Task *t = TaskList::Instance().getTaskByIndex(i);

         if (t->getIsMain()) {
            t->activate(t);
         }
      }

      // all heap elements should be allocated now !
      // dump unused size to log
      Log::info("Free Heap size: %d byte", xPortGetFreeHeapSize());


      xTaskCreate(&blink_task, "blink_task",
                  5000, //configMINIMAL_STACK_SIZE,
                  NULL, 5, NULL);
      printf("blink started\n");
   }

}
