/*
 [The "BSD license"]
 Copyright (c) 2012-2013 Rainer Mueller
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

#ifndef PRIORITYQUEUE_INCLUDED
#define PRIORITYQUEUE_INCLUDED

namespace pearlrt {
   class TaskCommon ;
}

//#include "TaskCommon.h"

namespace pearlrt {
   /**
   \addtogroup tasking_common
   @{
   */

   /**
   realize a queue of tasks which is ordered according the tasks
   priorities

   The insert methode inserts the given task according to its priority.
   The queue uses the pointers 'next' in the tasks TCB to realize the queue.
   */
   class PriorityQueue {
   private:
      TaskCommon* head;
   public:
      /**
         initialize the priority queue
      */
      PriorityQueue();

      /**
      insert a new element (task) at the correct position within the queue

      If there already tasks in the queue with the same priority than the
      given task, the new task will be inserted at the end of group
      of task with the same priority.

      \param x pointer to the task to be added into the queue
      */
      void insert(TaskCommon * x);

      /**
      remove the given element (task) from the queue.

      If the given task is not in the queue - nothing happens.

      \param x pointer to the task to be added into the queue

      \returns true, if the task was in the queue<br>
               false, if the task was not in the queue
      */
      bool remove(TaskCommon * x);

      /**
      retrieve the first element of the queue

      \returns pointer to the first task in the queue<br>
               or 0, if the queue is empty
      */
      TaskCommon* getHead();

      /**
      retrieve the next element of the queue
      \param x pointer to the current queue element

      \returns pointer to the next task in the queue<br>
               or 0, if the queue is empty
      */
      TaskCommon* getNext(TaskCommon * x);

   };

/**
@]
*/

}

#endif
