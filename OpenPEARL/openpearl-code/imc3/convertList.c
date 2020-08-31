#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <unistd.h>

static char reservedBitListFromKconfig[200];
static int readPointer=0;
static int reservedBits = 0;

static void removeWhiteSpace() {
   int i,j=0;
   for (i=0; i<strlen(reservedBitListFromKconfig) ; i++) {
      if (!isspace(reservedBitListFromKconfig[i])) {
          reservedBitListFromKconfig[j++] = reservedBitListFromKconfig[i];
      }
   }
   reservedBitListFromKconfig[j] = '\0';
}

// returns 1, if int ist read, x contains read value;
//         0, else
static int getInt(int * x) {
   int result = 0;
   if (!isdigit(reservedBitListFromKconfig[readPointer])) {
       return 0;
   }
   
   while (isdigit(reservedBitListFromKconfig[readPointer])) {
      result *= 10;
      result += reservedBitListFromKconfig[readPointer]-'0';
      readPointer++;
   }
   *x = result;
   return(1);
}

int getNextChar() {
   return reservedBitListFromKconfig[readPointer];
}

void add(int x) {
    reservedBits |= 1<<x;
}


void treatParameter(char * s) {
   int help, x, y;
   //printf("treat .. %s\n" , s);
   // make local copy...
   strcpy(reservedBitListFromKconfig, s);
 
   removeWhiteSpace();
   readPointer=0;
   if (strlen(reservedBitListFromKconfig)== 0) {
      return;
   } else {
      do {
         help = getInt(&x);
         if (help == 0) {
             fprintf(stderr,"int value expected");
             _exit(1);
         }
	 add(x);
         help=getNextChar();
         if (help == '-') {
            readPointer++;
            help=getInt(&y);
            if (y <= x) {
               fprintf(stderr,"illegal range %d-%d\n", x,y);
               _exit(1);
            }
            if (help!=1) {
               fprintf(stderr,"expected int value\n");
               _exit(1);
            }
            for (; x<y; ) {
                add(++x);
            }
            help=getNextChar();
	    // printf("help= %c\n", help);
         }
         if (help == ',' ) {
           readPointer++;
         } else if (help == 0) {
	   // end of string
         } else {
           fprintf(stderr,"illegal operator %d%c\n", x,help);
           _exit(1);
         } 
      } while (help == ','); 
   }
}



int main(int narg, char ** argv) {

   int i,arg;

   //printf("narg=%d\n" , narg);
   reservedBitListFromKconfig[0] = '\0';
   if (narg < 2) {
      fprintf(stderr," at least one parameter required");
      _exit(-1);
   } 

   for  (arg=1; arg<narg; arg++) {
      treatParameter(argv[arg]);
   }

   // print bit list
   int firstBitFound = 0;
   for (i=0; i<32; i++) {
      if (reservedBits & (1<<i)) {
         if (!firstBitFound) {
            printf("%d",i);
           firstBitFound=1;
         } else {
             printf(",%d", i);
         }
      }
    }
}
