# shellcheck shell=bash
# The table tools (S21, S22): Side pots and Split the prizes (the deal maker). tour.sh sources this
# file right after the seat draw steps, so these run in the plain tour, in this order, between Seat
# draw and Backup: each tool opens from the Tools list (During play, End of the night) and goes back
# to it, and the last step scrolls the list back to its top, as the seat draw left it. They use
# tour.sh's helpers (ui, step, require_tab_selected).
#
# The Bank's night is over by now (Alice won), so Split the prizes has nobody left to take from the
# Bank and starts from three players to name; its prizes are typed, to check the ICM example worked
# by hand in DealMathTest.

# Side pots (S21) ----------------------------------------------------------------------------------
s_side_pots() {
  # From the Tools list (During play): a line on when it's for, three players, nothing in yet,
  # every field named by its player.
  ui scroll-to "text=Side pots" --max 4
  ui tap "text=Side pots"
  ui assert-text "text=Side pots" desc=Back "text~=When someone is all in" "text=WHO PUT IN WHAT" \
    "desc=Player 1 name" "desc=Chips Player 1 put in" "desc=Player 3 folded" || return 1
  require_tab_selected Tools
}
s_side_pots_all_in() {
  # Player 1 is all in for 100, Players 2 and 3 bet on to 300: a main pot of 300 for all three and
  # a side pot of 400 only Players 2 and 3 can win; the pots add up to the 700 put in.
  ui set-text "desc=Chips Player 1 put in" --value 100
  ui set-text "desc=Chips Player 2 put in" --value 300
  ui set-text "desc=Chips Player 3 put in" --value 300
  ui scroll-to "text=Adds up to 700, everything put in." --max 6
  ui assert-text "text=Side pot 1" "text=400" "text=Player 2 or Player 3 can win it" \
    "text=Over 100, up to 300 from each player" "text=Adds up to 700, everything put in." || return 1
  ui scroll-to "text=Main pot" --dir up --max 6
  ui assert-text "text=Main pot" "text=Player 1, Player 2 or Player 3 can win it" "text=Up to 100 from each player"
}
s_side_pots_fold() {
  # Player 3 folds: their chips stay in, but the side pot is Player 2's alone. New hand clears the
  # chips with Undo, which brings the hand back.
  ui scroll-to "desc=Player 3 folded" --dir up --max 6
  ui tap "desc=Player 3 folded"
  ui scroll-to "text=Adds up to 700, everything put in." --max 6   # the line under the side pot
  ui assert-text "text=Only Player 2 can win it" "text=Adds up to 700, everything put in." || return 1
  ui tap "desc=New hand"
  ui assert-text "text=New hand: chips cleared" text=UNDO || return 1
  ui tap text=UNDO
  ui wait-gone text=UNDO --timeout 15 || return 1
  ui scroll up --times 6                       # from the top, wherever the clear left the page
  ui scroll-to "text=Only Player 2 can win it" --max 8
  ui assert-text "text=Only Player 2 can win it"
}

# Split the prizes (S22, the deal maker) ------------------------------------------------------------
s_deal() {
  # From the Tools list (End of the night), with the night over nobody is left in the Bank: a line on
  # what a deal is, three players to name, and tonight's payouts.
  ui back                                      # Side pots -> the Tools list
  ui scroll-to "text=Split the prizes" --max 4
  ui tap "text=Split the prizes"
  ui assert-text "text=Split the prizes" desc=Back "text~=A deal: when the players left agree to stop" \
    "text=PLAYERS LEFT" "text=Typed for this deal" "desc=Chips Player 1 has" "desc=Start over" || return 1
  require_tab_selected Tools
}
s_deal_icm() {
  # 5,000 / 3,000 / 2,000 chips for $50 / $30 / $20: by ICM $38.39, $32.75 and $28.86 (the example
  # worked by hand in DealMathTest), by chip chop $40, $32 and $28. Each way adds up to $100. On a
  # phone the deal comes right after the players, and the prizes after the deal.
  ui set-text "desc=Chips Player 1 has" --value 5000
  ui set-text "desc=Chips Player 2 has" --value 3000
  ui set-text "desc=Chips Player 3 has" --value 2000
  ui scroll-to "text=Type them" --max 8
  ui tap "text=Type them"
  ui scroll-to "desc=3rd prize" --max 4
  ui set-text "desc=1st prize" --value 50
  ui set-text "desc=2nd prize" --value 30
  ui set-text "desc=3rd prize" --value 20
  ui enter                                     # a prize counts once you leave its field
  ui scroll-to "text=\$38.39" --dir up --max 8
  ui assert-text "text=\$38.39" "text=\$40.00" || return 1
  ui scroll-to "text=\$28.86" --max 4
  ui assert-text "text=\$32.75" "text=\$28.86" "text=\$28.00" || return 1
  ui scroll-to "text=Each way adds up to \$100." --max 4
  ui assert-text "text=Each way adds up to \$100." || return 1
  # Each way in a few words, above the amounts: the chip chop's floor is the smallest prize, $20
  ui scroll-to "text~=ICM: by each stack's chances" --dir up --max 8
  ui assert-text "text=ICM: by each stack's chances of finishing in each place." \
    "text=Chip chop: everyone gets the smallest prize left (\$20), and the rest goes by chips."
}
s_deal_winner() {
  # $10 saved for the winner: $90 is shared now, and the winner takes the $10 on top.
  ui scroll-to "desc=Save for the winner" --max 8
  ui set-text "desc=Save for the winner" --value 10
  ui enter
  ui scroll-to "text=Plus \$10 to whoever wins." --dir up --max 8   # just above "Each way adds up"
  ui assert-text "text=Plus \$10 to whoever wins." "text=Each way adds up to \$90."
}
s_deal_start_over() {
  # Start over reads tonight again with nothing typed; Undo brings the deal back.
  ui tap "desc=Start over"
  ui assert-text "text=Deal started over from tonight" text=UNDO || return 1
  ui tap text=UNDO
  ui wait-gone text=UNDO --timeout 15 || return 1
  ui scroll up --times 6                       # from the top, wherever Start over left the page
  ui scroll-to "text=Each way adds up to \$90." --max 8
  ui assert-text "text=Each way adds up to \$90."
}
s_table_tools_back() {
  # Back to the Tools list, scrolled to its top again (Chip set, the first tool), as the seat draw left it.
  ui back
  ui scroll-to "text=Chip set" --dir up --max 6
  ui assert-text "text=Chip set" text=Odds || return 1
  require_tab_selected Tools
}

step side-pots            "Side pots (S21): three players, nothing in"          s_side_pots
step side-pots-all-in     "100 all in, 300, 300: main 300, side 400"            s_side_pots_all_in
step side-pots-fold       "Player 3 folds: side pot to 2; New hand, Undo"       s_side_pots_fold
step deal                 "Split the prizes (S22): night over, three to name"   s_deal
step deal-icm             "5k/3k/2k for 50/30/20: ICM 38.39/32.75/28.86"        s_deal_icm
step deal-winner          "\$10 saved for the winner: \$90 shared"               s_deal_winner
step deal-start-over      "Start over, then Undo brings the deal back"          s_deal_start_over
step table-tools-back     "Back to the Tools list, at its top"                  s_table_tools_back
