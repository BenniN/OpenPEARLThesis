/**
  \file


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

#ifndef ESP32UART_INCLUDED
#define ESP32UART_INCLUDED

#include "FullDuplexDationAbortNB.h"
#include "Mutex.h"
#include "CSema.h"

namespace pearlrt {

   /**
   \brief ESP32 uart device

   driver for the Uarts of the Esp32 system

   The uart driver of the esp-idf works with interrupts, thus
   we can use the driver for OpenPEARL.

   The ESP-logging on UART0 should not disturb the OpenPEARL
   operations in this interface.

   printf goes also to UART0.

   In order to realize suspending and terminating a task during it waits
   for comletion of an i/o operation, we use a separate task which
   communicates with the esp32-API of the uart. These function may block
   until enough data arrived or were transmitted. In these state a suspend or
   terminate request would not be possible to realize without inside
   knowledges of the API implementation.
   The usage of a separate task decouples the OpenPEARL application from
   the i/o system. This task is a good candidate to run of the PRO-CPU.

   The OpenPEARL driver code will trigger the transfer task via  ...

   For input direction, no separate task is required, since the uart-API
   provides the possibility of a FreeRTOS queue with event. In case to
   interrupting the reading, a special event may be sent to this queue
   releasing the OpenPEARL-task from the blocked state.

   The notification of the so-called uartOutTask is done with the
   FreeRTROS notifications. The application task waits on a simple semaphore
   until the output is completed.


   The UART device of the ESP32 platform is implemented upon the ESP32 api.
   The GenericUart implementation from other FreeRTOS based system does
   not fit, since GenericUart depends on a own implementation of the
   interrupt service routine. This would be possible for the ESP32, but
   we would loose contact to improvements in the rapidly growing esp32 api.

   */
   class Esp32Uart : public FullDuplexDationAbortNB {

      /** the esp32 api for the uart requires a longer receive buffer than
         the internal FIFO length of 128 bytes
         lets add 2 Bytes to make the api happy
      */
#define ESP32_UART_BUFFSIZE  130

      /**
       stack of for uartSendTask: configMINIMAL_STACK_SIZE is sufficient
      */
#define ESP32_UART_STACK_SIZE 768

   private:
      Mutex mutex;      // mutex for objects data
      Mutex mutexRead;      // mutex for objects data
      Mutex mutexWrite;      // mutex for objects data

      enum {writeAbort = 1, writeAborted = 2} UartOutTaskCommandStatus;

      struct UartOutTaskCommand {
         const char * data;
         size_t nbr;
         CSema done;
         Mutex mutex;

         /**
         the status field is used to abort a write operation.
         <ul>
         <li>Initially set to 0
         <li> bit 0 ist set by the application if an abort request
              is set
         <li> bit 1 is set when theabort request is detected. This
              bit indicates that there a an write job still in progres.
              Before the next write job we must wait for the completion
              of the previous write job.
         <li> If we get a new abort request during wait for completion of the
              previous write bon, we set bit 0 and clear bit 1 and
              we call treatCancelIO and wait until
              we get terminated or the previous write job succeeds.
         </ul>
         */
         int status;
      } sendCommand;
      FakeStackType_t uartSendTaskStack[ESP32_UART_STACK_SIZE];
      FakeTCB_t       uartSendTaskTCB;
      FakeTaskHandle_t uartSendTaskHandle;

      char unGetChar;           // character for unget
      bool hasUnGetChar;

      bool isOpen;

      int uartNum;
      bool doNewLineTranslation;

      /* we wait for an event which indicates a status change.
         In case of available input data, they may be sent as buffer.
         We must iterate upun this buffer over multiple read requests
      */
      /* QueueHandle_t */  void* rxQueue;
      size_t rxDataPending; // number of data pending in rxFifo
      int rxReadPointer;
      char rxData[ESP32_UART_BUFFSIZE];
      int uartRecv();

      /**
      the tranmission of bytes is implemented in a separat task in order
      to abort the transmission for SUSPEND or TERMINATE during the
      tranmission, or if the tranmission is blocked by the receiver.

      \param  parameters is a pointer to the Esp32Uart object
      */
      static void uartSendTask(void * parameters);

      /**
      send the given amount of data to the uartSendTask
      */
      void writeBytes(const char * data, size_t nbr);

   public:
      /**
       define the uart device

       \param port the number (0,1 or 2)
       \param baudRate the baud rate; all usual values from 300 to 115200 are
         accepted
       \param bitsPerCharacter  5-8  is allowed
       \param stopBits number of stop bits
       \param parity the desired type of parity ('O', 'E', 'N')
       \param xon use xon/sxoff protocol

       \throws theInternalDationSignal in case of illegal parameter values
       */

      Esp32Uart(int uartNum, int baudRate, int bitsPerCharacter,
                int stopBits, char* parity, bool xon);

      /**
      open the system dation

      \param idf must be null, since no named files aree supported here
      \param openParam must be 0, since no open params are supported here
      \returns pointer to this object as working object
      */
      Esp32Uart* dationOpen(const char * idf, int openParam);

      /**
      close the systen dation

      \param closeParam must be 0, since no parameters are supported
      */
      void dationClose(int closeParam);

      /**
      read the given amount of data from the device

      \param destination address of the buffer to store the data
      \param size number of bytes to read
      */
      void dationRead(void * destination, size_t size);

      /**
      write the given amount of data to the device

      \param destination address of the buffer which contains the data
      \param size number of bytes to write
      */
      void dationWrite(void * destination, size_t size);

      /**
      return one byte to next nest dationRead invocation, like ungetch()
      in the lib-c

      \param  c the character to be saved for the next read request
      */
      void dationUnGetChar(const char c);

      /**
      return the capabilities of the dation as bit pattern
      */
      int capabilities();

      /**
      translate newline<br>
      in input: CR -> \n<br>
      in output: \n -> CR+LF

      \param doNewLineTranslation true enables the translation, <br>
              false disables the translation
      */
      void translateNewLine(bool doNewLineTranslation);

      /**
         suspend the i/o performing task which is currently executing
      the read operation

         \param ioPerformingTask pointer to the task object of the
               i/o performing task
      */
      void suspendReader(TaskCommon * ioPerformingTask);

      /**
         suspend the i/o performing task which is currently executing
      the write operation

         \param ioPerformingTask pointer to the task object of the
               i/o performing task
      */
      void suspendWriter(TaskCommon * ioPerformingTask);

      /**
         terminate the i/o performing task which is currently executing
      the read operation

         \param ioPerformingTask pointer to the task object of the
               i/o performing task
      */
      void terminateReader(TaskCommon * ioPerformingTask);

      /**
         terminate the i/o performing task which is currently executing
      the write operation

         \param ioPerformingTask pointer to the task object of the
               i/o performing task
      */
      void terminateWriter(TaskCommon * ioPerformingTask);

#if 0
   private:
      void treatInterrupt();
      void doRecvChar();
      bool sendNextChar();
      void interruptEnable(bool on);
      void logError(int status);
      bool getNextTransmitChar(char * ch);
      bool addReceivedChar(char ch);
#endif

   };
}


#endif
