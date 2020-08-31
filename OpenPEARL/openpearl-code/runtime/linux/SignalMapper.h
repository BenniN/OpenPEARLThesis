#ifndef SIGNALMAPPER_INCLUDED
#define SIGNALMAPPER_INCLUDED

/**
\addtogroup tasking_linux
*/

/**
mapper for system signals to OpenPEARL specific identifiers

Only the real-time signals are treated here.
The standard UNIX signals are mainly treated by the UnixSignal system device
*/



#include  <signal.h>

#define SIG_SUSPEND        (SIGRTMIN+0)
#define SIG_ACTIVATE       (SIGRTMIN+1)
#define SIG_RESUME         (SIGRTMIN+2)
#define SIG_CONTINUE       (SIGRTMIN+3)
#define SIG_NO_MORE_TASKS  (SIGRTMIN+4)
#define SIG_CANCEL_IO      (SIGRTMIN+5)
#define SIG_LAST_MAPPED    (SIG_CANCEL_IO)

#endif

/** @} */
