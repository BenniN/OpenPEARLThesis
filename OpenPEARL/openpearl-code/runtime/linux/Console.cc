/*
 [A "BSD license"]
 Copyright (c) 2017 Rainer Mueller
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
\brief Implementation of generic non-Basic Linux Systemdation

*/

#include <stdio.h>
#include <errno.h>
#include <unistd.h>  // write

#include "Task.h"
#include "Console.h"
#include "Dation.h"
#include "Log.h"
#include "Signals.h"
#include "BitString.h"

//DCLTASK(stbp, pearlrt::Prio(1), pearlrt::BitString<1>(1)) {
namespace pearlrt {
   void Console::consoleLoop() {
      pearlrt::TaskCommon * taskEntered;

      openSubDations();

      while (1) {
         // the method returns to adress and length of the input record
         taskEntered =
            consoleCommon.treatLine(&bufferFromConsoleCommon,
                                    &lengthOfConsoleCommonBuffer);
         deliveredCharacters = 0;

         if (taskEntered) {
            TaskCommon::mutexLock();
            taskEntered->unblock();
            TaskCommon::mutexUnlock();
         }
      }
   }


   Console* Console::instance = NULL;

   bool Console::isDefined() {
      return instance != NULL;
   }

   Console* Console::getInstance() {
      return instance;
   }

   void Console::openSubDations() {
      stdIn.dationOpen(NULL, 0);
      stdOut.dationOpen(NULL, 0);
   }

   Console::Console() : SystemDationNB() {
      struct termios newSetting;


      consoleMutex.name("ConsoleX");
      inUse = false;
      cap = FORWARD;
      cap |= PRM;
      cap |= ANY;
      cap |= IN | OUT | INOUT;
      consoleCommon.setSystemDations(&stdIn, &stdOut);

      if (! instance) {
         instance = this;
      } else {
         Log::error("Console: only allowed once");
         throw theInternalDationSignal;
      }

      tcgetattr(0, &oldTerminalSetting);  // 0 = STDIN
      newSetting = oldTerminalSetting;
      newSetting.c_lflag &= ~(ICANON | ECHO);   // no echo, no line edit
      tcsetattr(0, TCSANOW, &newSetting);   // 0 = STDIN
   }

   int Console::capabilities() {
      return cap;
   }

   Console* Console::dationOpen(const char * idf, int openParams) {

      if (openParams & (Dation::IDF | Dation::CAN)) {
         Log::error("Console: does not support IDF and CAN");
         throw theDationParamSignal;
      }

      consoleMutex.lock();
      inUse = true;

      consoleMutex.unlock();
      return this;
   }

   Console::~Console() {
      tcsetattr(0, TCSANOW, &oldTerminalSetting);  // 0=STDIN

      // the console thread is still waiting for input and has
      // the lock of StdIn aquired - let's unlock the mutex for
      // silent termination
      stdIn.abortRead();

      // forget about closing the system dations. This is done
      // automatically by the operating system
      instance = NULL;
   }


   void Console::dationClose(int closeParams) {
      //(int ret;
      //
      consoleMutex.lock();
      inUse = false;

      if (closeParams & Dation::CAN) {
         Log::error("Console: CAN not supported");
         consoleMutex.unlock();
         throw theDationParamSignal;
      }

      consoleMutex.unlock();
   }

   void Console::dationRead(void * destination, size_t size) {
      // int ret;
      char * dest = (char*) destination;

//      consoleMutexIn.lock();

      for (size_t i = 0;
            i < size && deliveredCharacters < lengthOfConsoleCommonBuffer;
            i++) {
         dest[i] = bufferFromConsoleCommon[deliveredCharacters + i];
         deliveredCharacters ++;
      }

//      consoleMutexIn.unlock();
   }


   void Console::dationWrite(void * source, size_t size) {
      stdOut.dationWrite(source, size);
      consoleCommon.startNextWriter();
   }

   void Console::dationUnGetChar(const char x) {
      ungetc(x, stdin);
   }


   void Console::translateNewLine(bool doNewLineTranslation) {
      // This is treated in ConsoleCommon
   }

   bool Console::allowMultipleIORequests() {
      return true;
   }

   void Console::registerWaitingTask(void * task, int direction) {
      // just delegate to the platform independent part
      consoleMutex.lock();
      consoleCommon.registerWaitingTask(task, direction);
      consoleMutex.unlock();
   }

   void Console::suspend(TaskCommon * ioPerformingTask) {
      consoleCommon.suspend(ioPerformingTask);
   }

   void Console::terminate(TaskCommon * ioPerformingTask) {
      consoleCommon.terminate(ioPerformingTask);
   }

}
