# shellcheck shell=bash
# The table tools (S20 to S22): Outs & pot odds, Side pots and Deal maker. tour.sh sources this file
# right after the seat draw steps, so these run in the plain tour, in this order, between Seat draw
# and Odds: each tool opens from the Tools list and goes back to it, and the last step scrolls the
# list back to its top for the Odds steps. They use tour.sh's helpers (ui, step, require_tab_selected).
#
# The Bank's night is over by now (Alice won), so the deal maker has nobody left to take from the
# Bank and starts from three players to name; its prizes are typed, to check the ICM example worked
# by hand in DealMathTest.

# Outs & pot odds (S20) ----------------------------------------------------------------------------
s_outs() {
  # Opens on a flush draw on the flop: exactly 35.0% by the river (378 of the 1,081 pairs of cards
  # to come) and 19.1% on the next card, with the rules of 4 and 2 beside them. Tools stays selected.
  ui scroll-to "text=Outs & pot odds" --max 4
  ui tap "text=Outs & pot odds"
  ui assert-text "text=Outs & pot odds" desc=Back "text=9 outs on the flop" "desc=Increase Outs" \
    "desc=Gutshot, 4 outs" || return 1
  require_tab_selected Tools || return 1
  ui scroll-to "text=Rule of 2: 18%" --max 4
  ui assert-text "text=35.0%" "text=Rule of 4: 36%" "text=19.1%" "text=Rule of 2: 18%"
}
s_outs_turn() {
  # On the turn a gutshot (a common draw, one tap) has 4 of the 46 unseen cards: 8.7%.
  ui scroll-to "text=On the turn" --dir up --max 4
  ui tap "text=On the turn"
  ui tap "desc=Gutshot, 4 outs"
  ui assert-text "text=4 outs on the turn" || return 1
  ui scroll-to "text=Rule of 2: 8%" --max 4
  ui assert-text "text=On the river" "text=8.7%" "text=Rule of 2: 8%"
}
s_outs_pot_odds() {
  # 300 in the pot with the bet, 100 to call: the call needs 25.0% (3 to 1), so the gutshot is short.
  ui scroll-to "desc=Pot, with the bet" --max 4
  ui set-text "desc=Pot, with the bet" --value 300
  ui set-text "desc=To call" --value 100
  ui scroll-to "text=You need 25.0% to call" --max 4
  ui assert-text "text=You need 25.0% to call" "text=Pot odds 3 to 1" \
    "text=River 8.7% against 25.0% needed: too short to call."
}

# Side pots (S21) ----------------------------------------------------------------------------------
s_side_pots() {
  # From the Tools list: three players, nothing in yet, every field named by its player.
  ui back                                      # Outs & pot odds -> the Tools list
  ui scroll-to "text=Side pots" --max 4
  ui tap "text=Side pots"
  ui assert-text "text=Side pots" desc=Back "text=Who put in what" "desc=Player 1 name" \
    "desc=Chips Player 1 put in" "desc=Player 3 folded" || return 1
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
  ui scroll-to "text=Only Player 2 can win it" --max 6
  ui assert-text "text=Only Player 2 can win it" "text=Adds up to 700, everything put in." || return 1
  ui tap "desc=New hand"
  ui assert-text "text=New hand: chips cleared" text=UNDO || return 1
  ui tap text=UNDO
  ui wait-gone text=UNDO --timeout 15 || return 1
  ui scroll up --times 6                       # from the top, wherever the clear left the page
  ui scroll-to "text=Only Player 2 can win it" --max 8
  ui assert-text "text=Only Player 2 can win it"
}

# Deal maker (S22) ---------------------------------------------------------------------------------
s_deal() {
  # With the night over nobody is left in the Bank: three players to name, and tonight's payouts.
  ui back                                      # Side pots -> the Tools list
  ui scroll-to "text=Deal maker" --max 4
  ui tap "text=Deal maker"
  ui assert-text "text=Deal maker" desc=Back "text=Players left" "text=Typed for this deal" \
    "desc=Chips Player 1 has" "desc=Start over" || return 1
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
  ui scroll-to "text=\$38.39" --dir up --max 8
  ui assert-text "text=\$38.39" "text=\$40.00" || return 1
  ui scroll-to "text=\$28.86" --max 4
  ui assert-text "text=\$32.75" "text=\$28.86" "text=\$28.00" || return 1
  ui scroll-to "text=Each way adds up to \$100." --max 4
  ui assert-text "text=Each way adds up to \$100."
}
s_deal_winner() {
  # $10 saved for the winner: $90 is shared now, and the winner takes the $10 on top.
  ui scroll-to "desc=Save for the winner" --max 8
  ui set-text "desc=Save for the winner" --value 10
  ui scroll-to "text=Each way adds up to \$90." --dir up --max 8
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
  # Back to the Tools list, scrolled to its top again for the Odds steps.
  ui back
  ui scroll-to text=Odds --dir up --max 6
  ui assert-text text=Odds "text=Chip set" || return 1
  require_tab_selected Tools
}

step outs                 "Outs & pot odds (S20): a flush draw, 35.0% / 19.1%"  s_outs
step outs-turn            "On the turn: a gutshot is 4 of 46, 8.7%"            s_outs_turn
step outs-pot-odds        "Pot 300, call 100: 25.0% needed, too short"          s_outs_pot_odds
step side-pots            "Side pots (S21): three players, nothing in"          s_side_pots
step side-pots-all-in     "100 all in, 300, 300: main 300, side 400"            s_side_pots_all_in
step side-pots-fold       "Player 3 folds: side pot to 2; New hand, Undo"       s_side_pots_fold
step deal                 "Deal maker (S22): night over, three to name"         s_deal
step deal-icm             "5k/3k/2k for 50/30/20: ICM 38.39/32.75/28.86"        s_deal_icm
step deal-winner          "\$10 saved for the winner: \$90 shared"               s_deal_winner
step deal-start-over      "Start over, then Undo brings the deal back"          s_deal_start_over
step table-tools-back     "Back to the Tools list, at its top"                  s_table_tools_back
