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

/* OpenEmux: anchor mode (input_overlay_anchor). While it is on, the
 * first face, shoulder or trigger button a finger presses stays held
 * until that finger leaves the screen; any other button counts only
 * while the finger is over it.
 *
 * Each desc keeps, next to its touch_mask, the pointers that anchor it
 * (anchor_mask, by this poll's pointer index) and the ones that did on
 * the last poll (old_anchor_mask, by that poll's index), rotated with
 * the touch masks. Kept apart from input_driver.c so that
 * samples/tasks/overlay can compile this file as it is. */

#include <boolean.h>
#include <retro_miscellaneous.h>
#include <libretro.h>

#include "input_overlay.h"

/* The binds a desc may anchor: the face buttons, shoulders and
 * triggers. */
#define ANCHOR_BINDS ( (1u << RETRO_DEVICE_ID_JOYPAD_B)  \
                     | (1u << RETRO_DEVICE_ID_JOYPAD_Y)  \
                     | (1u << RETRO_DEVICE_ID_JOYPAD_A)  \
                     | (1u << RETRO_DEVICE_ID_JOYPAD_X)  \
                     | (1u << RETRO_DEVICE_ID_JOYPAD_L)  \
                     | (1u << RETRO_DEVICE_ID_JOYPAD_R)  \
                     | (1u << RETRO_DEVICE_ID_JOYPAD_L2) \
                     | (1u << RETRO_DEVICE_ID_JOYPAD_R2))

bool input_overlay_anchorable(const input_bits_t *mask)
{
   size_t i;
   for (i = 1; i < ARRAY_SIZE(mask->data); i++)
      if (mask->data[i])
         return false;
   return mask->data[0] && !(mask->data[0] & ~ANCHOR_BINDS);
}

/* Whether [desc] presses nothing: a "nul" button (an image, a toggle's
 * lit look, a button the console does not map), not an anchor toggle. */
static bool input_overlay_anchor_inert(const struct overlay_desc *desc)
{
   size_t i;
   if (     desc->type != OVERLAY_TYPE_BUTTONS
         || (desc->flags & OVERLAY_DESC_ANCHOR_TOGGLE))
      return false;
   for (i = 0; i < ARRAY_SIZE(desc->button_mask.data); i++)
      if (desc->button_mask.data[i])
         return false;
   return true;
}

/* Whether the pointer at [old_touch_idx] on the last poll was on a desc
 * that presses something: such a pointer has had its first press, and
 * anchors nothing new. */
static bool input_overlay_anchor_claimed(const struct overlay_desc *descs,
      size_t size, int old_touch_idx)
{
   size_t i;
   if (old_touch_idx < 0)
      return false;
   for (i = 0; i < size; i++)
      if (     BIT32_GET(descs[i].old_touch_mask, old_touch_idx)
            && !input_overlay_anchor_inert(&descs[i]))
         return true;
   return false;
}

bool input_overlay_anchor_apply(struct overlay_desc *descs, size_t size,
      int touch_idx, int old_touch_idx, input_bits_t *buttons)
{
   size_t i;
   bool held = false;

   /* The pointer's first press: every anchorable desc it is on. */
   if (!input_overlay_anchor_claimed(descs, size, old_touch_idx))
      for (i = 0; i < size; i++)
         if (     (descs[i].flags & OVERLAY_DESC_ANCHORABLE)
               && BIT32_GET(descs[i].touch_mask, touch_idx))
            BIT32_SET(descs[i].anchor_mask, touch_idx);

   /* What it anchored before stays pressed wherever it is now. */
   if (old_touch_idx >= 0)
      for (i = 0; i < size; i++)
      {
         struct overlay_desc *desc = &descs[i];
         if (!BIT32_GET(desc->old_anchor_mask, old_touch_idx))
            continue;
         BIT32_SET(desc->anchor_mask, touch_idx);
         BIT32_SET(desc->touch_mask,  touch_idx);
         bits_or_bits(buttons->data, desc->button_mask.data,
               ARRAY_SIZE(desc->button_mask.data));
         held = true;
      }

   return held;
}

bool input_overlay_anchor_toggled(const struct overlay_desc *descs,
      size_t size)
{
   size_t i;
   bool toggled = false;
   for (i = 0; i < size; i++)
      if (     (descs[i].flags & OVERLAY_DESC_ANCHOR_TOGGLE)
            && descs[i].touch_mask && !descs[i].old_touch_mask)
         toggled = !toggled;
   return toggled;
}

void input_overlay_anchor_rotate(struct overlay_desc *desc)
{
   desc->old_anchor_mask = desc->anchor_mask;
   desc->anchor_mask     = 0;
}

size_t input_overlay_anchor_feedback(const struct overlay_desc *descs,
      size_t size, const float *touch_x, const float *touch_y,
      unsigned touch_count, float *out, size_t max)
{
   size_t i, n = 0;
   unsigned t;
   for (t = 0; t < touch_count && t < 32; t++)
      for (i = 0; i < size; i++)
      {
         const struct overlay_desc *desc = &descs[i];
         float *e;
         if (!BIT32_GET(desc->anchor_mask, t))
            continue;
         if (n >= max)
            return n;
         e    = out + n++ * OVERLAY_ANCHOR_FEEDBACK_FLOATS;
         e[0] = desc->mod_x;
         e[1] = desc->mod_y;
         e[2] = desc->mod_w;
         e[3] = desc->mod_h;
         e[4] = desc->hitbox == OVERLAY_HITBOX_RECT ? 1.0f : 0.0f;
         e[5] = touch_x[t];
         e[6] = touch_y[t];
      }
   return n;
}
