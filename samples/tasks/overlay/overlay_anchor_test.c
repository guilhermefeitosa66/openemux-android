/* Regression test for OpenEmux's anchor mode: input/input_overlay_anchor.c,
 * built from the tree. input_driver.c tests each pointer against the
 * hitboxes (input_overlay_poll), then calls input_overlay_anchor_apply
 * while the mode is on, and rotates the masks once the poll is over
 * (input_overlay_post_poll, input_overlay_poll_clear). The test plays
 * those steps poll by poll on a row of descs along x:
 *
 *   1. only face buttons, shoulders and triggers are anchorable, a
 *      combo of them too; the D-pad, select/start, hotkeys and an
 *      empty ("nul") desc are not;
 *   2. with the mode on, the first button a finger presses stays held
 *      after the finger leaves it;
 *   3. hold Y, slide onto B: both pressed; slide off B: only Y; lift:
 *      nothing;
 *   4. a finger that started on a desc that cannot anchor (the D-pad,
 *      a quick action) anchors nothing it slides onto;
 *   5. a finger that lands between buttons, or on a desc that presses
 *      nothing (an image, a "nul" button), anchors the first one it
 *      reaches;
 *   6. the anchor follows the finger when the pointer indexes change
 *      (a lower finger lifts), and each finger keeps its own;
 *   7. with the mode off nothing is held: the masks stay empty;
 *   8. the toggle flips on its press, not while held, and two pressed
 *      in the same poll cancel out;
 *   9. the feedback reports each held button's box, shape and finger,
 *      one entry per holding finger, nothing once lifted, and never
 *      more entries than asked for.
 */

#include <stdio.h>
#include <string.h>

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

enum { DPAD = 0, Y, B, STRIP, TOGGLE, NUL, COUNT };

/* Each desc spans [x, x + 1) along the row, NUL (presses nothing)
 * right before Y; a gap at 9. */
static const float left[COUNT] = { 0.0f, 2.0f, 3.0f, 5.0f, 7.0f, 1.0f };
static struct overlay_desc descs[COUNT];

static void bind(unsigned d, unsigned id)
{
   BIT256_SET(descs[d].button_mask, id);
}

static void build(void)
{
   unsigned i;
   memset(descs, 0, sizeof(descs));
   bind(DPAD, RETRO_DEVICE_ID_JOYPAD_LEFT);
   bind(Y, RETRO_DEVICE_ID_JOYPAD_Y);
   bind(B, RETRO_DEVICE_ID_JOYPAD_B);
   bind(STRIP, RARCH_SAVE_STATE_KEY);
   descs[TOGGLE].flags |= OVERLAY_DESC_ANCHOR_TOGGLE;
   for (i = 0; i < COUNT; i++)
   {
      descs[i].type = OVERLAY_TYPE_BUTTONS;
      if (input_overlay_anchorable(&descs[i].button_mask))
         descs[i].flags |= OVERLAY_DESC_ANCHORABLE;
   }
}

/* One poll: [count] fingers at [xs], [old] each one's index on the last
 * poll (-1: new). Returns the buttons the overlay presses. */
static input_bits_t poll(bool anchor, unsigned count, const float *xs,
      const int *old)
{
   unsigned t, i;
   input_bits_t out;
   memset(&out, 0, sizeof(out));
   for (t = 0; t < count; t++)
   {
      for (i = 0; i < COUNT; i++)
         if (xs[t] >= left[i] && xs[t] < left[i] + 1.0f)
         {
            BIT32_SET(descs[i].touch_mask, t);
            bits_or_bits(out.data, descs[i].button_mask.data,
                  ARRAY_SIZE(out.data));
         }
      if (anchor)
         input_overlay_anchor_apply(descs, COUNT, (int)t, old[t], &out);
   }
   return out;
}

static void post_poll(void)
{
   unsigned i;
   for (i = 0; i < COUNT; i++)
   {
      descs[i].old_touch_mask = descs[i].touch_mask;
      descs[i].touch_mask     = 0;
      input_overlay_anchor_rotate(&descs[i]);
   }
}

/* One finger, a poll at each of [xs], tracked from one to the next. */
static input_bits_t slide(bool anchor, const float *xs, unsigned n)
{
   unsigned i;
   input_bits_t out;
   memset(&out, 0, sizeof(out));
   for (i = 0; i < n; i++)
   {
      int old = i ? 0 : -1;
      if (i)
         post_poll();
      out = poll(anchor, 1, &xs[i], &old);
   }
   return out;
}

static bool pressed(input_bits_t bits, unsigned id)
{
   return BIT256_GET(bits, id) != 0;
}

static void test_anchorable(void)
{
   input_bits_t m;
   unsigned ids[] = {
      RETRO_DEVICE_ID_JOYPAD_A, RETRO_DEVICE_ID_JOYPAD_B,
      RETRO_DEVICE_ID_JOYPAD_X, RETRO_DEVICE_ID_JOYPAD_Y,
      RETRO_DEVICE_ID_JOYPAD_L, RETRO_DEVICE_ID_JOYPAD_R,
      RETRO_DEVICE_ID_JOYPAD_L2, RETRO_DEVICE_ID_JOYPAD_R2 };
   unsigned not_ids[] = {
      RETRO_DEVICE_ID_JOYPAD_UP, RETRO_DEVICE_ID_JOYPAD_LEFT,
      RETRO_DEVICE_ID_JOYPAD_SELECT, RETRO_DEVICE_ID_JOYPAD_START,
      RETRO_DEVICE_ID_JOYPAD_L3, RETRO_DEVICE_ID_JOYPAD_R3,
      RARCH_TURBO_FIRE_TOGGLE, RARCH_BIND_LIST_END };
   unsigned i;
   bool all = true;

   puts("anchorable binds");
   for (i = 0; i < ARRAY_SIZE(ids); i++)
   {
      memset(&m, 0, sizeof(m));
      BIT256_SET(m, ids[i]);
      all = all && input_overlay_anchorable(&m);
   }
   check(all, "each face button, shoulder and trigger");
   memset(&m, 0, sizeof(m));
   BIT256_SET(m, RETRO_DEVICE_ID_JOYPAD_A);
   BIT256_SET(m, RETRO_DEVICE_ID_JOYPAD_B);
   check(input_overlay_anchorable(&m), "a combo of them");
   all = true;
   for (i = 0; i < ARRAY_SIZE(not_ids); i++)
   {
      memset(&m, 0, sizeof(m));
      BIT256_SET(m, not_ids[i]);
      all = all && !input_overlay_anchorable(&m);
   }
   check(all, "not the D-pad, select/start, L3/R3, a hotkey or an unknown bind");
   BIT256_SET(m, RETRO_DEVICE_ID_JOYPAD_A);
   check(!input_overlay_anchorable(&m), "not a face button with a hotkey");
   memset(&m, 0, sizeof(m));
   check(!input_overlay_anchorable(&m), "not a \"nul\" desc");
}

static void test_hold_and_slide(void)
{
   const float hold[] = { 2.5f, 2.5f, 6.0f, 9.5f };
   const float roll[] = { 2.5f, 3.5f };
   const float back[] = { 2.5f, 3.5f, 9.5f };
   const float lifted = 0.0f;
   input_bits_t out;

   puts("hold and slide");
   build();
   out = slide(true, hold, 2);
   check(pressed(out, RETRO_DEVICE_ID_JOYPAD_Y), "Y pressed under the finger");
   build();
   out = slide(true, hold, 4);
   check(pressed(out, RETRO_DEVICE_ID_JOYPAD_Y)
         && BIT32_GET(descs[Y].touch_mask, 0),
         "Y still held after the finger left it for empty space");

   build();
   out = slide(true, roll, 2);
   check(pressed(out, RETRO_DEVICE_ID_JOYPAD_Y)
         && pressed(out, RETRO_DEVICE_ID_JOYPAD_B),
         "Y held, B pressed while the finger is over it");
   check(!BIT32_GET(descs[B].anchor_mask, 0), "B is not anchored");
   build();
   out = slide(true, back, 3);
   check(pressed(out, RETRO_DEVICE_ID_JOYPAD_Y)
         && !pressed(out, RETRO_DEVICE_ID_JOYPAD_B),
         "off B: B released, Y still held");

   /* The finger lifts: the poll with no pointer (poll_clear) rotates. */
   post_poll();
   out = poll(true, 0, &lifted, NULL);
   check(!pressed(out, RETRO_DEVICE_ID_JOYPAD_Y)
         && !descs[Y].touch_mask && !descs[Y].anchor_mask,
         "lifting releases everything");
}

static void test_claimed(void)
{
   const float from_dpad[]  = { 0.5f, 2.5f, 9.5f };
   const float from_strip[] = { 5.5f, 3.5f, 9.5f };
   const float from_gap[]   = { 9.5f, 2.5f, 9.5f };
   const float from_nul[]   = { 1.5f, 2.5f, 9.5f };
   input_bits_t out;

   puts("first press only");
   build();
   out = slide(true, from_dpad, 3);
   check(!pressed(out, RETRO_DEVICE_ID_JOYPAD_Y)
         && !pressed(out, RETRO_DEVICE_ID_JOYPAD_LEFT),
         "a finger that started on the D-pad anchors neither it nor Y");
   build();
   out = slide(true, from_strip, 3);
   check(!pressed(out, RETRO_DEVICE_ID_JOYPAD_B)
         && !pressed(out, RARCH_SAVE_STATE_KEY),
         "a finger that started on a quick action anchors nothing");
   build();
   out = slide(true, from_gap, 3);
   check(pressed(out, RETRO_DEVICE_ID_JOYPAD_Y),
         "a finger landing between buttons anchors the first it reaches");
   build();
   out = slide(true, from_nul, 3);
   check(pressed(out, RETRO_DEVICE_ID_JOYPAD_Y),
         "a finger landing on a \"nul\" desc anchors the first button it reaches");
}

static void test_two_fingers(void)
{
   float xs[2];
   int old[2];
   input_bits_t out;

   puts("two fingers");
   build();
   /* Finger 0 on the D-pad, finger 1 lands on B. */
   xs[0] = 0.5f; old[0] = -1;
   poll(true, 1, xs, old);
   post_poll();
   xs[0] = 0.5f; old[0] = 0;
   xs[1] = 3.5f; old[1] = -1;
   poll(true, 2, xs, old);
   post_poll();
   /* The D-pad finger lifts: the B finger is index 0 now, was 1. */
   xs[0] = 9.5f; old[0] = 1;
   out = poll(true, 1, xs, old);
   check(pressed(out, RETRO_DEVICE_ID_JOYPAD_B)
         && !pressed(out, RETRO_DEVICE_ID_JOYPAD_LEFT),
         "B follows its finger to its new index, the D-pad is let go");
   check(BIT32_GET(descs[B].anchor_mask, 0)
         && !BIT32_GET(descs[B].anchor_mask, 1),
         "the anchor moved with it");
}

static void test_mode_off(void)
{
   const float hold[] = { 2.5f, 9.5f };
   input_bits_t out;
   unsigned i;
   bool clear = true;

   puts("mode off");
   build();
   out = slide(false, hold, 2);
   check(!pressed(out, RETRO_DEVICE_ID_JOYPAD_Y), "Y released when the finger leaves it");
   for (i = 0; i < COUNT; i++)
      clear = clear && !descs[i].anchor_mask && !descs[i].old_anchor_mask;
   check(clear, "no anchor mask is ever set");

   /* Anchored, then the mode goes off: the next poll lets go. */
   build();
   slide(true, hold, 2);
   post_poll();
   {
      int old = 0;
      out = poll(false, 1, &hold[1], &old);
   }
   post_poll();
   check(!pressed(out, RETRO_DEVICE_ID_JOYPAD_Y)
         && !descs[Y].old_anchor_mask,
         "turning the mode off releases an anchored button");
}

static void test_toggle(void)
{
   puts("toggle");
   build();
   check(!input_overlay_anchor_toggled(descs, COUNT), "no press, no flip");
   BIT32_SET(descs[TOGGLE].touch_mask, 0);
   check(input_overlay_anchor_toggled(descs, COUNT), "a new press flips it");
   descs[TOGGLE].old_touch_mask = descs[TOGGLE].touch_mask;
   check(!input_overlay_anchor_toggled(descs, COUNT), "held, it does not flip again");
   BIT32_SET(descs[Y].touch_mask, 0);
   descs[TOGGLE].old_touch_mask = 0;
   descs[STRIP].flags |= OVERLAY_DESC_ANCHOR_TOGGLE;
   BIT32_SET(descs[STRIP].touch_mask, 1);
   check(!input_overlay_anchor_toggled(descs, COUNT), "two toggles pressed together cancel out");
}

static void test_feedback(void)
{
   const float roll[] = { 2.5f, 3.5f };
   const float two[]  = { 2.5f, 3.5f };
   const float lifted = 0.0f;
   const int fresh[]  = { -1, -1 };
   float tx[2]        = { 0.4f, 0.7f };
   float ty[2]        = { 0.8f, 0.9f };
   float out[2 * OVERLAY_ANCHOR_FEEDBACK_FLOATS];
   size_t n;

   puts("feedback");
   build();
   descs[Y].mod_x  = 0.1f;
   descs[Y].mod_y  = 0.6f;
   descs[Y].mod_w  = 0.2f;
   descs[Y].mod_h  = 0.1f;
   descs[Y].hitbox = OVERLAY_HITBOX_RECT;
   descs[B].hitbox = OVERLAY_HITBOX_RADIAL;
   slide(true, roll, 2);
   n = input_overlay_anchor_feedback(descs, COUNT, tx, ty, 1, out, 2);
   check(n == 1, "one finger holding Y, one entry (B is only pressed)");
   check(out[0] == 0.1f && out[1] == 0.6f && out[2] == 0.2f
         && out[3] == 0.1f, "the entry is Y's drawn box");
   check(out[4] == 1.0f, "a rect button says so");
   check(out[5] == 0.4f && out[6] == 0.8f, "and where its finger is");

   build();
   poll(true, 2, two, fresh);
   n = input_overlay_anchor_feedback(descs, COUNT, tx, ty, 2, out, 2);
   check(n == 2 && out[4] == 0.0f
         && out[OVERLAY_ANCHOR_FEEDBACK_FLOATS + 5] == 0.7f,
         "two fingers, each its own entry and finger; B is round");
   n = input_overlay_anchor_feedback(descs, COUNT, tx, ty, 2, out, 1);
   check(n == 1, "never more entries than asked for");

   post_poll();
   poll(true, 0, &lifted, NULL);
   n = input_overlay_anchor_feedback(descs, COUNT, tx, ty, 0, out, 2);
   check(n == 0, "lifted, nothing to report");
}

int main(void)
{
   test_anchorable();
   test_hold_and_slide();
   test_claimed();
   test_two_fingers();
   test_mode_off();
   test_toggle();
   test_feedback();

   if (failures)
   {
      printf("%u failure(s)\n", failures);
      return 1;
   }
   printf("anchor mode holds\n");
   return 0;
}
