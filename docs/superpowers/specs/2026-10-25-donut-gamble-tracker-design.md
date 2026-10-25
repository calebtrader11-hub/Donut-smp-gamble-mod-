# Donut SMP Gamble Tracker — Design

## Goal
A client-side Fabric mod for Minecraft **1.21.11** that lets a streamer acting as
"the house" on Donut SMP track incoming gamble payments, declare wins/losses from
a polished in-game GUI, remember what is owed to winners, and show live profit on
screen (captured by the stream).

## Games
| Game | Bet range | On player win |
|---|---|---|
| Paper | 5M – 500M | Owe player 2x bet (money) |
| Blackjack | 101M – 701M | Owe player 2x bet (money) |
| Ely Arrows | any | Owe player 1 Elytra (bought and placed in their `/order`); cost = current elytra price |

All ranges, multipliers and the elytra price are editable in config / the GUI.

## Flow
1. A viewer runs `/pay <streamer> <amount>`.
2. The mod matches the incoming chat line, extracts player + amount, and creates a
   **pending bet**. Game is auto-guessed by range (5M–100M → Paper; 501M–701M →
   Blackjack; 101M–500M → ambiguous, defaults to last-used game). The card has
   game toggle chips (Paper / Blackjack / Ely) so the streamer can correct it.
3. Streamer clicks **WIN** or **LOSE** on the card (or hotkeys act on the newest
   pending bet).
   - **LOSE** → house keeps the bet. Profit += bet.
   - **WIN (money)** → profit += bet − payout; an **Owed** entry
     "You owe Steve 100M" with a **Pay** button that pre-fills
     `/pay Steve 100M` in the chat box (never auto-sends). **Paid** clears it.
   - **WIN (Ely)** → profit += bet − elytraPrice; Owed entry
     "You owe Steve 1 Elytra — fill their order" with a **Delivered** button.
4. A toast banner announces each new bet and each owed payout.

## Components
- **AmountParser** — "5M", "1.2b", "$500,000", "750k" → long. Pure, unit tested.
- **PaymentDetector** — configurable regex list (defaults cover
  `<player> paid you $<amt>` and `You received $<amt> from <player>`, with color
  codes/formatting stripped). Pure, unit tested. Hooked via Fabric
  `ClientReceiveMessageEvents` (GAME + CHAT).
- **GameClassifier** — range-based guess. Pure, unit tested.
- **Ledger** — bets, outcomes, owed list, session + lifetime stats (net profit,
  wagered, wins, losses, biggest win/loss). Pure, unit tested.
- **Storage** — JSON in `.minecraft/config/donutgamble/` (config.json,
  ledger.json); saved on every change so crashes lose nothing.
- **HUD overlay** — always-on compact profit card: session Net Profit (green/red,
  large), Wagered, W/L count, last 5 results, owed count. Position/scale
  configurable; toggle hotkey.
- **Control screen** (hotkey, default `F`) — dark rounded panels, accent color:
  pending bet cards with WIN/LOSE + game chips, Owed list, elytra price input,
  session/lifetime stats, "New Session" reset.

## Hotkeys (rebindable)
`F` open panel (vanilla "Swap Item With Offhand" is also F by default — move that in Controls to avoid both firing) · `H` toggle HUD · `]` newest pending = WIN · `[` newest pending = LOSE.

## Out of scope (YAGNI)
Auto-sending payments, OBS browser overlay, multi-account, reading other
players' bets, automatic elytra purchasing.

## Testing
JUnit tests for AmountParser, PaymentDetector, GameClassifier, Ledger math.
Build with Gradle (Fabric Loom). Manual in-game verification by the streamer
(real Donut SMP message format to be confirmed; regex is editable in config).
