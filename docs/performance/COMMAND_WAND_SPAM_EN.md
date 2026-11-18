# Command Wand spam benchmark

[中文](COMMAND_WAND_SPAM.md)

## Four-tick per-player cooldown retest

When 32 players submitted 1,024 orders in the same tick, the server admitted 32 after adding the cooldown, down from 640. The first burst dropped from 25.271 and 38.915 ms to 5.433 and 6.034 ms in the two runs. Later bursts dropped from 2.478–3.640 ms to 0.527–0.897 ms.

| Scenario | Mean MSPT before A / B | Mean MSPT after A / B |
| --- | ---: | ---: |
| Idle | 0.366 / 0.422 | 0.394 / 0.414 |
| One player submitting ten orders per tick | 0.394 / 0.477 | 0.410 / 0.412 |
| 32 players submitting one order each per tick | 0.678 / 0.677 | 0.485 / 0.565 |
| 32 players submitting ten orders each per tick | 0.593 / 0.622 | 0.455 / 0.510 |

In the last scenario, each run submitted 114,240 orders over 357 sampled ticks. The number admitted fell from 11,424 to 2,784. The retest used the same machine and fixture described below. Each phase started with 20 input-free ticks and discarded at least five transition ticks before sampling steady load. Each version ran twice with the launch order reversed for the second comparison. Live multiplayer network traffic and complex machine workloads remain outside this benchmark.

## Baseline before the cooldown

With 32 simulated players repeatedly issuing orders, the server accepted 32 of 320 requests per tick and rejected 288. Mean server tick time was 0.513–0.550 ms across two independent runs. When all 32 players simultaneously used a full burst allowance, each burst admitted 640 requests. The first burst in each run took 25.420 ms and 24.679 ms. The four later bursts took 2.224–3.505 ms. Every recorded peak was below the 50 ms tick budget for 20 TPS.

## Sustained load

| Scenario | Offered / accepted per tick | Mean MSPT A / B | P95 A / B | Maximum tick A / B |
| --- | ---: | ---: | ---: | ---: |
| Idle | 0 / 0 | 0.406 / 0.376 | 0.588 / 0.600 | 2.833 / 2.906 |
| One player repeatedly issuing orders | 10 / 1 | 0.388 / 0.474 | 0.629 / 0.835 | 0.791 / 6.292 |
| 32 players issuing one order each | 32 / 32 | 0.637 / 0.530 | 0.880 / 0.788 | 22.454 / 1.464 |
| 32 players issuing ten orders each | 320 / 32 | 0.550 / 0.513 | 0.770 / 0.679 | 1.173 / 1.230 |

## Method and scope

- Windows, Ryzen 7 9800X3D, Java 21.0.6, Minecraft 1.21.1, NeoForge 21.1.235, and a 2 GiB heap. Both GameTest servers ran the same current 0.1.404 source in separate test worlds. Each sustained scenario sampled 357 ticks per run using Minecraft's server tick timings.
- Thirty-two simulated players targeted loaded terrain about 112 blocks away with right-click destination orders bound to one active core. The test used the actual Command Wand packet codec and server handler. Source inspection confirmed that over-quota requests return before raycasting.
- Each run included three full bursts. Every burst submitted 1,024 requests in one tick, with 640 admitted. Reported burst timings cover the full server tick.
- This measures server-side command handling, raycasting and rate limiting. Real client network queueing, outbound packets, complex machine modules and wider modpack load require separate measurements.
