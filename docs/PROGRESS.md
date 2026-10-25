# Progress: premium redesign

Living document for the redesign. Read this first when picking the work back up.

## Phase 1: Audit (what the code looked like before this pass)

Architecture: Fabric client mod, Minecraft 1.21.11, Yarn mappings. `core/` is pure Java
(unit tested, no Minecraft imports); `client/` is the Minecraft side. State lives in
`GambleState`, which every GUI calls into. Data is JSON in `config/donutgamble/`.

### Requested changes (from the streamer)
1. The "Awaiting manual payment of …" line should be a toast, not part of the score box.
2. Scores are picked from fixed options: Paper 1-9, Blackjack 17-21 (players hit until 17+).
3. A tie records straight away with no score box. Win **and** loss open the score box.

### Problems found
| # | Area | Finding | Severity |
|---|---|---|---|
| A1 | Performance | Every change writes all 4 JSON files synchronously on the render thread; `finance.json` grows forever, so this gets slower over a stream. | High |
| A2 | Data safety | No backups. One corrupt file means `Storage.read` logs an error and starts **empty**, then the next save overwrites the bad file, so the data is lost. | High |
| A3 | Correctness | An Ely win with no elytra price set records a payout of 0, so profit is overstated. The price box allowed "OK" only if price > 0, but the `]` hotkey path skipped the box entirely. | Medium |
| A4 | Correctness | Settings number fields apply every keystroke: typing "5m" briefly sets the default auction length to 5 seconds, and a half-typed value is saved if the screen closes. | Low |
| A5 | Correctness | `resolve` for Ely through the `]` hotkey also ignores whether a price exists (same as A3). | Medium |
| A6 | Architecture | Drawing helpers (`text`, `right`, `link`, hit regions) are duplicated in `QueueOverlay` and `ManagerScreen`; `FlatButton` is a plain outlined rectangle; no shared spacing, radius or type scale. | Medium |
| A7 | Architecture | `QueueOverlay` (600 lines) mixes layout, drawing, hit-testing and dragging; only the queue can be moved; toasts are fixed bottom-right. | Medium |
| A8 | UX | No animation anywhere; toasts pop in and vanish; tab selection jumps. | Low |
| A9 | UX | No way to see one player's whole story (bets, payments, auctions) in one place. | Medium |
| A10 | Robustness | `activeToasts()` mutates the list while the HUD renders it; harmless today (render thread only) but fragile. | Low |

Already sound (kept as is): payment detection is anchored (no chat spoofing), amounts over
`Long.MAX_VALUE` are caught, duplicate echoes are dropped, writes are atomic (tmp + move).

## Phase 2: Plan (in priority order)

1. **Requested changes** (score picker, tie skips the box, payment toast).
2. **Storage**: debounced background saving + flush on exit (A1); daily rotating backups,
   and on a corrupt file restore from the newest backup instead of starting empty (A2).
3. **Correctness**: Ely wins always confirm a price (A3, A5); settings apply on commit (A4).
4. **Design system** `client/ui/`: tokens (`Theme`), painter (rounded panels, shadows,
   pills, bars), animation (`Anim`), components (`Button`, `TextBox`, `Chips`), and a
   shared hit-region helper (A6, A8).
5. **HUD components + editor**: Queue, Auction timer, Session profit, Toasts as separate
   components with saved position / scale / visibility; a HUD editor screen to drag, scale,
   toggle, snap and reset (A7).
6. **Redesign** every screen on the design system: queue, toasts, score picker, manager.
7. **Players** section: per-player bets, W-L-T, money in/out, auction totals (A9).
8. Tests for the new pure logic; game test screenshots of every screen; README.

## Phase 3-5: Status

- [x] 1 Requested changes: `ScoreScreen` picker (Paper 1-9, Blackjack 17-21 + Bust, digits
      can be typed), W and L open it, T records instantly; "Awaiting manual payment" is a toast.
- [x] 2 Storage: `Storage` debounces saves onto a background thread (JSON snapshot on the game
      thread, single writer, flush on `CLIENT_STOPPING`); daily backups, 7 kept; unreadable file
      → newest readable backup, bad file kept as `.broken`.
- [x] 3 Correctness: Ely wins always confirm a price (W and the `]` hotkey when no price is set);
      settings fields apply on Enter / focus loss / leaving the page.
- [x] 4 Design system `client/ui/`: `Colors` tokens, `Paint` (rounded rects, cards, shadow, pills,
      bars, text), `Anim`, `Button` (4 variants, hover fade), `TextBox`, `Regions`, `ScaledScreen`.
- [x] 5 HUD: `HudElement` + `HudManager`; `QueueHud`, `AuctionHud`, `ProfitHud`, `ToastHud`;
      `HudEditorScreen` (drag, snap, scroll to scale, right-click to hide, reset). Layout in
      `GambleConfig.hud` (`core.HudLayout`, unit tested); old `hudX/hudY` migrated.
- [x] 6 Redesign: queue (animated tab pill, hover highlights), toasts (slide/fade), score picker,
      manager (sidebar with animated selection, cards, scroll lists with scrollbars).
      `ScaledScreen` fixes cramped layouts at 1080p/auto GUI scale.
- [x] 7 Players section (`core.PlayerStats`, unit tested).
- [x] 8 63 unit tests; client game test with 33 checks and 22 screenshots; README.

### Verified
- `./gradlew test`: 63 tests pass.
- `./gradlew runClientGameTest`: passes. Exercises chat detection, spoof rejection, queue
  routing, W/L/T flows and the score picker (typed digits saved), Ely price flow, payment toast,
  HUD drag, auction routing, dedupe, correction, void, refunds, payouts, exports.
- Rendered screens were inspected at 427×240 and at a forced 480×270 manager layout.

### Not verified
- In a real world on a real server (the test machine can't load a world): the HUD over
  gameplay, hotkeys during play, and chat-screen clicking were exercised only indirectly.
- `Open folder` (needs a desktop).

### Decisions
- Manager minimum layout 480×270 (1080p at GUI scale 4).
- Profit HUD off by default (the queue's game tabs already show profit).
- `ScaledScreen` only lowers the scale in whole steps, so pixels stay crisp.
