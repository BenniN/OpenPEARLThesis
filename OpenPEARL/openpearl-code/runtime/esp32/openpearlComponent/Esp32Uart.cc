/**
  * ESP32 UART Driver
  * Author: R. Mueller
  *
  */

/*
 [A "BSD license"]
 Copyright (c) 2019 R. Mueller
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

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR
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


#include "FreeRTOSConfig.h"
#include "FreeRTOS.h"
#include "task.h"
#include "semphr.h"
#include "driver/uart.h"
#include "allTaskPriorities.h"

#include "Esp32Uart.h"
#include "Log.h"
#include "Task.h"
#include "TaskCommon.h"
#include "Signals.h"

//#include "freertos/FreeRTOS.h"
//#include "freertos/semphr.h"
#include "esp_log.h"
#include "driver/uart.h"
#include "soc/uart_struct.h"
#include "string.h"


/*
 Note: Be careful when picking pins, 1-3 are reserved for UART0,
 4-5 clash with the default display GPIO and
 12-13 clash with the JTAG debugger.
 They can still be used but don't expect the overwritten functionality
 to work when you do
*/

#if configMINIMAL_STACK_SIZE > ESP32_UART_STACK_SIZE
#error "ESP32_UART_STACK_SIZE too small"
#endif

namespace pearlrt {

   // set default values
//   bool Esp32Uart::doNewLineTranslation = false;

   static const int validBaudRates[] = { 300, 600, 1200,
                                         2400, 4800, 9600,
                                         19200, 38400, 57600, 115200
                                       };
   static const uart_word_length_t uartDataBits[] = {
      UART_DATA_5_BITS, UART_DATA_6_BITS,
      UART_DATA_7_BITS, UART_DATA_8_BITS
   };

   static const int rxdPin[] = {GPIO_NUM_3, GPIO_NUM_26, GPIO_NUM_16};
   static const int txdPin[] = {GPIO_NUM_1, GPIO_NUM_25, GPIO_NUM_17};




   Esp32Uart::Esp32Uart(int uartNum, int baudRate,
                        int bitsPerCharacter, int stopBits,
                        char* parity, bool xon) {

      bool found;
      uart_config_t uartConfig;
      int i;

      printf("Esp32Uart(%d,%d,%d,%d,%c,%d):%p\n",
             uartNum, baudRate, bitsPerCharacter, stopBits,
             *parity, xon, this);

      if (uartNum < 0 || uartNum > 2) {
         Log::error("Esp32Uart: illegal port number (%d)", uartNum);
         throw theInternalDationSignal;
      }

      this->uartNum = uartNum;

      found = false;
      isOpen = false;
      hasUnGetChar = false;
      doNewLineTranslation = false;
      rxDataPending = 0;
      rxReadPointer = 0;

      for (i = 0;
            i < (int)(sizeof(validBaudRates) / sizeof(validBaudRates[0]));
            i++) {
         if (validBaudRates[i] == baudRate) {
            found = true;
         }
      }

      if (! found) {
         Log::error("Esp32Uart: illegal baud rate: %d",
                    baudRate);
         throw theInternalDationSignal;
      }

      uartConfig.baud_rate = baudRate;


      if (bitsPerCharacter < 5 || bitsPerCharacter > 8) {
         Log::error("Esp32Uart: illegal number of bits per character: %d",
                    bitsPerCharacter);
         throw theInternalDationSignal;
      }

      // now we are shure that the word length is ok
      uartConfig.data_bits = uartDataBits[bitsPerCharacter - 5];

      switch (stopBits) {
      case 1:
         uartConfig.stop_bits = UART_STOP_BITS_1;
         break;

      case 2:
         uartConfig.stop_bits = UART_STOP_BITS_2;
         break;

      default:
         Log::error("Esp32Uart: illegal number of stop bits: %d",
                    stopBits);
         throw theInternalDationSignal;
      }

      switch (*parity) {
      case 'O':
         uartConfig.parity = UART_PARITY_ODD;
         break;

      case 'E':
         uartConfig.parity = UART_PARITY_EVEN;
         break;

      case 'N':
         uartConfig.parity = UART_PARITY_DISABLE;
         break;

      default:
         Log::error("Esp32Uart: illegal parity: %c", *parity);
         throw theInternalDationSignal;
         break;
      }


      uartConfig.flow_ctrl = UART_HW_FLOWCTRL_DISABLE;
      // the esp-uart.c needs this value even if HW-FLOW ist disabled
      uartConfig.rx_flow_ctrl_thresh = 120;

      ESP_ERROR_CHECK(uart_param_config((uart_port_t)uartNum, &uartConfig));
      ESP_ERROR_CHECK(uart_set_pin((uart_port_t)uartNum,
                                   txdPin[uartNum], rxdPin[uartNum],
                                   UART_PIN_NO_CHANGE, UART_PIN_NO_CHANGE));


      if (xon) {
         /*
           uart_set_sw_flow_ctrl((uart_port_t) uartNum, true,
                     120, 10);
           this works only while receiving bytes
           To implement xon/xoff also for transmission we must
           filter each received byte and enable/disable the
           transmission according xoff/xon reception.
           The generic ISR does not allow this.
           Thus we need a own isr for the uart.
           The ISR of the api may be reduced to the relevant parts.
          */
         Log::error("Esp32Uart: xon/xoff not supported yet");
         throw theInternalDationSignal;
      }

      uart_driver_install(
         (uart_port_t)uartNum,
         ESP32_UART_BUFFSIZE, // rxBuffer size
         0,                   // no txBuffer send data immediatelly
         20,			// rx event queue size
         &rxQueue,		// rx queue handle
         0
      );			//interrupt allocation flags

      // initialize some attributes ..
      rxReadPointer = 0;
      rxDataPending = 0;

      sendCommand.status = 0;

      uart_flush_input((uart_port_t)uartNum);

      uartSendTaskHandle = xTaskCreateStatic(
                              uartSendTask,
                              "uartSendTask",
                              ESP32_UART_STACK_SIZE,
                              this,
                              PRIO_UART_SEND_TASK,
                              uartSendTaskStack,
                              (StaticTask_t*)&uartSendTaskTCB);
   }

   Esp32Uart* Esp32Uart::dationOpen(const char * idf,
                                    int openParam) {

      if (openParam & (Dation::IDF | Dation::CAN)) {
         Log::error("Esp32Uart: does not support IDF and CAN");
         throw theDationParamSignal;
      }

      if (isOpen) {
         Log::error("Esp32Uart: already opened");
         throw theOpenFailedSignal;
      }


      isOpen = true;

      return this;

   }

   void Esp32Uart::dationClose(int closeParam) {


      if (!isOpen) {
         Log::error("Esp32Uart: no dation opened");
         throw theCloseFailedSignal;
      }

      if (closeParam & Dation::CAN) {
         Log::error("Esp32Uart: CAN not supported");
         throw theDationParamSignal;
      }

      mutex.lock();
      isOpen = false;
      mutex.unlock();
   }

   void Esp32Uart::dationRead(void * destination, size_t size) {
      size_t readPending = 0;
      int i;

      char* dataPointer = (char*) destination;

      mutexRead.lock();
//     mutex.lock();

      if (!isOpen) {
         Log::error("Esp32Uart: not opened");
         mutexRead.unlock();
//         mutex.unlock();
         throw theDationNotOpenSignal;
      }

      setReaderTask(Task::currentTask());

      if (hasUnGetChar) {
         hasUnGetChar = false;
         *dataPointer = unGetChar;

         if (doNewLineTranslation && unGetChar == '\r') {
            *dataPointer = '\n';
         }

         size--;
         dataPointer++;
      }

//     mutex.unlock();

      try {
         while (size > 0) {
            if (rxDataPending < size) {
               readPending = rxDataPending;
            } else {
               readPending = size;
            }

            if (readPending > 0) {
               // we have some data in the read buffer
               for (i = 0; i < readPending; i++) {
                  *dataPointer = rxData[rxReadPointer];

                  if (doNewLineTranslation && *dataPointer == '\r') {
                     *dataPointer = '\n';
                  }

                  dataPointer ++;
                  rxReadPointer ++;
               }

               size -= readPending;
               rxDataPending -= readPending;
            }

            if (size > 0) {
               // not enough data in the rxData-buffer
               // let wait for the next bunch of data.
               // If an error is detected, the return value is not 0
               // if data or ABORT is detected, the return value is 0
               if (uartRecv()) {
                  setReaderTask(NULL);
                  mutexRead.unlock();
                  throw theReadingFailedSignal;
               }

               Task* ct = Task::currentTask();

               if (ct) {
                  ct->treatCancelIO();
               }

            }
         }
      } catch (TerminateRequestSignal s) {
         setReaderTask(NULL);
         mutexRead.unlock();
         throw;
      }


      setReaderTask(NULL);
      mutexRead.unlock();

   }

   void Esp32Uart::dationWrite(void * src, size_t size) {
      int i;
      static const char cr = '\r';
      char * source = (char*) src;

      mutexWrite.lock();
      mutex.lock();

      if (!isOpen) {
         Log::error("Esp32Uart: not opened");
         mutex.unlock();
         throw theDationNotOpenSignal;
      }

      mutex.unlock();

      setWriterTask(Task::currentTask());

      // writeBytes calls treatCancelIO in case of an abort request
      // if the abort is due to a TERMINATE request, an exception is thrown
      // this must be caught and rethrown at all levels of the call stack
      // which contains mutex and semaphore operations which must
      // be unlocked
      try {
         if (doNewLineTranslation) {
            for (i = 0; i < size; i++) {
               if (*source == '\n') {
                  writeBytes(&cr, 1);
               }

               writeBytes(source, 1);
               source ++;
            }
         } else {
            writeBytes(source, size);
         }
      } catch (TerminateRequestSignal & s) {
         setWriterTask(NULL);
         mutexWrite.unlock();
         throw;
      }

      uart_wait_tx_done((uart_port_t)(uartNum), (portTickType)portMAX_DELAY);

      setWriterTask(NULL);
      mutexWrite.unlock();

   }

   void Esp32Uart::writeBytes(const char * data, size_t nbr) {
      // let's check if we had a writeAbort at the previous
      // invocation of this method - if yes - let#s wait
      // until the previous output command was compeleted
      sendCommand.mutex.lock();

      while (sendCommand.status & writeAborted) {
         sendCommand.status &= ~writeAborted;
         sendCommand.mutex.unlock();
         // let's wait until the previous job is done
         sendCommand.done.request();

         // check wether we were aborted again
         // if yes, we must repeat in case of suspend as
         // long we are no longer aborted
         sendCommand.mutex.lock();

         if (sendCommand.status & writeAbort) {
            sendCommand.status |= writeAborted;
            sendCommand.status &= ~writeAbort;
            sendCommand.mutex.unlock();

            Task* ct = Task::currentTask();

            if (ct) {
               ct->treatCancelIO();
            }

            sendCommand.mutex.lock();
         }
      }

      sendCommand.mutex.unlock();

      sendCommand.data = data;
      sendCommand.nbr = nbr;
      xTaskNotifyGive((TaskHandle_t)uartSendTaskHandle);
      sendCommand.done.request();

      // check if the write was aborted
      sendCommand.mutex.lock();

      if (sendCommand.status & writeAbort) {
         sendCommand.status &= ~writeAbort;
         sendCommand.status |= writeAborted;
         sendCommand.mutex.unlock();

         Task* ct = Task::currentTask();

         if (ct) {
            ct->treatCancelIO();
         }

         // if we reach this line, than we had a write abort and
         // no TerminateRequestSignal
         // the last job may be still in progress
      } else {
         sendCommand.mutex.unlock();
      }
   }

   void Esp32Uart::dationUnGetChar(const char c) {
      mutex.lock();

      if (!isOpen) {
         Log::error("Esp32Uart: not opened");
         mutex.unlock();
         throw theDationNotOpenSignal;
      }

      hasUnGetChar = true;
      unGetChar = c;

      mutex.unlock();

   }

   int Esp32Uart::capabilities() {
      return (Dation::FORWARD | Dation::IN | Dation::PRM | Dation::ANY |
              Dation::OUT | Dation::INOUT);
   }

   void Esp32Uart::translateNewLine(bool doNewLineTranslation) {
      this->doNewLineTranslation = doNewLineTranslation;
   }


   void Esp32Uart::suspendReader(TaskCommon * ioPerformingTask) {
      uart_event_t event;

      // insert dummy element in rxQueue, thus the reading thread
      // becomes runnable again and treats the suspend request
      event.type = UART_EVENT_MAX;
      event.size = 0;
      xQueueSendToFront(rxQueue, (void *)&event, (portTickType)portMAX_DELAY);
   }


   void Esp32Uart::suspendWriter(TaskCommon * ioPerformingTask) {
      sendCommand.mutex.lock();
      sendCommand.status |= writeAbort;
      sendCommand.done.release();
      sendCommand.mutex.unlock();
   }

   void Esp32Uart::terminateReader(TaskCommon * ioPerformingTask) {
      // thats the same operation as during suspend
      // treatCancelIO differs in behavior
      suspendReader(ioPerformingTask);
   }
   void Esp32Uart::terminateWriter(TaskCommon * ioPerformingTask) {
      suspendWriter(ioPerformingTask);
   }

   int Esp32Uart::uartRecv() {
      uart_event_t event;

      // reset the rxData buffer data to safe values (nothing is present)
      rxReadPointer = 0;
      rxDataPending = 0;

      if (xQueueReceive(rxQueue,
                        (void *)&event,
                        (portTickType)portMAX_DELAY)) {
         Log::info("Esp32_uart[%d] event: %d", uartNum, event.type);

         switch (event.type) {
         case UART_DATA:
            //Event of UART receiving data
            /*We'd better handler data event fast,
            there would be much more data events than
             other types of events. If we take too much
              time on data event, the queue might
             be full.*/
            Log::info("dataSize %d", event.size);
            // we store the number of available indput data and read the data
            // at the superior call level
            rxDataPending = event.size;
            uart_read_bytes((uart_port_t)uartNum,
                            (uint8_t*)rxData,
                            event.size,
                            portMAX_DELAY);
            return 0;

         //Event of HW FIFO overflow detected
         case UART_BUFFER_FULL: //Event of UART ring buffer full
         case UART_FIFO_OVF:
            // fifo overflow happened!
            // is discarding remaining bytes in the FIFO and queue ok?
            uart_flush_input((uart_port_t)uartNum);
            xQueueReset(rxQueue);
            Log::error("Esp32Uart: Receive overrun");
            return -1;

         case UART_BREAK:
            Log::error("Esp32Uart: break received");
            return -1;

         case UART_PARITY_ERR:
            Log::error("Esp32Uart: Parity error");
            return -1;

         case UART_FRAME_ERR:
            Log::error("Esp32Uart: frame error");
            return -1;

         case UART_EVENT_MAX:
            return 0;

         default:
            Log::warn("Esp32Uart: untreated event type (%d)", event.type);
         }
      }

      return 0;

   }
   void Esp32Uart::uartSendTask(void * parameters) {
      Esp32Uart * uartObject = (Esp32Uart*)parameters;
      int transmitted;

      while (true) {
         ulTaskNotifyTake(pdTRUE, portMAX_DELAY);

         transmitted = uart_write_bytes((uart_port_t)(uartObject->uartNum),
                                        uartObject->sendCommand.data,
                                        uartObject->sendCommand.nbr);

         // inform PEARL application that the write was compeleted
         // maybe only sent into the transmit fifo
         uartObject->sendCommand.done.release();
      }
   }
}
