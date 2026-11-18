# Propulsion and Motion Damping benchmark

[中文](PHYSICAL_MOTION_0.1.440.md)

With 32 one-block creatures running 64 motion modules, mean server event timing changed from **1.663 MSPT** to **1.724 MSPT**. The observed mean difference was approximately 0.061 ms in this short comparison. It does not establish capacity for large articulated machines or multiplayer servers.

## Scenario and method

- Minecraft 1.21.1, NeoForge 21.1.235, Java 21, Create 6.0.10, Sable 2.0.6 and Photomancy 1.0.1; isolated local Windows server with a 2 GiB maximum heap.
- Actual tested builds: `0.1.438-damping-ui-test` and `0.1.439-world-thrust-test`. All production classes in 0.1.440 match that 439 build; 440 adds recipe and manual resources. These measurements retain their original build labels and are not presented as a new 440 benchmark run.
- Each creature had one Propulsion module and one Motion Damping module. The older build used an east-facing mark; the newer build used equivalent world-east thrust. Continuous propulsion and directional speed control were active, with damping of eastward velocity.
- Each run warmed up for 100 ticks and sampled 300 ticks. Timing covers the server tick event interval. The fixture reset structure poses and velocities after each sample to keep workload and placement consistent. That additional reset work is outside the reported timing, so these figures do not represent the full cost of freely moving machines.
- One run per build; this is not a repeated-sample statistical significance test.

## Sustained activity

All timings are milliseconds.

| Build | Cores / active modules | Sample ticks | Mean MSPT | Median | P95 | Maximum |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 438, marked-face thrust | 32 / 64 | 300 | 1.662716 | 1.6345 | 2.1013 | 7.8399 |
| 439, world-direction thrust | 32 / 64 | 300 | 1.723783 | 1.6519 | 2.3294 | 9.0391 |

## Blueprint placement calls

The native blueprint interface placed and activated 32 one-block creatures from the same template.

| Build | Mean placement call | Maximum placement call | Complete scene setup |
| --- | ---: | ---: | ---: |
| 438 | 2.756238 | 17.2107 | 485.9247 |
| 439 | 3.623884 | 23.3598 | 484.0142 |

These are wall-clock placement-call and setup durations, not full generation-tick MSPT peaks. Large blueprints can still cause noticeable pauses; one-block template results cannot establish their performance.

## Scope

This fixture did not measure client FPS, real multiplayer traffic, third-party thrusters, large moving joints or 1024 continuously active force modules. Configuration capacity is not a recommended active workload. See the [performance summary](STRESS_TESTS_EN.md) for retained complex-machine and large-blueprint measurements.
