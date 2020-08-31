#include <sys/stat.h>

namespace pearlrt {

   /**
    scan the attribut parameter and create a capabilities flags for the
    system dation

   \param attribut the file attributes
   \returns the corresponding capabilities bitmap

   */
   int capabilitiesFromPermissions(struct stat & attribut);
}
