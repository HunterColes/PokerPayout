# shellcheck shell=bash
# Opt-in tour steps that pose the app for the store listing's screenshots (PP-040). listing.sh runs
# them with some of the tour's own steps, then copies the pictures of the `listing-shot-*` steps into
# metadata/en-US/images/phoneScreenshots/. tour.sh sources this file and runs these steps only when
# --only or --steps-file names them, so the plain tour is unchanged. They use tour.sh's helpers (ui,
# tab, demo_mode, require_landscape, wait_screen, s_start_fold, cash_add_player, cash_count,
# cash_snackbar_gone).
#
# One home game, played through: nine friends, the default $20 buy-in with a $5 progressive bounty
# and $20 rebuys until the end of level 4. Everyone pays in, the clock starts, and on level 3 two have
# rebought and two are out. Then a cash game after, and the tools. Each `listing-shot-*` step ends on
# a clean screen for its picture: no snackbar, no keyboard, the page at its top (or, for the
# settle-up, its end), the status bar in demo mode (12:00, full battery). It ends with an assertion,
# so the tour keeps that dump for the step's .xml.

# Real-looking names, not "Player N": the Bank's nine rows, and five of them in the cash game.
LISTING_PLAYERS=(Alice Ben Carla Dev Erin Femi Gus Hana Ivan)

# The status bar as boot.sh leaves it (12:00, full battery, no notification icons): sent again before
# each picture, since SystemUI can drop demo mode when the display turns.
listing_pose() {
  demo_mode on
  sleep 1
}

# Taps a Bank cell by its TalkBack description, with no snackbar over the list's lower rows: from the
# list's top, scrolled down to it.
bank_tap() { # selector
  ui wait-gone text=UNDO --timeout 12
  ui scroll up --times 3
  ui scroll-to "$1" --max 6
  ui tap "$1"
}

# $1 is knocked out by $2, from the knockout sheet. With progressive bounties the sheet says what
# the winner takes now.
listing_knockout() { # who is out, who knocked them out
  bank_tap "desc=Knock out $1"
  ui wait "text=$1 is out" || return 1
  ui tap "re=^$2(,|\$)"                          # the choice; with its bounty it may read "Alice, bounty $5"
  ui scroll-to "text~=$2 takes " --max 3          # under the choices; the sheet may scroll
  ui assert-text "text~=$2 takes " || return 1
  ui tap "text=Knock out $1" --scroll-in scrollable
  ui wait-gone "text=$1 is out"
}

# Counts a cash player's chips from their sheet: $1 bought in $2, counted out $3; $4 is what the
# sheet then says.
listing_cash_count() { # name, in, out, what the sheet says
  cash_snackbar_gone
  ui scroll up --times 4
  ui scroll-to "has=$1|in \$$2" --max 6
  ui tap "has=$1|in \$$2"
  cash_count "$1" "$3" "$4"
}

# The night's setup ------------------------------------------------------------------------------
s_listing_money() {
  # Nine players (from the default five), a $5 bounty paid progressively, $20 rebuys until the end of
  # level 4. The buy-in stays at the default $20.
  for _ in 1 2 3 4; do ui tap "desc=Increase Players"; done
  ui assert-text "has=Players|9" || return 1
  ui set-text has=Bounty class=EditText --value 5
  ui enter
  ui scroll-to text=Progressive --max 3
  ui tap text=Progressive
  ui assert "has=Progressive" checked || return 1
  ui scroll-to has=Rebuy class=EditText --max 3
  ui set-text has=Rebuy class=EditText --value 20
  ui enter
  ui scroll-to "has=Rebuys until" clickable --max 3
  ui tap "has=Rebuys until" clickable
  ui tap "text=End of L4" --scroll-in scrollable   # in the menu, which scrolls if it is cut short
  ui assert-text "has=Rebuy|20" "has=Rebuys until|End of L4"
}
s_listing_bank_names() {
  # The Bank's rows get the players' names; Enter saves each one and leaves the field
  tab Bank
  ui assert-text "text=Player 1" "text~=9 players · " || return 1
  local i
  for i in "${!LISTING_PLAYERS[@]}"; do
    ui scroll-to "text=Player $((i + 1))" class=EditText --max 4
    ui set-text "text=Player $((i + 1))" class=EditText --value "${LISTING_PLAYERS[$i]}"
    ui enter
  done
  ui scroll up --times 4
  ui assert-text text=Alice text=Ben text=Carla
}
s_listing_buy_ins() {
  # Everyone pays in before the start
  local name
  for name in "${LISTING_PLAYERS[@]}"; do bank_tap "desc=$name, buy-in, not paid"; done
  ui wait-gone text=UNDO --timeout 12
  ui scroll up --times 4
  ui assert-text "desc=Alice, buy-in, paid" "desc=Ben, buy-in, paid"
}
s_listing_start() {
  # Start (the tour's start-fold, which also answers the notifications question), then on to level 3
  tab Tournament
  ui wait "Start clock" || return 1
  s_start_fold
  ui tap "desc=Next blind level"
  ui wait "text~=Level 2 · time left" || return 1
  ui tap "desc=Next blind level"
  ui assert-text "text~=Level 3 · time left" "text~=Level 3 of 9 · running"
}
s_listing_midgame() {
  # Level 3: Ben and Erin rebuy; Alice knocks Ivan out and Carla knocks Hana out (each takes half the
  # $5 bounty now, and their own grows to $7.50)
  tab Bank
  bank_tap "desc=Ben, rebuy, none yet"
  bank_tap "desc=Erin, rebuy, none yet"
  listing_knockout Ivan Alice
  listing_knockout Hana Carla
  ui wait-gone text=UNDO --timeout 12
  ui scroll up --times 4
  ui assert-text "text~=7 of 9 left · " "desc=Ben, rebuy, 1 taken" "desc=Erin, rebuy, 1 taken"
}

# The pictures -----------------------------------------------------------------------------------
s_listing_shot_clock() {
  # 01: the clock on level 3, a few minutes in (four minutes off, so it isn't on a fresh level)
  tab Tournament
  ui scroll up --times 3
  local i; for i in 1 2 3 4; do ui tap "desc=Remove one minute"; done
  listing_pose
  ui assert-text "text~=Level 3 of 9 · running" "text~=Level 3 · time left" "text~=played" "desc=Pause timer"
}
s_listing_shot_table_view() {
  # 02: the table view, full screen and landscape, with the table's numbers in its footer
  ui tap "desc=Table view"
  ui wait "desc=Exit table view" || return 1
  require_landscape
  listing_pose
  ui assert-text "text~=7 of 9 left" "text~=Pool " "desc=Pause timer" "desc=Exit table view"
}
s_listing_shot_bank() {
  # 03: the Bank mid-game. First back to the upright clock, and pause it, so the Payouts tab after
  # this isn't locked.
  ui tap "desc=Exit table view"
  ui wait-gone "desc=Exit table view"
  wait_screen port
  ui tap "desc=Pause timer"
  ui wait "desc=Resume timer" || return 1
  tab Bank
  ui wait-gone text=UNDO --timeout 12
  ui scroll up --times 4
  listing_pose
  ui assert-text "text~=7 of 9 left · " "text=Alice" "text=Ben"
}
s_listing_shot_payouts() {
  # 04: the Payouts tab: the prize pool, the places, the bubble
  tab Payouts
  ui scroll up --times 4
  listing_pose
  # (a place's row is one node for TalkBack, "1st, Still playing, $110, 50%")
  ui assert-text "PRIZE POOL" "text~=prize pool · 3 places paid" "re=^1st(,|\$)" "re=^2nd(,|\$)"
}
s_listing_cash() {
  # The cash game after: five buy in, Carla tops up, everyone's chips are counted, $200 in and out
  tab Bank
  ui scroll up --times 4
  ui tap "text=Cash game"
  ui assert-text "text=Nobody at the table yet" "text=Add player" || return 1
  cash_add_player Alice 40
  cash_add_player Ben 20
  cash_add_player Carla 40
  cash_add_player Dev 40
  cash_add_player Erin 40
  # Carla's sheet: a $20 top-up, then her count in the same sheet
  cash_snackbar_gone
  ui scroll up --times 4
  ui scroll-to "has=Carla|in \$40" --max 6
  ui tap "has=Carla|in \$40"
  ui wait "text=BOUGHT IN · \$40" || return 1
  ui set-text "desc=Top-up for Carla" --value 20
  ui tap "text=Top up \$20"
  ui assert-text "text=BOUGHT IN · \$60" "text=Top-up 1" || return 1
  cash_count Carla 45 "Down \$15 on the night"
  listing_cash_count Alice 40 84 "Up \$44 on the night"
  listing_cash_count Ben 20 0 "Down \$20 on the night"
  listing_cash_count Dev 40 63 "Up \$23 on the night"
  listing_cash_count Erin 40 8 "Down \$32 on the night"
  cash_snackbar_gone
  ui scroll up --times 4
  ui assert-text "text=BALANCED" "has=Alice|in \$40|out \$84|up \$44" "has=Ben|in \$20|out \$0|down \$20"
}
s_listing_shot_settle_up() {
  # 05: who pays whom, the first payment ticked as paid at the table; the end of the page, so the
  # whole settle-up shows under the ledger
  cash_snackbar_gone
  ui scroll-to "text=Share as text" --max 6
  ui assert-text "text=Tick each one when paid" || return 1
  ui tap "re=^[A-Z][a-z]+ pays [A-Z][a-z]+\$"
  cash_snackbar_gone
  ui scroll down --times 2
  listing_pose
  ui assert-text "text=Tick each one when paid" "re=^[A-Z][a-z]+ pays [A-Z][a-z]+\$" "text=Share as text"
}
s_listing_shot_odds() {
  # 06: the tour's odds steps leave A♠K♠ against Q♥Q♦ on a J♠10♠2♣ flop, worked out exactly
  ui scroll up --times 3
  listing_pose
  ui assert-text "text~=Flop · exact · 990 runouts" "text~=Nut flush draw + gutshot" "text~=Overpair, queens"
}
s_listing_shot_chip_set() {
  # 07: the chip set (the tour's chip-calc step opened it): the chips you own and each player's stack
  ui scroll up --times 3
  listing_pose
  ui assert-text "text=Chip set" "text~=Chips you own" "text~=Each player gets" "re=chips? a stack ·"
}
s_listing_shot_seat_draw() {
  # 08: the seat draw with the Bank's nine, one table, dealt for the button
  ui back                                     # Chip set -> the Tools list
  ui scroll-to "text=Seat draw" --max 4
  ui tap "text=Seat draw"
  ui assert-text "text=From the Bank" "re=^Alice, Ben, Carla" "text=Draw seats" || return 1
  ui tap "text=Draw seats"
  ui wait "text=Deal for the button" || return 1
  ui tap "text=Deal for the button"
  ui wait "re=^Button: .+, seat [0-9]+$" || return 1
  ui wait-gone text=UNDO --timeout 12
  ui scroll up --times 3
  listing_pose
  ui assert-text "text=TABLE 1" "re=^Button: .+, seat [0-9]+$" "text=Deal again"
}

extra_step listing-money            "Listing: 9 players, \$5 progressive bounty, \$20 rebuys to L4" s_listing_money
extra_step listing-bank-names       "Listing: the Bank's nine get names"                          s_listing_bank_names
extra_step listing-buy-ins          "Listing: everyone pays the buy-in"                           s_listing_buy_ins
extra_step listing-start            "Listing: start the clock, on to level 3"                     s_listing_start
extra_step listing-midgame          "Listing: two rebuys, two knockouts"                          s_listing_midgame
extra_step listing-shot-clock       "Listing picture: the clock mid-game"                         s_listing_shot_clock
extra_step listing-shot-table-view  "Listing picture: the table view"                             s_listing_shot_table_view
extra_step listing-shot-bank        "Listing picture: the Bank mid-game (clock paused)"           s_listing_shot_bank
extra_step listing-shot-payouts     "Listing picture: the Payouts tab"                            s_listing_shot_payouts
extra_step listing-cash             "Listing: a cash game, counted and balanced"                  s_listing_cash
extra_step listing-shot-settle-up   "Listing picture: the cash game's settle-up"                  s_listing_shot_settle_up
extra_step listing-shot-odds        "Listing picture: Odds on the flop"                           s_listing_shot_odds
extra_step listing-shot-chip-set    "Listing picture: the chip set"                               s_listing_shot_chip_set
extra_step listing-shot-seat-draw   "Listing picture: the seat draw, dealt for the button"        s_listing_shot_seat_draw
