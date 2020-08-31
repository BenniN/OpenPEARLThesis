/*
 [A "BSD license"]
 Copyright (c) 2012-2016 Rainer Mueller
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
\mainpage
The PEARL run time system for Linux Plattforms.

\section Introduction

PEARL tasks are mapped on pthreads with the SCHED_RR-scheduler,
which is available on all up to date linux systems (since kernel 2.6).
There is a risc of locking  the system by a programming error -
e.g an endless loop in a user task, since SCHED_RR-threads work
with better priority than all other normal linux applications
(including shells). This risc is limited by setting a maximum
execution time for the PEARL application.

To get access to SCHED_RR root priviledges are required.
To work as normal user only the completely fair scheduler of linux is used.
The behavior of the task is not the same as required by the
PEARL specification.

The run time system is written in C++ in a separate namespace 'pearlrt'.

Linux allows the usage of many cpu cores.
During the startup of the project only 1 core is used.

\todo the number of cores to be used should be placed in
a configuration file


\section DT Data Types
The run time system provides data types which behave like the PEARL data types.
The data types are implemented as C++ classes.
The compiler may translate to user PEARL code into C++ using these types.

\subsection rt1 realized types
-# Task
-# Duration
-# Clock
-# Character<S> as template
-# Ref Character
-# Fixed(x), with x=1..63 as template
-# Fixed63 (for internal usage)
-# TaskList (for internal usage)
-# Bit (with size from 1 to 64)

\subsection rt2 to be done
-# Interrupt


\section Tasks
Beside of the simple data types, PEARL provides multitasking at language level.
The task concept of PEARL relies on common data.
PEARL tasks are mapped to linux threads.
For each PEARL task a task object must be created and an task-body function.
The task object provides the methods for the PEARL tasking language elements
The task-body function contains the compiled task code.
The creation of the requied C++-statements for the task object and
task-body header, C-macros are used.

PEARL provides only static tasks. This allows to create static task objects.
The constructor is called before entering main().
During construction of the task object, the task object registers
in the TaskList, which is a static object containing all tasks in
the PEARL application.

\subsection ts Task Scheduling
PEARL provides prowerful statements to schedule tasks.
It is possible to activate or continue task on time based rules
and on externalevents (interrupts in PEARL).
The time based actions are realized with timers and RT_SIGNALS.
The RT_SIGNALS are delivered to the target thread in a reliable
way (normal signals may be lost).
The signals are treated by reading a signalfd() descriptor for incoming
signals in a separate thread (signalThread). Each arriving signal a
dispatched to the corresponding tasling method. The signal context
contains a pointer to the target task-object.

The structure looks like:

\dot
digraph G {
   graph[size="6, 4"];
   subgraph cluster_os {
       label = "<<thread>>\nFile: os.cc\nmain";
       style=filled; color=grey;
       node[shape="rect"];
       main -> signalThread[label="creates"];
   }

   subgraph cluster_task_obj {
       label = "File: task.cc\n task object";
       node[shape="rect"];
       activateHandler[style=filled, color=grey];
       continueHandler[style=filled, color=grey];
       activate;
       resume;
       continue;
   }

   subgraph cluster_timer {
       label = "<<CLOCK_REALTIME>>\nTimer";
       node[shape="rect"];
       activateTimer;
       resumeTimer;
       continueTimer;
   }

   signalThread -> activateHandler[label="calls"];
   signalThread -> continueHandler[label="calls"];

   activate -> activateTimer[label="setup"];
   resume -> resumeTimer[label="setup"];
   continue -> continueTimer[label="setup"];
   activateHandler->activateTimer[label="stop"];

   continueTimer->signalThread[label="SIG_CONTINUE"];
   activateTimer->signalThread[label="SIG_ACTIVATE"];
   resumeTimer->signalThread[label="SIG_RESUME"];
}
\enddot
The colors indicate the active thread context.


\section Signals
PEARL was designed for experiment and automation control.
In the region of safety critical
systems, overflows of variable values should not occur without notice.
These situations are called SIGNAL in PEARL.
The term signal differs between PEARL and linux!
PEARL signals are a kind of exceptions.
Each runtime function throws exceptions in case of an unsafe situation.

All defined signals are derived from the class Signal.
The class Task provided the method setLocation(), which should be used
by the compiler as instrumentation of the code.
Before each executable source line, the filename and line number should be
set vie the me-pointer of the task.
The generic signal treatment routine just prints the last set location, the
execption type and exits the task-thread.


\section Logging
There is a tiny logging system to write messages (together with a time stamp)
on a disc file named pearl_log.txt' in the current working directory.
\todo It should be possible to configure the name for the log file and
the log level in an configuration file

\section I I/O system
The I/O-system is currently not planned.
The iostreams should not be used, since they are known be be not thread safe.
The standard library (glibc) functions are known be be thread safe.

\subsection ii Dations
\todo: dation implementation must be added here

\subsection Formatting
The formatting uses depending on the direction ether Source or Sink objects.
They are realized in separate classes. The connection between the Dations
and these obejcts must be provided by the dation implementation.

For output formatting (PUT) each data type has a formatting method in a
correspondig class (eq. PutFixed<S> for Fixed<S> types.

\todo realize the class PutUtils for positioning.

For input formatting (GET) each data type has a formatting method in a
correspondig class (eq. GetFixed<S> for Fixed<S> types.

The positioning is realized in the class GetUtils.

Example code:
\verbatim
// GET x FROM console BY T(13), SKIP;

pearlrt::Source* s = console.getSource();
pearlrt::GetClock::fromT(x, 13, 0, *s);
pearlrt::GetUtils::fromSkip(1, *s);
\endverbatim

\section st System start
On system start, some checks are done.
For details see the documentation of os.cc.
After all main-tasks became activated the system start thread wait
for user input.
A small command line interface is realized to monitor the system.

\todo realize a global timeout function to shutdown the complete application


\section cli Command Line Interface
There is a tiny command line interface.
All commands start with '/'.
List of commands:
- PRLI: list of all tasks
-

*/

/**
\file

\brief main part of the PEARL system (os)

\author R. Mueller




*/
#include <stdio.h>
#include <pthread.h>
#include <unistd.h>
#include <pwd.h>
#include <sys/resource.h> // get/set rlimit

#include "SignalMapper.h"
#include "Task.h"
#include "TaskTimer.h"
#include "TaskList.h"
#include "TaskMonitor.h"
#include "Log.h"
#include "Signals.h"
#include "Control.h"
#include "UnixSignal.h"
#include "Console.h" 

#include "LogFile.h"
#include "Disc.h"

/**
The namespace pearlrt is introduced to collect all symbols of the
runtime system apart from the user symbols coming from the PEARL source
code. By this way, the compiler may pass the user names directly to
C++ source code.
*/
namespace pearlrt {
// ------------------------ prototypes of static functions

  // data for scanPearlRc
  enum {LOGLEVEL = 0, MAXCPUTIME, CORES};
  static struct Entry {
     char  format[20];
     int value;
   } entries[] = {
      {"LogLevel   %x", -1},
      {"MaxCpuTime %d", 300},
      {"UseCores   %d", 1}
   };
   // cpu_set for task with no explicit  'Cores' statement
   static cpu_set_t * defaultCpuSet=NULL;
   static bool pearlRcFileOk = true;

//signal handler for SIGXCPU - cpu time limit exeeded
   static void sigXCPU(int dummy) {
      Log::error("CPU time exeeded - emergency stop now.");
      exit(1);
   }


   static void noMoreTasksPendingHandler(int sig) {
      printf("*** no more tasks active or scheduled --> stop\n");
      Log::info("no more tasks active or scheduled --> stop");
      sleep(1);
      Log::exit();
      exit(Control::getExitCode());
   }

   static void set_cores(int n) {
      cpu_set_t *mask;
      size_t size;
      int cpu;
      //allocate memory for a cpu-set for n cores
      mask = CPU_ALLOC(n);

      if (mask == NULL) {
         perror("CPU_ALLOC: ");
         exit(1);
      }

      //get the size in bytes of to hold cpu sets for n cores
      size = CPU_ALLOC_SIZE(n);

      //clear the cpu set
      CPU_ZERO_S(size, mask);

      // allow operation on all cores
      for (cpu = 0; cpu < n; cpu ++) {
         CPU_SET_S(cpu, size, mask);
      }

      //set the CPU_MASK for the process
      if (sched_setaffinity(0, size, mask) != 0) {
         perror("setaffinity:");
         exit(1);
      }

      CPU_FREE(mask);

   }

   static cpu_set_t * getCpuSet(char * line, char* taskName) {
      bool atLeast1CoreFound = false;
      cpu_set_t *cpuset;
      char * tok;

      cpuset = CPU_ALLOC(entries[CORES].value);
      size_t size = CPU_ALLOC_SIZE(entries[CORES].value);
      CPU_ZERO_S(size,cpuset);
      tok = strtok(line," ,\n");

      while(tok != NULL) {
         int core = atoi(tok);
         if (core >= entries[CORES].value) {
            if (taskName) {
               fprintf(stderr, ".pearlrc: core number %d for task %s too large --> ignored\n",
                                core, taskName);
            } else {
               fprintf(stderr, ".pearlrc: core number %d for 'DefaultCores' too large --> ignored\n",
                                core);
            }
          } else {
              atLeast1CoreFound = true;
              CPU_SET_S(core,size,cpuset);
          }
          tok = strtok(NULL," ,\n");
       }

       if (atLeast1CoreFound) {
          return cpuset;
       } else {
          CPU_FREE(cpuset);
          fprintf(stderr, ".pearlrc: no core defined in Cores/DefaultCores\n");
          pearlRcFileOk = false;
          return NULL;
       } 
   }

   static void scanPearlRc() {
      FILE * fp;
      int found;
      char line[80];
      
      // search .pearlrc in current folder
      fp = fopen("./.pearlrc", "r");

      if (fp == NULL) {
         // if not in current folder then search in $HOME
         struct passwd *pw = getpwuid(getuid());
         char fn[100];
         strcpy(fn, pw->pw_dir);
         strcat(fn, "/.pearlrc");
         fp = fopen(fn, "r");
      }

      if (fp != NULL) {
         while (!feof(fp)) {
            found = 0;
            fgets(line, sizeof(line) - 1, fp);
            if (!feof(fp) && line[0] != '!') {
               if (line[strlen(line)-1] == '\n') {
                  line[strlen(line)-1] = ' ';  // replace \n with space
               }
               // this is no comment line
               for (unsigned int i = 0;
                     i < sizeof(entries) / sizeof(entries[0]) && found == 0;
                     i++) {
                  found = sscanf(line, entries[i].format, &entries[i].value);
               }

               // check if it is the 'Cores' tag
               if (!found && strncmp(line,"Cores ",6) == 0) {
                   if (entries[CORES].value < 2) {
   	              fprintf(stderr, ".pearlrc: need more than 2 cores for statement 'Core'\n");
                      pearlRcFileOk = false;
                   }
                     
                   char * taskName = line+6;
                   while(isspace(*taskName)) {
                       taskName++;
                   }
                   char * startOfCores = taskName;
                   while(!isspace(*startOfCores)) {
                       startOfCores ++;
                   }
                   *startOfCores = '\0'; // set end of taskname
                   startOfCores ++;

		   cpu_set_t * set = getCpuSet(startOfCores,taskName);
                   if (set) {
                      Task *t = TaskList::Instance().getTaskByName(taskName);
                      if (t) {
                         t->setCpuSet(set);
                      } else {
                         fprintf(stderr, ".pearlrc: task %s not defined; Core-tag ignored\n",taskName);
                         pearlRcFileOk = false;
                      }
		   } 
               } else if (!found && strncmp(line,"DefaultCores ",13) == 0) {
                   if (entries[CORES].value < 2) {
   	              fprintf(stderr, ".pearlrc: need more than 2 cores for statement 'DefaultCores'\n");
                      pearlRcFileOk = false;
                   }
                     
		   defaultCpuSet = getCpuSet(line+13,NULL);
               } else if (!found) {
                   // let's ignore empty lines
                   char * isEmptyCheck = line;
                   while (isspace(*isEmptyCheck)) {
                       isEmptyCheck ++;
                   }
                   if (*isEmptyCheck != '\0') {
   	              fprintf(stderr, ".pearlrc: illegal command: '%s'\n",line);
                      pearlRcFileOk = false;
                   }
               }
            }
         }

         fclose(fp);
      } else {
         Log::info("no .pearlrc found");
      }
      return;
   }

   static void setLimits() {
      struct rlimit l;
      getrlimit(RLIMIT_CPU, &l);
      Log::info("old RLIMIT_CPU (soft/hard)=(%d,%d) sec",
                (int)l.rlim_cur,
                (int)l.rlim_max);
      l.rlim_cur = entries[MAXCPUTIME].value;
      l.rlim_max = l.rlim_cur + 1;
      setrlimit(RLIMIT_CPU, &l);
      Log::info("   set to (soft/hard)=(%d,%d) sec",
                (int)l.rlim_cur, (int)l.rlim_max);
   }
}  // of name space

using namespace pearlrt;

/**
system start

At system start several tests are performed to enshure proper working
of the system:
<ol>
<li> set number of used cores to 1 (in case of a multi core system <br>
     this may be deleted later
<li>  test scheduler availablity and available priority range
<li> setup signal handlers for the timers
<li> setup emergency timeout to shutdown the SCHED_RR-System in case
     of endless loops ...
<li> start all tasks with attribute MAIN in order of their priority
<li> do command line operations until shutdown
</ol>

\returns nothing useful, since the appilication terminates with exit()
*/
int main() {
   struct sched_param param;
   pthread_attr_t attr;
   int max, min;
   char line[80];
   int numberOfCpus;


   // setup default log file as defined in linux/Log.cc
   bool logFromSystemPart = pearlrt::Log::getInstance()->
		isDefinedInSystemPart();

   //Disc * disc = new Disc("./", 1);
   //LogFile * logfile = new LogFile(disc, "pearl_log.txt");
   //new Log(logfile, (char*)"EWDI"); // all levels enabled

   scanPearlRc();
   if (pearlRcFileOk == false) {
      exit(1);
   }

   if (!logFromSystemPart) {
      if (entries[LOGLEVEL].value != -1) {
          // set desired log level from .pearlrc
          Log::setLevel(entries[LOGLEVEL].value);
      }
   }
   // --- log setup complete ---


   numberOfCpus = sysconf(_SC_NPROCESSORS_ONLN);
   Log::info("PEARL system startup");
   sprintf(line, "number of CPUs: %d", numberOfCpus);
   Log::info(line);

   if (numberOfCpus >= entries[CORES].value) {
      numberOfCpus = entries[CORES].value;
   }

   set_cores(numberOfCpus);
   Task::setNumberOfCores(numberOfCpus);

   Log::info("   setting number of used cpus to %d", numberOfCpus);
   signal(SIGXCPU, sigXCPU);
   setLimits();

   //get min and max priority for the scheduling policy SCHED_RR
   if ((min = sched_get_priority_min(SCHED_RR)) == -1) {
      perror("sched_get_priority_min: ");
      exit(1);
   }

   if ((max = sched_get_priority_max(SCHED_RR)) == -1) {
      perror("sched_get_priority_max: ");
      exit(1);
   }

   param.sched_priority = max;
   Task::setThreadPrioMax(max);

   if (pthread_attr_init(&attr) != 0) {
      perror("init scheduling attributes");
      exit(1);
   }


   //set prio and schedulingpolicy for the main thread
   if (pthread_setschedparam(pthread_self(), SCHED_RR, &param) != 0) {
      Task::useNormalScheduler();
      Log::warn("error setting SCHED_RR --> using normal scheduler");
      TaskTimer::init(-1);  // setup timer thread
   } else {
      if (pthread_attr_setschedpolicy(&attr, SCHED_RR) == 0) {
         // ok we have access to SCHED_RR
         param.sched_priority = max; // schedPrioMax;

         if (pthread_attr_setschedparam(&attr, &param) != 0) {
            perror("error on setting priority");
            exit(1);
         }
      }

      TaskTimer::init(max);  // setup timer thread
      PrioMapper::getInstance()->logPriorities();
   }

   // register a signal handler for the situation where
   // the last active or pending task terminates
   signal(SIG_NO_MORE_TASKS, noMoreTasksPendingHandler);

   Log::info("Defined Tasks");
   sprintf(line, "  %-20.20s %4s %6s %s", "Name", "Prio", "isMain", "run on core");
   Log::info(line);
   TaskList::Instance().sort();	// sort taskList

   for (int i = 0; i < TaskList::Instance().size(); i++) {
      Task *t = TaskList::Instance().getTaskByIndex(i);
      char cores[40];
      cpu_set_t * set = t->getCpuSet();
      if (set==NULL && defaultCpuSet != NULL) {
          t->setCpuSet(defaultCpuSet);
          set=defaultCpuSet;
      }
      Task::getCpuSetAsText(set, cores, sizeof(cores)-1);
      sprintf(line, "  %-20.20s  %3d  %2d     %s", t->getName(),
              (t->getPrio()).x,
              t->getIsMain(), cores);
      Log::info(line);
   }

   if (TaskList::Instance().size() == 0) {
      printf("no task defined --> exit.\n");
      exit(1);
   }

   if (TaskList::Instance().getNbrOfMainTasks() == 0) {
      printf("no MAIN task defined --> exit.\n");
      exit(1);
   }

   /*****************init end*******************/
   //activate all threads which declared with "main"
   Log::info("start all main-threads");

   for (int i = 0; i < TaskList::Instance().size();  i++) {
      Task *t = TaskList::Instance().getTaskByIndex(i);

      if (t->getIsMain()) {
         t->activate(t);
      }
   }

   Log::info("system startup complete");

   if (Console::isDefined()) {
      Console* con;
      con = Console::getInstance();
      con->consoleLoop();
      // returns never !!
   } else {
      while (1) {
         //sleep all the time. if a timer expire, he will be restarted
         sleep(20);
      } 
   }

   // will never reach this point !
}
