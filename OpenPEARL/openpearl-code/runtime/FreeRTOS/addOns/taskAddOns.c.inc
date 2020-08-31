
// the operation differs whether we use the standard FreeRTOS
// or the version for the ESP32, which provides symetric multi tasking
// if we detect portNUM_PROCESSORS to be defined we expect to be on the ESP32


UBaseType_t uxTaskGetCurrentFreeStack( void )
{
    UBaseType_t x; // place one value on the stack
    #if( portSTACK_GROWTH < 0 )
    {
       #ifdef portNUM_PROCESSORS
          x = (StackType_t*) &x - pxCurrentTCB [ xPortGetCoreID() ] -> pxStack;
       #else
          x = (StackType_t*) &x - pxCurrentTCB -> pxStack;
       #endif
    }
    #else /* portSTACK_GROWTH */
    {
       #ifdef portNUM_PROCESSORS
          x = pxCurrentTCB [ xPortGetCoreID() ] -> pxEndOfStack - (StackType_t*) &x;
       #else
          x = pxCurrentTCB  -> pxEndOfStack - (StackType_t*) &x;
       #endif
    }
    #endif
    return x;
}

