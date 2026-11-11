/*  RetroArch - A frontend for libretro.
 *  Copyright (C) 2011-2026 - The RetroArch team
 *
 *  RetroArch is free software: you can redistribute it and/or modify it under the terms
 *  of the GNU General Public License as published by the Free Software Found-
 *  ation, either version 3 of the License, or (at your option) any later version.
 *
 *  RetroArch is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 *  PURPOSE.  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with RetroArch.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

/* OpenEmux: triangular descs (overlayN_descM_triangle = bottom-left,
 * bottom-right, top-left or top-right). A skin's combo key can be a
 * right triangle that tucks into the gap between two round buttons:
 * its square corner points at one corner of its rect, its long edge
 * faces the buttons. A touch on the rest of the rect, over a
 * neighbouring button, must not press it, so the rect hitbox of such a
 * desc is cut along that long edge. Kept apart from input_driver.c so
 * that samples/tasks/overlay can test it as it ships. */

#include <string.h>

#include <boolean.h>

#include "input_overlay.h"

uint8_t input_overlay_corner_parse(const char *name)
{
   if (!name)
      return OVERLAY_CORNER_NONE;
   if (!strcmp(name, "bottom-left"))
      return OVERLAY_CORNER_BOTTOM_LEFT;
   if (!strcmp(name, "bottom-right"))
      return OVERLAY_CORNER_BOTTOM_RIGHT;
   if (!strcmp(name, "top-left"))
      return OVERLAY_CORNER_TOP_LEFT;
   if (!strcmp(name, "top-right"))
      return OVERLAY_CORNER_TOP_RIGHT;
   return OVERLAY_CORNER_NONE;
}

bool input_overlay_corner_contains(uint8_t corner, float dx, float dy)
{
   /* The long edge is a diagonal of the rect: the side of it the
    * square corner is on is inside, the edge itself included. */
   switch (corner)
   {
      case OVERLAY_CORNER_BOTTOM_LEFT:
         return dy >= dx;
      case OVERLAY_CORNER_TOP_RIGHT:
         return dy <= dx;
      case OVERLAY_CORNER_BOTTOM_RIGHT:
         return dx + dy >= 0.0f;
      case OVERLAY_CORNER_TOP_LEFT:
         return dx + dy <= 0.0f;
      default:
         break;
   }
   return true;
}
