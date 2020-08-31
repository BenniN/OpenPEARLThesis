/*
 [A "BSD license"]
 Copyright (c) 2013-2014 Florian Mahlecke
 Copyright (c) 2016-2019 Rainer Mueller
 
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

/*
 * Task.cc
 *
 *  Created on: 08.02.2014
 *  Author: Florian Mahlecke
 */
#define TASK_SELF_TLS 1

/**
 \file

 \brief Base class for FreeRTOS based tasks

 */

#include <string.h>

#include "FreeRTOS.h"
#include "task.h"
#include "allTaskPriorities.h"

#include "TaskMonitor.h"
#include "TaskList.h"
#include "PrioMapper.h"
#include "Semaphore.h"
#include "Log.h"
#include "service.h"
#include "Task.h"

namespace pearlrt {

//          remove this vv comment to enable debug messages
#define DEBUG(fmt, ...) // Log::debug(fmt, ##__VA_ARGS__)

   Task* Task::currentTask(void) {
      Task* mySelf;

      mySelf = (Task*)(pvTaskGetThreadLocalStoragePointer(NULL,TASK_SELF_TLS));

      return mySelf;
   }

   /*********************************************************************//**
    * @brief		Constructor for FreeRTOS based tasks
    * @param[in]	n task name
    * @param[in]	prio PEARL90 priority
    * @param[in]	ismain PEARL90 isMain flag
    *
    * <b>Note:</b> When using UART in BLOCKING mode,
    *               a time-out condition is used
    * via defined symbol UART_BLOCKING_TIMEOUT.
    **********************************************************************/

   Task::Task(char* n, Prio prio, BitString<1> ismain) :
      TaskCommon(n, prio, ismain) {

      schedActivateData.taskTimer = &activateTimer;
      schedContinueData.taskTimer = &continueTimer;

      // FreeRTOS part
      stackDepth = sizeof(stack)/sizeof(stack[0]);

      xth = pdFALSE;
      TaskList::Instance().add(this);
   }

   void Task::init() {
      ((TaskTimer*)schedActivateData.taskTimer)->create(this,
            0,
            activateHandler);
      ((TaskTimer*)schedContinueData.taskTimer)->create(this,
            0,
            continueHandler);
   }

   void Task::directActivate(const Fixed<15>& prio) {
      bool freeRtosRunning;
#ifdef USE_FREERTOS_8_0
      StructParameters_t taskParams;
#endif
      int cp=0;  // current prio of calling task

      int freeRtosPrio = PrioMapper::getInstance()->fromPearl(prio.x);

      DEBUG("%s::directActivate freeRTOSprio=%d", name, freeRtosPrio);

      BaseType_t taskCreation = pdFALSE;

      /*
      note: the call of TaskMonitor::...::incPendingTasks()
            is already done in Taskcommon::activate()
      */

      // the scheduling may only be stopped, if the scheduler
      // was started - the creation of MAIN-tasks need no
      // stop/start of the scheduler, since FreeRTOS is started later
      freeRtosRunning =
         (xTaskGetSchedulerState() != taskSCHEDULER_NOT_STARTED);

      if (freeRtosRunning) {
         cp = switchToThreadPrioMax();
      }

      asyncSuspendRequested = false;
      asyncTerminateRequested = false;
      terminateWaiters = 0;

      xth = xTaskCreateStatic(
                        &wrapperTskfunc,
                        (const char*) this->name,
                        this->stackDepth,
                        (void*) this,
                        freeRtosPrio,
                        stack,
                        ((StaticTask_t*) &tcb));


      if (!taskCreation) {
         DEBUG("%s: started", name);
         taskState = RUNNING;
      } else {
         Log::error("%s: not created", name);
         throw theInternalTaskSignal;
      }

      vTaskSetThreadLocalStoragePointer((TaskHandle_t)xth,TASK_SELF_TLS,this);

      DEBUG("%s::activated ", name);

      activateDone.release();

      if (freeRtosRunning) {
         switchToThreadPrioCurrent(cp);
      }

   }


   void Task::entry() {
      schedActivateOverrun = false;
   }

   void Task::wrapperTskfunc(void * param) {
       tskfunc(param);
   }


   void Task::tskfunc(void* param) {
      Task* me = ((Task*)param);

      me->entry();
      DEBUG("%s: starts", me->getName());

      try {
         me->task(me);
      } catch (Signal & p) {
         {
            int f = uxTaskGetStackHighWaterMark(NULL);
            printf("Task stack usage: %d\n", f);
         }
         Log::error("++++++++++++++++++++++++++++++");
         Log::error("%s:%d Task: %s   terminated due to: %s",
                    me->getLocationFile(), me->getLocationLine(),
                    me->getName(), p.which());
         Log::error("++++++++++++++++++++++++++++++");
      }

      me->terminate(me);
   }


   void Task::terminateMySelf() {
      TaskHandle_t oldTaskHandle = (TaskHandle_t)xth;
      DEBUG("%s: terminateSelf", name);

      // set the calling tasks priority to maximum to be shure that
      // the following code - especially the restarting of the task itself
      // until vTaskDelete() is executed without task switch
      switchToThreadPrioMax();

      if (schedActivateOverrun) {
         /* leave the tasks state to NOT TERMINATED.
            This make the system insensitiv to retriggering the
            task activation during the time until the automatic restart
            of the task
         */
         ServiceJob s = {(void (*)(void *))restartTaskStatic, this};

         DEBUG("%s: terminates with schedOverrun flag - start task again",
                    name);

         /* note: the current task prio is "RunToCompletion"
                  there will be no taskswitch on a single core
                  cpu, when the service tasks receives the job
                  to start the task --> this tasks completes and
                  then it will be restarted
         */
         printf("dispatch restart\n");
         add_service(&s);
      } else {
         /* set the tasks state to terminated */
         taskState = TERMINATED;

         if (schedActivateData.taskTimer->isActive() == false &&
               schedActivateData.whenRegistered == false) {
            TaskMonitor::Instance().decPendingTasks();
         } else {
            // do nothing
         }
      }

      while (terminateWaiters > 0) {
         terminateWaiters --;
         terminateDone.release();
      }

      // this block is useful for stack optimizations
      {
         int f = uxTaskGetStackHighWaterMark(NULL);
         printf("Task %s stack usage: %d\n", name, f);
      }

      mutexUnlock();
      vTaskDelete(oldTaskHandle);
   }

   void Task::terminateIO() {
Log::debug("%s: terminateIO called", name);
      asyncTerminateRequested = true;
      // be not disturbed by application threads
      switchToThreadPrioMax();
      {

         blockParams.why.u.io.dation->terminate(this);
      }
   }

   void Task::terminateSuspended() {
       DEBUG("%s: terminateSuspended", name);
       terminateRunning();
   }

   void Task::terminateRunning() {
      int cp; // current calling tasks priority

       DEBUG("%s: terminateRunning waiters=%d", name, terminateWaiters);

      // set the calling threads priority to maximum priority
      // to enshure the execution of this function without task switch
      cp = switchToThreadPrioMax();

         {
            int f = uxTaskGetStackHighWaterMark((TaskHandle_t)this->xth);
            printf("Task stack usage: %d\n", f);
         }

      taskState = TERMINATED;
      vTaskDelete((TaskHandle_t)(this->xth));
      DEBUG("%s: terminateRunning .. done", name);

      // test if the a scheduled activation was not performed due to 
      // the task was still active. Restart the task right now
      if (schedActivateOverrun) {
         schedActivateOverrun = false;
         directActivate(schedActivateData.prio);
      } else {
         if (schedActivateData.taskTimer->isActive() == false &&
               schedActivateData.whenRegistered == false) {
            mutexUnlock();
            TaskMonitor::Instance().decPendingTasks();
         } else {
            mutexUnlock();
         }
      }

      while(terminateWaiters > 0) {
         terminateWaiters --;
         terminateDone.release();
      }

      switchToThreadPrioCurrent(cp);
   }

   void Task::terminateSuspendedIO() {
         Log::warn("terminate remote in suspended i/o blocked not supported");
   }


   void Task::suspendMySelf() {
      int cp; // current calling threads priority
      TaskState previousTaskState;

      DEBUG("%s: suspendMyself  taskState=%d", name, taskState);

      previousTaskState = taskState;

      switch (taskState) {
      case RUNNING:
         taskState = Task::SUSPENDED;
         break;

      case BLOCKED:
         taskState= Task::SUSPENDED_BLOCKED;
         break;

      default:
         Log::error("%s:  suspendMySelf: unknown taskState =%d",
                    name, taskState);
         mutexUnlock();
         throw theInternalTaskSignal;
      }


      // set the priority of this thread to maxium priority to
      // enshure the consecutive operation until at least vTaskSuspend()
      // after continuation the thread is executed until the active task
      // state is set again.
      cp = switchToThreadPrioMax();
   
      while (suspendWaiters > 0) {
         suspendWaiters--;
         suspendDone.release();
      }

      mutexUnlock();
      DEBUG("%s: suspendMySelf: go into suspend state", name);
      vTaskSuspend((TaskHandle_t)xth);
      DEBUG("%s: suspended - got continue", name);
      mutexLock();

      taskState = previousTaskState;
      switchToThreadPrioCurrent(cp);

      DEBUG("%s: suspendMyself: continue done", name);

   }

   void Task::suspendRunning() {
      int cp; // current calling threads priority
      DEBUG("%s: suspendrequest in RUNNING mode", name);
      suspendWaiters ++;
      cp = switchToThreadPrioMax();
      taskState = SUSPENDED;
      vTaskSuspend((TaskHandle_t)(this->xth));
      switchToThreadPrioCurrent(cp);
      suspendDone.request();
   }


   void Task::suspendIO() {
      int cp;
      DEBUG("%s: suspendIO: started state=%d", name, taskState);
      asyncSuspendRequested = true;

      suspendWaiters ++;
      // be not disturbed by application threads
      cp = switchToThreadPrioMax();
      {

         blockParams.why.u.io.dation->suspend(this);
         suspendDone.request();
      }
      switchToThreadPrioCurrent(cp);
      DEBUG("%s: suspendIO: done, state=%d", name, taskState);
   }


   void Task::continueSuspended() {
      DEBUG("%s: continue suspended state=%d", name, taskState);
      vTaskResume((TaskHandle_t)xth);
      DEBUG("%s: continue suspended ... done. state=%d",name, taskState);
      // update of taskState and release of mutexTask is done in
      // continued task
   }

   void Task::setPearlPrio(const Fixed<15>& prio) {
      currentPrio = prio;

      int p = PrioMapper::getInstance()->fromPearl(prio);
      vTaskPrioritySet((TaskHandle_t)xth, p);
   }

   int Task::switchToThreadPrioMax() {
      int cp;
      cp = uxTaskPriorityGet(NULL);
      vTaskPrioritySet(NULL, PRIO_TASK_MAX_PRIO);
      return cp;
   }

   void Task::switchToThreadPrioCurrent(int cp) {
      vTaskPrioritySet(NULL, cp);
   }

  void Task::treatCancelIO(void) {
      DEBUG("%s: treatCancelIO: termReq=%d suspeReq=%d", name
             , asyncTerminateRequested, asyncSuspendRequested);

      // we do not need to lock the global task lock, since this
      // was already done where the signal was raises in suspendFromOtherTask
      // or terminateFromOtherTask.


      if (asyncSuspendRequested) {
         asyncSuspendRequested = false;

         DEBUG("%s: Task::treatIOCancelIO: suspending ...", name);
         suspendMySelf();
         DEBUG("%s: Task::treatIOCancelIO: continued: termReq=%d suspeReq=%d",
              name , asyncTerminateRequested, asyncSuspendRequested);

         if (! asyncTerminateRequested) {
            // we must unlock the mutex at this point, due to the asymmetry
            // at suspendIO().
            // if there is a terminate request pending, the unlock is done
            // in the next if statement
            mutexUnlock();
         }
      }

      if (asyncTerminateRequested) {
         asyncTerminateRequested = false;
         DEBUG("%s: terminate during system IO device", name);
         mutexUnlock();
         throw theTerminateRequestSignal;
      }
   }


#ifdef unused
   TaskHandle_t Task::getFreeRTOSTaskHandle() {
      if (taskState != TERMINATED) {
         return (TaskHandle_t)xth;
      }

      return NULL;
   }
#endif
   void Task::restartTaskStatic(Task * t) {
      t->restartTask();
   }

   void Task::restartTask() {
      // directActivate must be called with all tasks mutex beeing locked
      mutexLock();
      directActivate(schedActivateData.prio);
      mutexUnlock();
   }

}

