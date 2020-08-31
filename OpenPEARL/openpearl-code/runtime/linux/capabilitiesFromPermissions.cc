#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include "Dation.h"
#include "capabilitiesFromPermissions.h"

namespace pearlrt {

   int capabilitiesFromPermissions(struct stat & attribut) {
      int cap = 0;

      if (geteuid() == attribut.st_uid) {
         //  am owner of the folder
         if (attribut.st_mode & S_IRUSR) {
            cap |= Dation::IN;
         }

         if (attribut.st_mode & S_IWUSR) {
            cap |= Dation::OUT; 
         }

         if (attribut.st_mode & (S_IRUSR | S_IWUSR)) {
            cap |= Dation::INOUT;
         }
      }

     if (getegid() == attribut.st_gid) {
         //  am in the same group as the folder
         if (attribut.st_mode & S_IRGRP) {
            cap |= Dation::IN;
         }

         if (attribut.st_mode & S_IWGRP) {
            cap |= Dation::OUT;
         }

         if (attribut.st_mode & (S_IRGRP | S_IWGRP)) {
            cap |= Dation::INOUT;
         }
      }
      // permissions of OTHERs apply also
      if (attribut.st_mode & S_IROTH) {
         cap |= Dation::IN;
      }

      if (attribut.st_mode & S_IWOTH) {
         cap |= Dation::OUT;
      }

      if (attribut.st_mode & (S_IROTH | S_IWOTH)) {
         cap |= Dation::INOUT;
      }

      if (cap & Dation::OUT) {
         cap |= (Dation::NEW | Dation::ANY | Dation::PRM | Dation::CAN);
      }

      if (cap & Dation::IN) {
         cap |= (Dation::OLD | Dation::ANY);
      }

      return cap;
   }

}

