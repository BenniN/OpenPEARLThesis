#include "../../configuration/include/autoconf.h"

#include "FreeRTOSConfig.h"
#include "FreeRTOS.h"
#include "task.h"

// obtain access to the task control block
//typedef struct tskTaskControlBlock TCB_t;

// FreeRTOS types
StaticTask_t __attribute__((section ("TCB_T"))) tcb;
char __attribute__ ((section ("STACKTYPE"))) stack[sizeof(portSTACK_TYPE)];
char __attribute__ ((section ("PORTSHORT"))) shortT[sizeof(portSHORT)];
char __attribute__ ((section ("UBASETYPE"))) ubasetype[sizeof(UBaseType_t)];
char __attribute__ ((section ("StaticSemaphoreType"))) 
	uStaticSemaphoreType[sizeof(StaticSemaphore_t)];

// FatFs types
#ifdef CONFIG_HAS_FAT 
#include "ff.h"

char __attribute__ ((section ("FATFS"))) fatfs[sizeof(FATFS)];
char __attribute__ ((section ("FIL"))) fatFil[sizeof(FIL)];
char __attribute__ ((section ("VOLUMES"))) fatVol[sizeof(_VOLUMES)];
#endif
