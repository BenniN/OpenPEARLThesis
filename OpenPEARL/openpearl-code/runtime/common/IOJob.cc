#include "IOJob.h"
#include "Clock.h"
#include "Duration.h"

namespace pearlrt {

   LoopControl::LoopControl(int nbrOfItems, bool cyclic) {
      loopLevel = -1;   // we are outside of all loops
      item = -1;
      this->numberOfItems = nbrOfItems;
      workCyclic = cyclic;
   }

   int LoopControl::next()  {
//printf("next Format( item: %d, loopLevel=%d\n",
//       item, loopLevel);
      // test if we are in an loop and last statment was reached
      while (loopLevel >= 0 &&
             item == loopControl[loopLevel].lastItem) {

//printf("end format loop: level=%d start=%d loops =%d last =%d\n",
// loopLevel, loopControl[loopLevel].startItem,
// loopControl[loopLevel].loops ,
// loopControl[loopLevel].lastItem);
         loopControl[loopLevel].loops--;

         if (loopControl[loopLevel].loops > 0) {
            // uncompleted inner loop - do at least one more
            // iteration
            item = loopControl[loopLevel].startItem;
            loopControl[loopLevel].offsetIncrement +=
              loopControl[loopLevel].sizeOfBody;
//printf("next --> %zu\n", item);

            return item;
         } else {
            // current loop level completed -- test next loop
            // level
            loopLevel --;
         }
      }

      // take next element from list (restarting on demand)
      item ++;

      if (workCyclic && item >= numberOfItems) {
         item = 0;
      }

//printf("next --> %zu\n", item);
      return item;
   }

   int LoopControl::enter(int nbrOfItems, int nbrOfRepetitions,
                          size_t sizeOfBody) {
      loopLevel ++;

      if (loopLevel == MAX_LOOP_LEVEL) {
         Log::error("too many nested formats");
         throw theDationFormatRepetitionOverflow;
      }

      if (nbrOfRepetitions < 0) {
         Log::error("number of repetitions must be >0");
         throw theDationFormatRepetitionValue;
      }

      loopControl[loopLevel].startItem = item + 1;
      loopControl[loopLevel].lastItem = item + nbrOfItems;
      loopControl[loopLevel].loops = nbrOfRepetitions;
      loopControl[loopLevel].sizeOfBody = sizeOfBody;
      loopControl[loopLevel].offsetIncrement = 0;

//printf("enter loop: level=%d start=%d loops =%d last =%d size=%zu offsetIncr = %zu Item=%d\n",
//                  loopLevel,
//                  loopControl[loopLevel].startItem,
//                  loopControl[loopLevel].loops ,
//                  loopControl[loopLevel].lastItem,
//                  loopControl[loopLevel].sizeOfBody,
//                  loopControl[loopLevel].offsetIncrement,
//                  item);
      item ++;  // skip enter statement
      return item;
   }

   size_t LoopControl::getOffset() {
       size_t result=0;

       if (loopLevel >= 0) {
           result = loopControl[loopLevel].offsetIncrement;
       }
//printf("\n getOffset=%zu\n", result);
       return result;
   }

   size_t IODataEntry::getSize() {
      size_t nbrOfBytes;

      switch (dataType.baseType) {
	case CHAR: ///< CHAR types
	   return dataType.dataWidth;
        case FLOAT: ///< FLOAT types
	   if (dataType.dataWidth <24) {
 		return sizeof (Float<23>);
	   } else {
 		return sizeof (Float<52>);
	   }
        case FIXED: ///< FIXED types
	   nbrOfBytes =	(dataType.dataWidth+8)/8;
	   return nbrOfBytes;
        case BIT:   ///< BIT types
	   nbrOfBytes =	(dataType.dataWidth+7)/8;
	   return nbrOfBytes;
        case CHARSLICE:
	   nbrOfBytes = // param1.numberOfElements;
	 	param2.charSliceLimits.upb.x -  
       	 	param2.charSliceLimits.lwb.x + 1; 
	   return nbrOfBytes;
        case CLOCK: ///< CLOCK types
	   return sizeof (Clock);
        case DURATION: ///< DURATION types
	   return sizeof (Duration);
	default:
	   printf("IODataType:get_size untreated type %d\n", dataType.baseType);
	   return 0;
      }
   }

    size_t IODataEntry::getStartOffset() {
       size_t result = 0;
       int lwb,upb;

       if (dataType.baseType == CHARSLICE) {
         lwb = param2.charSliceLimits.lwb.x - 1;
         upb = param2.charSliceLimits.upb.x - 1;

         if (lwb < 0 || lwb >= dataType.dataWidth) {
            Log::error("lwb (%d) out of range",lwb+1);
            throw theCharacterIndexOutOfRangeSignal;
         }
         if (upb < 0 || upb >= dataType.dataWidth) {
           Log::error("upb (%d) out of range",upb+1);
           throw theCharacterIndexOutOfRangeSignal;
         }
         if (upb < lwb) {
           Log::error("upb >= lwb violation (lwb:upb=%d:%d)",lwb+1,upb+1);
           throw theCharacterIndexOutOfRangeSignal;
         }
         result = lwb;
      }
      return (result); 
   }
}
