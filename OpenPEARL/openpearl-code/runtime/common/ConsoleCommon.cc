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

#include "ConsoleCommon.h"
#include "TaskMonitor.h"
#include "TaskList.h"

namespace pearlrt {

// input character codes
#define BS 0x08
#define ESC 27
#define BRACKETOPEN '['
// prefixed with ESC [
#define RIGHT 67
#define LEFT  68
#define UP   0x41      // not used yet
#define DOWN   0x42    // not used yet
#define INSERT 50	// change insert mode
#define DELETE 51	// delete from right

// prefixed with ESC O
#define HOME   'H'
#define END    'F'

#define NL '\n'
#define BEL '\a'  // alarm code
#define SPACE ' '
#define UTF8_Part1 0xc2
#define UTF8_Part2 0xc3

   ConsoleCommon::ConsoleCommon() {
      insertMode = false;
      inputStarted = false;
      lastAdressedTask = NULL;
      waitingForInput = NULL;
      mutex.name("ConsoleCommon");

      // setup command list
      commands[0].command = "/HELP";
      commands[0].help = &ConsoleCommon::helpHelp;
      commands[0].doIt = &ConsoleCommon::printHelp;
      commands[1].command = "/?";
      commands[1].help = &ConsoleCommon::helpHelp;
      commands[1].doIt = &ConsoleCommon::printHelp;
      commands[2].command = "/PRLI";
      commands[2].help = &ConsoleCommon::helpPrli;
      commands[2].doIt = &ConsoleCommon::printPrli;
   }

   void ConsoleCommon::setSystemDations(SystemDationNB* in,
                                        SystemDationNB* out) {
      systemIn = in;
      systemOut = out;
   }

   int ConsoleCommon::getChar(void) {
      char ch;
      systemIn->dationRead(&ch, 1);
      return (ch);
   }

   void ConsoleCommon::putChar(char ch) {
      systemOut->dationWrite(&ch, 1);
   }

   void ConsoleCommon::putChar(Wchar ch) {
      if (ch.page == 0) {
         systemOut->dationWrite(&(ch.ch), 1);
      } else {
         systemOut->dationWrite(&ch, 2);
      }
   }

   void ConsoleCommon::compress() {
      char* writePointer = inputLine.compressedLine;
      size_t i;

      for (i = 0; i < nbrEnteredCharacters; i++) {
         if (inputLine.wLine[i].page != 0) {
            *writePointer++ = inputLine.wLine[i].page;
         }

         *writePointer++ = inputLine.wLine[i].ch;
      }

      nbrEnteredCharacters = (int)(writePointer - inputLine.compressedLine);
   }

   void ConsoleCommon::putString(const char* string) {
      systemOut->dationWrite((void*)string, strlen(string));
   }

   void ConsoleCommon::putString(const Wchar* string) {
      int i;

      for (i = 0; string[i].ch != 0; i++) {
         putChar(string[i]);
      }
   }


   void ConsoleCommon::goRight(int n) {
      while (n--) {
         putChar(inputLine.wLine[cursorPosition]);
         cursorPosition++;
      }
   }

   void ConsoleCommon::goLeft(int n) {
      while (n--) {
         putChar(BS);
      }
   }

   TaskCommon* ConsoleCommon::treatLine(char** inputBuffer, size_t * length) {
      TaskCommon * t;
      static TaskCommon * lastTaskEntered = NULL;
      TaskCommon * previousTaskInList = NULL;
      int ch;
      Wchar wch;
      static const Wchar wnull = {0, 0};
      size_t i;
      int cursor = 0;
      bool endOfLineReceived = false;
      bool taskIsInList;


      nbrEnteredCharacters = 0;
      cursorPosition = 0;
      inputStarted = false;

      while (!endOfLineReceived) {
         cursor = 0;       // no cursor position code detected
         ch = getChar();   // wait passive until character is received

         // test for combined character
         if (ch == ESC) {
            ch = getChar();

            if (ch == BRACKETOPEN) {
               ch = getChar();

               if (ch == RIGHT) {
                  cursor = RIGHT;
               } else if (ch == LEFT) {
                  cursor = LEFT;
               } else if (ch == INSERT) {
                  insertMode = ! insertMode;
                  ch = getChar(); // discard trailing ~ character
                  cursor = -1;  // discard ch
               } else if (ch == DELETE) {
                  ch = getChar(); // discard trailing ~ character
                  cursor = DELETE;
               } else {
                  // ignore all other
                  Log::debug("ignore ESC [ %x", ch);

                  cursor = -1;  // discard ch
               }

            } else if (ch == 'O') { // ESC O sequences for HOME and END
               ch = getChar();

               if (ch == HOME) {
                  cursor = HOME;
               } else if (ch == END) {
                  cursor = END;
               } else {
                  // ignore all other
                  Log::debug("ignore ESC O %x", ch);
                  cursor = -1;  // discard ch
               }
            }
         } else if ((ch & 0x0ff) == UTF8_Part1 || (ch & 0x0ff) == UTF8_Part2) {
            wch.page = ch;
            wch.ch = getChar();
         } else {
            wch.ch = ch;
            wch.page = 0;
         }

         switch (cursor) {
         case -1: // discard character
            continue;

         case 0: // normal input
            if (wch.ch == NL && wch.page == 0) {
               putChar(wch);
               // always add NL at the end and the NIL also
               inputLine.wLine[nbrEnteredCharacters++] = wch;
               inputLine.wLine[nbrEnteredCharacters++] = wnull;
//     printf("end-of-record found cp=%zu nbr=%zu\n>%s<\n",
//             cursorPosition, nbrEnteredCharacters, inputLine);
               endOfLineReceived = true;
            } else if ((wch.ch == 0x07f || wch.ch == BS) && wch.page == 0) {
               // del to left
               if (cursorPosition > 0) {
                  for (i = cursorPosition; i < nbrEnteredCharacters; i++) {
                     inputLine.wLine[i - 1] = inputLine.wLine[i];
                  }

                  nbrEnteredCharacters--;
                  goLeft(1);
                  cursorPosition --;

                  for (i = cursorPosition; i < nbrEnteredCharacters; i++) {
                     putChar(inputLine.wLine[i]);
                     //   cursorPosition++;
                  }

                  putChar(SPACE);
                  goLeft(nbrEnteredCharacters - cursorPosition + 1);
               }
            } else if ((wch.page == 0 && (ch >= ' ' && ch < 0x7f)) ||
                       wch.page != 0) {
               // reserve space for NL and NIL
               if (nbrEnteredCharacters < sizeof(inputLine) / 2 - 2) {
                  if (insertMode) {
                     if (nbrEnteredCharacters > cursorPosition) {
                        for (i = nbrEnteredCharacters;
                              i >= cursorPosition; i--) {
                           inputLine.wLine[i + 1] = inputLine.wLine[i];
                        }
                     }

                     inputLine.wLine[cursorPosition] = wch;
                     nbrEnteredCharacters++;
                     putChar(wch);
                     cursorPosition ++;

                     for (i = cursorPosition; i < nbrEnteredCharacters; i++) {
                        putChar(inputLine.wLine[i]);
                     }

                     goLeft(nbrEnteredCharacters - cursorPosition);
                  } else {
                     inputLine.wLine[cursorPosition] = wch;

                     if (cursorPosition == nbrEnteredCharacters) {
                        nbrEnteredCharacters++;
                     }

                     putChar(wch);
                     cursorPosition ++;
                  }
               } else {
                  putChar(BEL);
               }
            } else {
               Log::info("ConsoleCommon::character %x found - not treated yet",
                         ch & 0x0ff);
            }

            break;

         case DELETE: // del key --> delete from right

//printf("DELETE: cp=%d nbr=%d\n", cursorPosition, nbrEnteredCharacters);
            if (cursorPosition < nbrEnteredCharacters) {
               nbrEnteredCharacters--;

               for (i = cursorPosition; i < nbrEnteredCharacters; i++) {
                  inputLine.wLine[i] = inputLine.wLine[i + 1];
               }

               for (i = cursorPosition; i < nbrEnteredCharacters; i++) {
                  putChar(inputLine.wLine[i]);
               }

               putChar(SPACE);

               goLeft(nbrEnteredCharacters - cursorPosition + 1);
            }

            break;

         case RIGHT: // position to right if possible
            if (cursorPosition < nbrEnteredCharacters) {
               goRight(1);
            } else {
               putChar(BEL);
            }

            break;

         case LEFT: // position to left if possible
            if (cursorPosition > 0) {
               goLeft(1);
               cursorPosition--;
            } else {
               putChar(BEL);
            }

            break;

         case HOME:  // position to first position
            goLeft(cursorPosition);
            cursorPosition = 0;
            break;

         case END:  // position after last entered position
            if (cursorPosition < nbrEnteredCharacters) {
               goRight(nbrEnteredCharacters - cursorPosition);
            }

            break;

         }

         if (nbrEnteredCharacters > 0) {
            inputStarted = true;
         } else {
            if (inputStarted) {
               inputStarted = false;
               startNextWriter();
            }
         }
      }

      // input line ready
      //   preset return values with complete line
      //   this may be truncates if a taskname was detected
      compress();
      *length = nbrEnteredCharacters;
      *inputBuffer = inputLine.compressedLine;
      inputStarted = false;

      // check if line starts with (new) task name
      if (inputLine.compressedLine[0] == ':') {
         // let's search the next colon
         for (i = 1; i < nbrEnteredCharacters; i++) {
            if (inputLine.compressedLine[i] == ':') {
               if (i > 2) {
                  // task name found
                  // update length information
                  * length -= i + 1;
                  // let's check if the task waits for input
                  // and pass the effecitive input to the input processing
                  // and quit this function
                  inputLine.compressedLine[i] = '\0';
                  lastTaskEntered = NULL;

                  mutex.lock();

                  for (t = waitingForInput;
                        t != NULL; t = t->getNext()) {
                     // ignore leading underscore in task name

                     if (strcmp(t->getName() + 1,
                                inputLine.compressedLine + 1) == 0) {
                        // found adressed task

                        // and set the return parameters
                        *inputBuffer = inputLine.compressedLine + i + 1;
                        lastTaskEntered = t;
                        break;
                     }
                  }

                  mutex.unlock();

                  if (t == NULL) {
                     lastTaskEntered = NULL; // forget last valid task
                     putString("\n:???: not waiting\n");
                     // return the complete input line
                     *length = nbrEnteredCharacters;
                     inputLine.compressedLine[i] = ':'; //restore 2nd colon
                     startNextWriter();
                     // discard this input line for further processing
                     return NULL;
                  }
               }
            }
         }

         // no 2nd task name delimiter found
         // will be treated as if no colon is at the first position
      } else if (inputLine.compressedLine[0] == '/') {
         treatCommand(inputLine.compressedLine);
         startNextWriter();
         // nothing to do -- command was treated
         return 0;
      }


      // remove this task from wait queue
      taskIsInList = false;
      previousTaskInList = NULL;
      mutex.lock();

      if (lastTaskEntered == NULL) {
         putString("\n:???: no default task\n");
         mutex.unlock();
         startNextWriter();
         return NULL;
      }

      for (t = waitingForInput; t != NULL; t = t->getNext()) {
         if (lastTaskEntered == t) {
            taskIsInList = true;

            if (previousTaskInList) {
               previousTaskInList->setNext(lastTaskEntered->getNext());
               t->setNext(NULL);
            } else {
               waitingForInput = lastTaskEntered->getNext();
            }

            break;
         }

         previousTaskInList = t;
      }

      mutex.unlock();
      startNextWriter();

      if (taskIsInList) {
         return lastTaskEntered;
      }

      // no task name given and default task is not in list
      putString("\n:???: not waiting\n");
      return NULL;

   }

   void ConsoleCommon::startNextWriter() {
      mutex.lock();

      if (!inputStarted) {
         TaskCommon * nextWriter = waitingForOutput.getHead();

         if (nextWriter) {
            waitingForOutput.remove(nextWriter);
            nextWriter->unblock();
         }
      }

      mutex.unlock();
   }

   void ConsoleCommon::registerWaitingTask(void * task, int direction) {
      TaskCommon * t = (TaskCommon*) task;

      mutex.lock();

      if (direction == Dation::IN) {
         t->setNext(waitingForInput);
         waitingForInput = t;
      } else if (direction == Dation::OUT) {

         if (inputStarted || waitingForOutput.getHead()) {
            // queue not empty --> add task as waiter
            waitingForOutput.insert(t);
         } else {
            // let the task do its output
            t->unblock();
         }
      } else {
         Log::error("ConsoleCommon::registerWaitung: illegal direction=%d",
                    direction);
         mutex.unlock();
         throw theInternalDationSignal;
      }

      mutex.unlock();
   }

   void ConsoleCommon::suspend(TaskCommon * ioPerformingTask) {
      terminate(ioPerformingTask);
   }

   bool ConsoleCommon::removeFromInputList(TaskCommon* taskToRemove) {
      TaskCommon * previousTaskInList = NULL;
      TaskCommon * t;

      mutex.lock();

      for (t = waitingForInput; t != NULL; t = t->getNext()) {
         if (t == taskToRemove) {
            // remove this task from wait queue
            if (previousTaskInList) {
               previousTaskInList->setNext(t->getNext());
               t->setNext(NULL);
            } else {
               waitingForInput = t->getNext();
            }

            mutex.unlock();
            return true;
         }

         previousTaskInList = t;
      }

      mutex.unlock();
      return false;
   }

   bool ConsoleCommon::removeFromOutputList(TaskCommon* taskToRemove) {
      mutex.lock();
      TaskCommon * next = waitingForOutput.getHead();

      while (next) {
         if (next == taskToRemove) {
            waitingForOutput.remove(next);
            mutex.unlock();
            return true;
         }

         next = waitingForOutput.getNext(next);
      }

      mutex.unlock();
      return false;
   }


   void ConsoleCommon::terminate(TaskCommon * ioPerformingTask) {
      // let's check wether the task is doing input or output
      if (removeFromInputList(ioPerformingTask)) {
         Log::debug("ConsoleCommon: removed %s from input list",
                    ioPerformingTask->getName());
         /*
         for (TaskCommon * t = waitingForInput; t != NULL; t = t->getNext()) {
            Log::debug("ConsoleCommon: still waiting: %s", t->getName());
         }
         */
      } else if (removeFromOutputList(ioPerformingTask)) {
         Log::debug("ConsoleCommon: removed %s from output list",
                    ioPerformingTask->getName());
      } else {
         Log::error("ConsoleCommon: task %s was nether in input"
                    " nor in output list", ioPerformingTask->getName());
         throw theInternalTaskSignal;
      }
   }


   const char* ConsoleCommon::helpHelp() {
      static const char* help = "show available commands";
      return help;
   }

   void ConsoleCommon::printHelp() {
      size_t i;
      char line[80];

      putString("supported commands\n");

      for (i = 0; i < sizeof(commands) / sizeof(commands[0]); i++) {
         sprintf(line, "  %-10.10s: ", commands[i].command);
         putString(line);
         putString((this->*(commands[i].help))());
         putChar('\n');
      }
   }

   const char* ConsoleCommon::helpPrli() {
      static const char* help = "show task stati";
      return help;
   }

   void ConsoleCommon::printPrli() {
      Task * t;
      int j, n;
      char line1[80], line2[80], line3[80], line4[80];
      char* detailedState[] = {line1, line2, line3, line4};

      sprintf(line1, "Number of pending tasks: %d\n",
              TaskMonitor::Instance().getPendingTasks());
      putString(line1);

      for (int i = 0; i < TaskList::Instance().size();  i++) {
         t = TaskList::Instance().getTaskByIndex(i);
         sprintf(line1, "%-10.10s  %3d  %2d  %-20.20s (%s:%d)\n",
                 t->getName() + 1, (t->getPrio()).x, t->getIsMain(),
                 t->getTaskStateAsString(),
                 t->getLocationFile(), t->getLocationLine());
         putString(line1);
         n = t->detailedTaskState(detailedState);

         for (j = 0; j < n ; j++) {
            putChar('\t');
            putString(detailedState[j]);
            putChar('\n');
         }
      }
   }



   bool ConsoleCommon::treatCommand(char * line) {
      size_t i;
      bool cmdFound = false;

      // remove trailing newline
      if (line[strlen(line) - 1] == '\n') {
         line[strlen(line) - 1] = '\0';
      }

      for (i = 0; i < sizeof(commands) / sizeof(commands[0]); i++) {

         if (strcasecmp(line, commands[i].command) == 0) {
            (this->*(commands[i].doIt))();
            cmdFound = true;
         }
      }

      if (cmdFound == false) {
         printf("illegal command\n");
      }

      return cmdFound;
   }

}
