/*
 [A "BSD license"]
 Copyright (c) 2012-2018 Rainer Mueller
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

\brief test program for terminate while ether running, suspended or
   blocked on semaphore operations

\page Testprograms

\section sematest
This module tests whether tasks can be terminated while
running, suspended or waiting for a semaphore

\cond TREAT_EXAMPLES
*/


#include "PearlIncludes.h"
#include "Semaphore.h"
#include <stdio.h>
using namespace std;

DCLSEMA(_s1, 0);
DCLSEMA(_s2, 10);
DCLSEMA(_boltBlockerGoon, 0);
DCLBOLT(_bolt);

SPCTASK(START);
//SPCTASK(T1SemaBlocked);
SPCTASK(T2SemaBlocked);
SPCTASK(T2BoltBlocked);
SPCTASK(T2BoltBlocker);

SPCTASK(T2running);
SPCTASK(T2suspended);

void t1SemaBlocked(pearlrt::Task* me) ;
void t1BoltBlocked(pearlrt::Task* me) ;
void running(pearlrt::Task* me) ;
void suspended(pearlrt::Task* me) ;
void terminated(pearlrt::Task* me) ;

DCLTASK(START, pearlrt::Prio(10), pearlrt::BitString<1>(1)) {
    terminated(me);
    running(me);
    suspended(me);
    t1SemaBlocked(me);
    t1BoltBlocked(me);
}


void t1SemaBlocked(pearlrt::Task* me) { 
   int ts;
   me->setLocation(__LINE__, __FILE__);
   printf("Task T1SemaBlocked started -- do 10 slow terminates\n");
   me->setLocation(__LINE__, __FILE__);

   pearlrt::Log::info("****** t1SemaBlocked  -- do 10 slow terminates **********");
   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2SemaBlocked.activate(me);
      me->setLocation(__LINE__, __FILE__);
      me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,100000));
      me->setLocation(__LINE__, __FILE__);
      ts = T2SemaBlocked.getTaskState();

      if (ts != pearlrt::TaskCommon::BLOCKED) {
         printf("run as root? \n*** wrong task state (%d) (BLOCKED expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2SemaBlocked.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2SemaBlocked.getTaskState();
      me->setLocation(__LINE__, __FILE__);
      me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,100000));
      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("run as root?\n*** wrong task state (%d) (TERMINATED expected)\n", ts);
      }
   }

   printf("\nT1SemaBlocked  -- do 10 fast terminates\n");
   pearlrt::Log::info("****** t1SemaBlocked  -- do 10 fast terminates **********");
   me->setLocation(__LINE__, __FILE__);

   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2SemaBlocked.activate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2SemaBlocked.getTaskState();

      if (ts != pearlrt::TaskCommon::BLOCKED) {
         printf("run as root?\n*** wrong task state (%d) (BLOCKED expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2SemaBlocked.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2SemaBlocked.getTaskState();
      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("*** wrong task state (%d)(TERMINATED expected)\n", ts);
      }
   }

   me->setLocation(__LINE__, __FILE__);
   printf("Task T1 finished\n");
}

DCLTASK(T2SemaBlocked, pearlrt::Prio(2), pearlrt::BitString<1>(0)) {
//   printf("Task T2 started\n");
   me->setLocation(__LINE__, __FILE__);
   {
      pearlrt::Semaphore* s[2] = {&_s1, &_s2};
      me->setLocation(__LINE__, __FILE__);
      pearlrt::Semaphore::request(me, 2, s);
      me->setLocation(__LINE__, __FILE__);
   }
}


void t1BoltBlocked(pearlrt::Task* me) { 
   int ts;
   me->setLocation(__LINE__, __FILE__);
   printf("Task T1BoltBlocked started -- do 10 slow terminates\n");
   me->setLocation(__LINE__, __FILE__);

   pearlrt::Log::info("****** t1BoltBlocked  -- do 10 slow terminates **********");
   T2BoltBlocker.activate(me);
    me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,200000));

   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2BoltBlocked.activate(me);
      me->setLocation(__LINE__, __FILE__);
      me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,100000));
      me->setLocation(__LINE__, __FILE__);
      ts = T2BoltBlocked.getTaskState();

      if (ts != pearlrt::TaskCommon::BLOCKED) {
         printf("run as root? \n*** wrong task state (%d) (BLOCKED expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2BoltBlocked.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2BoltBlocked.getTaskState();
      me->setLocation(__LINE__, __FILE__);
      me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,100000));
      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("run as root?\n*** wrong task state (%d) (TERMINATED expected)\n", ts);
      }
   }

   printf("\nT1BoltBlocked  -- do 10 fast terminates\n");
   pearlrt::Log::info("****** t1SemaBlocked  -- do 10 fast terminates **********");
   me->setLocation(__LINE__, __FILE__);

   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2BoltBlocked.activate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2BoltBlocked.getTaskState();

      if (ts != pearlrt::TaskCommon::BLOCKED) {
         printf("run as root?\n*** wrong task state (%d) (BLOCKED expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2BoltBlocked.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2BoltBlocked.getTaskState();
      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("*** wrong task state (%d)(TERMINATED expected)\n", ts);
      }
   }

   me->setLocation(__LINE__, __FILE__);
   printf("Task T1BoltBlocked finished\n");
}

DCLTASK(T2BoltBlocker, pearlrt::Prio(2), pearlrt::BitString<1>(0)) {
//   printf("Task T2BoltBlocker started\n");
   me->setLocation(__LINE__, __FILE__);
   {
      pearlrt::Bolt* s[1] = {&_bolt};
      me->setLocation(__LINE__, __FILE__);
      pearlrt::Bolt::enter(me, 1, s);
      me->setLocation(__LINE__, __FILE__);
   }
}

DCLTASK(T2BoltBlocked, pearlrt::Prio(2), pearlrt::BitString<1>(0)) {
//   printf("Task T2 started\n");
   me->setLocation(__LINE__, __FILE__);
   {
      pearlrt::Bolt* s[1] = {&_bolt};
      me->setLocation(__LINE__, __FILE__);
      pearlrt::Bolt::reserve(me, 1, s);
      me->setLocation(__LINE__, __FILE__);
   }
}

int goOn = 1;

void terminated(pearlrt::Task * me) {
   bool ok = false;

   printf("Task T1terminated started \n");
   pearlrt::Log::info("****** t1terminated  **********");
   me->setLocation(__LINE__, __FILE__);
   try {
      T2running.terminate(me);
   } catch (pearlrt::TaskTerminatedSignal s) {
      printf("ok - got TerminatedSignal\n");
      ok = true;
   }
   me->setLocation(__LINE__, __FILE__);
   if (!ok) {
      printf("failed- did not get TerminatedSignal\n");
   }
}


void running(pearlrt::Task * me) {
   int ts;
   printf("Task T1running started -- do 10 slow terminates\n");
   pearlrt::Log::info("****** t1running  -- do 10 slow terminates **********");
   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2running.activate(me);
      me->setLocation(__LINE__, __FILE__);
      me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,100000));
      me->setLocation(__LINE__, __FILE__);
      ts = T2running.getTaskState();

      if (ts != pearlrt::TaskCommon::RUNNING) {
         printf("run as root? \n*** wrong task state (%d) (RUNNING expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2running.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2running.getTaskState();
      me->setLocation(__LINE__, __FILE__);
//      me->resume(pearlrt::Task::AFTER,
//                 pearlrt::Clock(), pearlrt::Duration(0,100000));
//      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("run as root?\n*** wrong task state (%d) (TERMINATED expected)\n", ts);
      }
   }
   printf("Task T1running started -- do 10 fast terminates\n");
   pearlrt::Log::info("****** t1running  -- do 10 fast terminates **********");
   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2running.activate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2running.getTaskState();

      if (ts != pearlrt::TaskCommon::RUNNING) {
         printf("run as root? \n*** wrong task state (%d) (RUNNING expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2running.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2running.getTaskState();
      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("run as root?\n*** wrong task state (%d) (TERMINATED expected)\n", ts);
      }
   }
   goOn = 0;  // enshure that task T2Running terminates
}

DCLTASK(T2running, pearlrt::Prio(20), pearlrt::BitString<1>(0)) {
//   printf("Task T2 started\n");
   me->setLocation(__LINE__, __FILE__);
   while (goOn) {
      me->setLocation(__LINE__, __FILE__);
   }
}

void suspended(pearlrt::Task * me) {
   int ts;
   printf("Task T1suspended started -- do 10 slow terminates\n");
   pearlrt::Log::info("****** t1suspended  -- do 10 slow terminates **********");
   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2suspended.activate(me);
      me->setLocation(__LINE__, __FILE__);
      me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,100000));
      me->setLocation(__LINE__, __FILE__);
      ts = T2suspended.getTaskState();

      if (ts != pearlrt::TaskCommon::SUSPENDED) {
         printf("run as root? \n*** wrong task state (%d) (SUSPENDED expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2suspended.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2suspended.getTaskState();
      me->setLocation(__LINE__, __FILE__);
      me->resume(pearlrt::Task::AFTER,
                 pearlrt::Clock(), pearlrt::Duration(0,100000));
      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("run as root?\n*** wrong task state (%d) (TERMINATED expected)\n", ts);
      }
   }
   printf("Task T1suspended started -- do 10 fast terminates\n");
   pearlrt::Log::info("****** t1suspended  -- do 10 fast terminates **********");
   for (int i = 0; i < 10; i++) {
      printf("test #%d\n", i);
      me->setLocation(__LINE__, __FILE__);
      T2suspended.activate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2suspended.getTaskState();

      if (ts != pearlrt::TaskCommon::SUSPENDED) {
         printf("run as root? \n*** wrong task state (%d) (RUNNING expected)\n", ts);
      }

      me->setLocation(__LINE__, __FILE__);
      T2suspended.terminate(me);
      me->setLocation(__LINE__, __FILE__);
      ts = T2suspended.getTaskState();
      me->setLocation(__LINE__, __FILE__);

      if (ts != pearlrt::TaskCommon::TERMINATED) {
         printf("run as root?\n*** wrong task state (%d) (TERMINATED expected)\n", ts);
      }
   }
}

DCLTASK(T2suspended, pearlrt::Prio(2), pearlrt::BitString<1>(0)) {
//   printf("Task T2 started\n");
   me->setLocation(__LINE__, __FILE__);
   me->suspend(me);
}

/**
\endcond
*/
