# Credits and licences

This project is an **original implementation**. The algorithms here were
written from scratch in Kotlin. The three repositories below were studied as
*references* — for ideas about how to represent the board, how to search the
move space, and how to read the screen. No code was copied unless the licence
explicitly allowed it, and each one is credited below with its actual licence.

---

## Reference projects

### 1. Joey239716/BlockBlast-Solver-CPP
- **URL:** https://github.com/Joey239716/BlockBlast-Solver-CPP
- **Licence:** *No licence file.* By default that means "all rights reserved":
  the code may be read, but not copied, modified or redistributed.
- **What was taken:** ideas only. Specifically, the observation that a
  Block Blast round should be treated as an *ordered* sequence of three
  placements (not three independent best-moves), and the general shape of a
  board + piece-list solver.
- **What was written independently:** the entire Kotlin solver, the heuristic,
  the vision pipeline, the overlay and the UI.

### 2. RisticDjordje/BlockBlast-Game-AI-Agent
- **URL:** https://github.com/RisticDjordje/BlockBlast-Game-AI-Agent
- **Licence:** the GitHub API reports **no licence** for this repository
  (`"license": null`). Its README mentions MIT, but with no licence file the
  safe reading is that it is not licensed for reuse. Treated as *reference
  only*.
- **What was taken:** ideas only. The confirmation that the legal action set
  is `3 pieces × 64 positions = 192`, and that enumerating legal placements
  (rather than learning a policy) is entirely tractable for this game size.
- **What was written independently:** everything. This project does **not** use
  reinforcement learning, tensors, or a trained model.

### 3. DrakeSeteraO/Block-Blast-Best-Move-Solver
- **URL:** https://github.com/DrakeSeteraO/Block-Blast-Best-Move-Solver
- **Licence:** **MIT** — https://github.com/DrakeSeteraO/Block-Blast-Best-Move-Solver/blob/main/LICENSE
- **What was taken:** ideas only. The recursive "try every order, then every
  position, score the finished board" search structure, and the idea that line
  clears are resolved immediately after each individual placement.
- **What was written independently:** this repository's `BlockBlastSolver`,
  `Board`, `Piece`, `BoardAnalysis` and `Heuristic` are original Kotlin. They
  differ substantially — this project scores the *whole* sequence (holes,
  fragmentation, free space, look-ahead risk) rather than only counting lines,
  and it uses a two-pass search so the expensive scoring runs on a shortlist.

---

## The pieces themselves

Block Blast is a published game by **Huu Games / Ketchapp**. This app does
not contain any of its artwork, audio or code. The only thing it "uses" is the
fact that the board is an 8x8 grid and that rows/columns clear — which is the
kind of rule you can read off a game in a few minutes of play, and which is
stated here as fact rather than copied expression.

The app reads the screen so it can *show you where to tap*. It does not play
for you: there is no input-injection code anywhere in this project.

## Sample screenshots

`samples/shot1.jpg`, `samples/shot2.jpg` and `samples/shot3.jpg` are the
project owner's own screenshots of Block Blast, used as test fixtures for the
vision tests. They are used for local testing and are not redistributed.