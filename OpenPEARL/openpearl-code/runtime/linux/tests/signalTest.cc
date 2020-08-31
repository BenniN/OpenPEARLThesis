/*
Signal test program
*/
#include "PearlIncludes.h"

static pearlrt::FixedRangeSignal _overfl;
pearlrt::Signal *generalized_overfl = &_overfl;


static pearlrt::FixedDivideByZeroSignal _div0;
pearlrt::Signal *generalized_div0 = &_div0;

static pearlrt::ArithmeticSignal _arith;
pearlrt::Signal *generalized_arith = &_arith;

extern pearlrt::Signal *generalized_overfl;
extern pearlrt::Signal *generalized_div0;
extern pearlrt::Signal *generalized_arith;

SPCTASK(TASK1);

void _x(pearlrt::Task * me, pearlrt::Fixed<15> p) {
   pearlrt::Fixed<15> _k;

   //[ generated from ON statements in PROC
   static pearlrt::ScheduleSignalAction sigActions[] = {
		pearlrt::ScheduleSignalAction(generalized_arith),
		pearlrt::ScheduleSignalAction(generalized_overfl),
		pearlrt::ScheduleSignalAction(generalized_div0)};

   const size_t NBROFSIGACTIONS = 
		sizeof(sigActions)/sizeof(sigActions[0]);

   enum signalIndex{index_arith, index_overfl, index_div0};
   int indexOfSignalAction = -1; // no action -> execute proc body
   //]


      // local decls
      _k = pearlrt::Fixed<15>(2);

_restart:  // user label

   //[ autogenerated due to ON statements in PROC 
   //    (after decls before first statement but after fisrt label)
tryAgain:    // << autogen
   try {
      switch(indexOfSignalAction) {
          case -1: break;
          //[ generated from signal action #1 in PROC
          case 1:
             sigActions[index_arith].disable();
             printf("proc x: arithmetic error (returning)\n");
             return; // if in PROC; else me->terminate(me);
             sigActions[index_arith].enable();
          //]
          //[ generated from signal action #2 in PROC
          case 2:
             sigActions[index_overfl].disable();
             printf("proc x: Overflow occured (terminating)\n");
             sigActions[index_overfl].enable();
             me->terminate(me);
          //]
          //[ generated from signal action #3 in PROC
          case 3: 		 // signal action #3
             sigActions[index_overfl].disable();
                printf("proc x: Overflow occured (returning)\n");
             sigActions[index_overfl].enable();
               return; // if in PROC; else me->terminate(me);
          //]
          //[ generated from signal action #4 in PROC
	  case 4:               // signal action #4
printf("reaction#4\n"); 
              // disable div0 for this handler
              sigActions[index_div0].disable();
                // code for PUT 'PROC X: divide by 0 (restarting)'
                // TO console BY A, SKIP;
              printf("proc x: divide by 0 (restarting)\n");
              if ((p == pearlrt::Fixed<15>(6)).getBoolean()) {
                 sigActions[index_div0].enable();
                 goto _exit;                 
              } 
              if ((p == pearlrt::Fixed<15>(12)).getBoolean()) {
printf("create new divBy0\n");
	         _k = pearlrt::Fixed<15>(11)/(_k-_k);
              } 
              
              p = pearlrt::Fixed<15>(6);
              sigActions[index_div0].enable();
              goto _restart;                 
              
          //]
      } // end of switch
   //]

   printf("---  x(%d) ---------------\n",p.x);


      //[ generated from ON
      sigActions[index_arith].setActionIndex(1);
      //]
       
      if ((p == pearlrt::Fixed<15>(1)).getBoolean()) {
         //[ generated from ON
         sigActions[index_overfl].setActionIndex(2);
      //]
      }

      if ((p > pearlrt::Fixed<15>(5)).getBoolean()) {
         //[ generated from ON
         sigActions[index_overfl].setActionIndex(3);
         //]
      }
     
      //[ generated from ON
      sigActions[index_div0].setActionIndex(4);
      //]

      if ((p == pearlrt::Fixed<15>(11)).getBoolean()) {
printf("10/(k-k)\n");
         _k = pearlrt::Fixed<15>(10)/(_k-_k);
      }

      if ((p == pearlrt::Fixed<15>(12)).getBoolean()) {
printf("10/(k-k)\n");
         _k = pearlrt::Fixed<15>(10)/(_k-_k);
      }

      {
         pearlrt::Fixed<15> _i;    // loop counter is auto gen.
         for (_i = 1;
            (_i<=pearlrt::Fixed<15>(100)).getBoolean(); 
            _i = _i + pearlrt::Fixed<15>(1)) {
printf("%d: %d\n",_i.x, _k.x);
             _k =  _k * _k;
         }
      }
_exit:    //<< user label
	/* nop */ ;
   //[ generated due to ON statements in PROC
   } catch (pearlrt::Signal & s) {
      indexOfSignalAction = pearlrt::ScheduleSignalAction::getAction(
		&s, NBROFSIGACTIONS, sigActions);
      printf("got exception: indexOfAction=%d\n", indexOfSignalAction);
      if (indexOfSignalAction == 0) {
        // no active handler found
        throw;
      }
      goto tryAgain;
   } // end of catch
   //] end of generated code
}


DCLTASK(TASK1,pearlrt::Prio(2),pearlrt::BitString<1>(1)) {
    pearlrt::Fixed<15> f;

    f = 0;
    _x(me, f);
    f = 11;
    _x(me, f);
    f = 10;
    _x(me, f);
    f = 1;
    _x(me, f);
    f=12;
    _x(me,f);
}

