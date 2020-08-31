/*
 [A "BSD license"]
 Copyright (c) 2017-2019 Rainer Mueller
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

#include "SystemDationNB.h"
#include "TaskCommon.h"
#include "PriorityQueue.h"
#include "Mutex.h"

#ifndef CONSOLECOMMON_INCLUDED
#define CONSOLECOMMON_INCLUDED

namespace pearlrt {

   /**
   The Console device shall be available for all platform to adress
   text input to individual tasks
   This class provides the target independent stuff like
   line edit data processing, task name lookup, ...

   The following cursor keys are treated:
   <ul>
    <li> LEFT  ESC [ D
    <li> RIGHT ESC [ E
    <li> POS1  ESC O H
    <li> END   ESC O F
    <li> INS   ESC [ 2
    <li> DEL   ESC [ 3
    <li> BS    0x08
   </ul>

   The input is accepted as UTF-8. Internally all input data is stored
   with 16 bit. At the end of the input, the data is compressed to
   deliver a UTF-8 string.
   The input length is limited to 80 characters. This size
   is choosen, since the usual terminal size is 80 columns.
   Longer input lines will produce problems with the cursor
   control if a line wrap occurs.
   */
   class ConsoleCommon {
   private:
      Mutex mutex;

      PriorityQueue waitingForOutput;
      TaskCommon* waitingForInput;
      SystemDationNB * systemIn;
      SystemDationNB * systemOut;
      TaskCommon*  lastAdressedTask;

      bool insertMode;
      size_t  nbrEnteredCharacters;
      size_t  cursorPosition;
      bool inputStarted;
      struct Wchar {
         char page;     // ether 0 or UTF8_Part1 or UTF8_Part2
         char ch;       // the index in the page
      };
      union InputLine {
         struct Wchar wLine[82];  // 80 characters+\n+\0
         char compressedLine[164];
      } inputLine;

      void compress();
      int getChar(void);
      void putChar(char ch);
      void putChar(Wchar ch);
      void putString(const Wchar* string);
      void putString(const char* string);
      void goLeft(int n);
      void goRight(int n);
      bool removeFromInputList(TaskCommon * t);
      bool removeFromOutputList(TaskCommon * t);
      bool treatCommand(char* line);
      const char* helpHelp();
      void printHelp();
      const char* helpPrli();
      void printPrli();

      struct Commands {
         const char* command;
         const char* (ConsoleCommon:: *help)();
         void (ConsoleCommon:: *doIt)();
      } commands[3];

   public:
      /**
      resume operation  the next waiting writer at completion
      of an input processing
      */
      void startNextWriter();

      /**
      ctor: initialize private data and memorize the in and out-dations
      */
      ConsoleCommon();

      /**
      set the system dations

      \param in pointer to the system dation which is responsible
                for data input
      \param out pointer to the system dation which is responsible
                 for data output
      */
      void setSystemDations(SystemDationNB* in, SystemDationNB* out);

      /**
      perform the input processing with line editing functions
      the method returns if the line was entered completely

      The input line will be scanned for the prefix of :taskname: and the
      corresponding task pointer from the wqaiting queue is returned
      \param length returned length of the input record
      \param inputBuffer return input buffer
      \returns pointer to the task which gets the input<br>
              or NULL, if the selected task expects no input
      */
      TaskCommon* treatLine(char** inputBuffer, size_t * length);


      /**
       register the calling task as waiting for an IO-operation

       The method is only called if allowMultipleIORequests is set by the
       system dation

       \param task the pointer to the calling task
       \param direction is ether Dation::IN or Dation::OUT
       */
      void registerWaitingTask(void * task, int direction);

      /**
      terminate a task which is registered for console i/o

      This method is invoked from TaskCommon via the SystemDation.
      The specified task is removed from input or output list.
      In TaskCommon, the further treatment for termination is
      started,

      \param ioPerformingTask pointer to the task which should become
            terminated
      */
      void terminate(TaskCommon * ioPerformingTask);

      /**
      suspend a task which is registered for console i/o

      This method is invoked from TaskCommon via the SystemDation.
      The specified task is removed from input or output list.
      In TaskCommon, the further treatment for suspending is
      started,

      \param ioPerformingTask pointer to the task which should become
            suspended
      */
      void suspend(TaskCommon * ioPerformingTask);

   };
}
#endif
