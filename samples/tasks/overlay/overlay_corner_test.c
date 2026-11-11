/* Regression test for OpenEmux's triangular descs:
 * input/input_overlay_corner.c, built from the tree. A skin's combo key
 * can be a right triangle in its rect (overlayN_descM_triangle), and
 * input_driver.c takes a touch on the rect as on the desc only when
 * input_overlay_corner_contains says it is inside the triangle. The
 * test checks:
 *
 *   1. the four corner names parse, anything else is no corner;
 *   2. each triangle holds its square corner, its centroid and the
 *      middle of its long edge, and not the opposite corner nor the
 *      far side of the long edge;
 *   3. no corner takes the whole rect, as a rect desc always did;
 *   4. the SNES case: a bottom-left triangle in the gap between Y
 *      (left) and B (below) is never touched over either button's
 *      circle, and touched all over its own triangle.
 */

#include <stdio.h>
#include <math.h>

#include <boolean.h>
#include <retro_miscellaneous.h>
#include <libretro.h>

#include "../../../input/input_overlay.h"

static unsigned failures = 0;

static void check(bool cond, const char *what)
{
   if (cond)
      printf("  [pass] %s\n", what);
   else
   {
      printf("  [FAIL] %s\n", what);
      failures++;
   }
}

static void test_parse(void)
{
   printf("parse\n");
   check(input_overlay_corner_parse("bottom-left")  == OVERLAY_CORNER_BOTTOM_LEFT,  "bottom-left");
   check(input_overlay_corner_parse("bottom-right") == OVERLAY_CORNER_BOTTOM_RIGHT, "bottom-right");
   check(input_overlay_corner_parse("top-left")     == OVERLAY_CORNER_TOP_LEFT,     "top-left");
   check(input_overlay_corner_parse("top-right")    == OVERLAY_CORNER_TOP_RIGHT,    "top-right");
   check(input_overlay_corner_parse("left")         == OVERLAY_CORNER_NONE,         "an unknown name is no corner");
   check(input_overlay_corner_parse("")             == OVERLAY_CORNER_NONE,         "an empty name is no corner");
   check(input_overlay_corner_parse(NULL)           == OVERLAY_CORNER_NONE,         "no name is no corner");
}

/* Each corner's square corner, as (sx, sy) signs: y down. */
static const float signs[5][2] = {
   { 0.0f, 0.0f }, { -1.0f, 1.0f }, { 1.0f, 1.0f }, { -1.0f, -1.0f }, { 1.0f, -1.0f }
};

static void test_triangles(void)
{
   uint8_t c;
   printf("triangles\n");
   for (c = OVERLAY_CORNER_BOTTOM_LEFT; c <= OVERLAY_CORNER_TOP_RIGHT; c++)
   {
      float sx = signs[c][0];
      float sy = signs[c][1];
      char what[96];
      sprintf(what, "corner %u: its square corner", c);
      check(input_overlay_corner_contains(c, sx, sy), what);
      sprintf(what, "corner %u: its centroid", c);
      check(input_overlay_corner_contains(c, sx / 3.0f, sy / 3.0f), what);
      sprintf(what, "corner %u: the middle of its long edge", c);
      check(input_overlay_corner_contains(c, 0.0f, 0.0f), what);
      sprintf(what, "corner %u: not the opposite corner", c);
      check(!input_overlay_corner_contains(c, -sx, -sy), what);
      sprintf(what, "corner %u: not just past its long edge", c);
      check(!input_overlay_corner_contains(c, -sx * 0.05f, -sy * 0.05f), what);
   }
   check(input_overlay_corner_contains(OVERLAY_CORNER_NONE, 1.0f, -1.0f)
         && input_overlay_corner_contains(OVERLAY_CORNER_NONE, -1.0f, 1.0f),
         "no corner: the whole rect");
}

/* The design's SNES cluster: Y centred (146, 170), B (210, 234), both
 * 30 in radius; the Y+B triangle's rect from (120, 206) to (176, 262),
 * its square corner bottom-left. Every point of the rect on a 1-unit
 * grid: none over Y or B is in the triangle, and every point clearly
 * inside the triangle is. */
static void test_snes_gap(void)
{
   const float left = 120.0f, top = 206.0f, side = 56.0f;
   const float cx = left + side / 2.0f, cy = top + side / 2.0f;
   unsigned over_button = 0, missed = 0;
   int i, j;
   printf("SNES gap\n");
   for (i = 0; i <= 56; i++)
      for (j = 0; j <= 56; j++)
      {
         float x  = left + i;
         float y  = top + j;
         bool in  = input_overlay_corner_contains(OVERLAY_CORNER_BOTTOM_LEFT,
               (x - cx) / (side / 2.0f), (y - cy) / (side / 2.0f));
         bool on_y = (x - 146.0f) * (x - 146.0f) + (y - 170.0f) * (y - 170.0f) <= 900.0f;
         bool on_b = (x - 210.0f) * (x - 210.0f) + (y - 234.0f) * (y - 234.0f) <= 900.0f;
         if (in && (on_y || on_b))
            over_button++;
         /* Inside the triangle by a unit of margin. */
         if (!in && (float)j - (float)i > 1.0f)
            missed++;
      }
   check(over_button == 0, "never touched over Y or B");
   check(missed == 0, "touched all over its own triangle");
}

int main(void)
{
   test_parse();
   test_triangles();
   test_snes_gap();

   if (failures)
   {
      printf("%u failure(s)\n", failures);
      return 1;
   }
   printf("triangles hold\n");
   return 0;
}
