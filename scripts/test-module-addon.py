"""Verify packaged Java or datapack modules in isolated Minecraft scenarios."""

import argparse
import json
import os
import shutil
import subprocess
import time
from contextlib import ExitStack
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"
MODDEV = BUILD / "moddev"


def java_command(java, run, directory, mods, *properties):
    classpath_file = MODDEV / f"{run}LegacyClasspath.txt"
    program_args = MODDEV / f"{run}RunProgramArgs.txt"
    vm_args = MODDEV / f"{run}RunVmArgs.txt"
    mod_paths = {Path(mod.split("%%", 1)[1]).resolve() for mod in mods}
    libraries = [path for path in classpath_file.read_text(encoding="utf-8").splitlines()
                 if path and Path(path).resolve() not in mod_paths
                 and not Path(path).name.startswith("sablelife-example-addon-")]
    legacy_classpath = directory / "legacy-classpath.txt"
    legacy_classpath.write_text("\n".join(libraries) + "\n", encoding="utf-8")
    neo_version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines()
                       if line.startswith("neo_version="))
    artifacts = [MODDEV / "artifacts" / f"neoforge-{neo_version}.jar"]
    dependencies = sorted((ROOT / ".dev/mods").glob("*.jar"))
    classpath = os.pathsep.join([*libraries, *(path.as_posix() for path in artifacts + dependencies)])
    classpath_args = directory / "classpath.args"
    classpath_args.write_text('-cp\n"' + classpath.replace("\\", "/") + '"\n', encoding="utf-8")
    return [str(java.resolve()), "@" + str(vm_args), "@" + str(classpath_args), "-Xmx2G",
            "-DlegacyClassPath.file=" + str(legacy_classpath),
            "-Dfml.modFolders=" + os.pathsep.join(mods), "-Dneoforge.gameTestServer=false",
            "-Dneoforge.enableGameTest=false", "-Dsablelife.localGameTests=false",
            *properties, "@" + str(program_args)]


def verify(log, marker, result):
    if result or marker not in log.read_text(encoding="utf-8", errors="replace"):
        raise SystemExit(f"{marker} missing. See {log}")
    print(f"{marker} ({log})", flush=True)


def network(java, directory, mods, port, properties):
    processes = []
    with ExitStack() as resources:
        try:
            server_log = directory / "server.log"
            output = resources.enter_context(server_log.open("w", encoding="utf-8"))
            command = java_command(java, "gameTestServer", directory, mods, *properties, "-Dsablelife.addonNetworkServer=true")
            server = subprocess.Popen([*command, "--nogui"], cwd=directory, stdout=output, stderr=subprocess.STDOUT)
            processes.append((server, server_log, "ADDON_MODULE_NETWORK_SERVER PASS"))
            deadline = time.monotonic() + 90
            while time.monotonic() < deadline and server.poll() is None:
                if "Done (" in server_log.read_text(encoding="utf-8", errors="replace"):
                    break
                time.sleep(1)
            else:
                raise SystemExit(f"Server did not start. See {server_log}")
            for role, locale in (("author", "en_us"), ("observer", "zh_cn")):
                client_directory = directory / role
                client_directory.mkdir()
                (client_directory / "options.txt").write_text(
                    f"lang:{locale}\nrenderDistance:3\nsimulationDistance:3\nmaxFps:30\npauseOnLostFocus:false\n",
                    encoding="utf-8")
                client_log = client_directory / "client.log"
                output = resources.enter_context(client_log.open("w", encoding="utf-8"))
                command = java_command(java, "coreModuleAuditClient", client_directory, mods, *properties,
                                       "-Dsablelife.coreModuleAudit=false", f"-Dsablelife.addonNetworkRole={role}",
                                       f"-Dsablelife.addonNetworkPort={port}")
                client = subprocess.Popen([*command, "--username", "Addon" + role.title()], cwd=client_directory,
                                          stdout=output, stderr=subprocess.STDOUT)
                processes.append((client, client_log, f"ADDON_MODULE_NETWORK_CLIENT {role} PASS"))
            for process, log, marker in processes:
                verify(log, marker, process.wait(timeout=150))
        finally:
            for process, _, _ in reversed(processes):
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=20)


def install_full_catalog(directory, fixture):
    pack = directory / "world/datapacks/capacity"
    modules = pack / "data/sablelife_capacity/sablelife/modules"
    modules.mkdir(parents=True)
    shutil.copyfile(fixture / "pack.mcmeta", pack / "pack.mcmeta")
    prefix = "sablelife_capacity:datapack/"

    def localized(module, field, length):
        start = 0x4E00 + module * 20 + field * 2
        return {"en_us": chr(start) * length, "zh_cn": chr(start + 1) * length}

    def option(module, index):
        return {"id": f"p{index}" + "x" * 56, "label": localized(module, index + 2, 64),
                "min": 0.5, "max": 2, "default": 1.25 if index == 7 else 1}

    def write(stem, definition):
        (modules / f"{stem}.json").write_text(json.dumps(definition, ensure_ascii=False), encoding="utf-8")

    for index in range(64):
        stem = f"m{index:02d}".ljust(64 - len(prefix), "x")
        options = [option(index, number) for number in range(8)]
        definition = {"format": 1, "name": localized(index, 0, 64), "description": localized(index, 1, 192),
                      "cores": ["heart", "redstone", "survival"], "mark": "none", "options": options,
                      "on_start": {"type": "play_sound", "sound": "minecraft:block.note_block.chime",
                                   "volume": {"option": options[6]["id"]}, "pitch": {"option": options[7]["id"]}}}
        write(stem, definition)
        if index == 0:
            write("a_invalid", {**definition, "options": [*options, option(index, 8)]})
            write("z_excess", definition)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("JAVA_HOME", "")) / "bin/java.exe")
    parser.add_argument("--run-directory", type=Path,
                        default=ROOT / "runs" / datetime.now().strftime("addon-restart-%Y%m%d-%H%M%S"))
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--network", action="store_true", help="Run one server and two clients")
    mode.add_argument("--singleplayer", action="store_true", help="Run one client with an integrated server")
    parser.add_argument("--port", type=int, default=25592, help="Loopback port for the multiplayer scenario")
    parser.add_argument("--datapack", action="store_true", help="Use the JSON alarm instead of the Java addon")
    parser.add_argument("--full-catalog", action="store_true", help="Test the full JSON module catalog with two clients")
    parser.add_argument("--jar", type=Path, help="Use a specific Sable Life candidate JAR")
    parser.add_argument("--previous-jar", type=Path, help="Create the alarm fixture world on this JAR before upgrading")
    args = parser.parse_args()
    if args.full_catalog and (not args.network or args.datapack):
        parser.error("Use --full-catalog with --network instead of --datapack")
    if args.previous_jar and (not args.datapack or args.network or args.singleplayer):
        parser.error("Use --previous-jar with the datapack restart scenario")
    directory = args.run_directory.resolve()
    properties_file = (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
    version = next(line.split("=", 1)[1] for line in properties_file if line.startswith("mod_version="))
    minecraft = next(line.split("=", 1)[1] for line in properties_file if line.startswith("minecraft_version="))
    main_jar = args.jar.resolve() if args.jar else BUILD / f"libs/sablelife-neoforge-{minecraft}-v{version}.jar"
    previous_jar = args.previous_jar.resolve() if args.previous_jar else None
    addon_jar = BUILD / "libs/sablelife-example-addon-1.0.0.jar"
    test_classes = BUILD / "classes/java/gameTest"
    test_resources = BUILD / "resources/gameTest"
    datapack = args.datapack or args.full_catalog
    required = [args.java, main_jar, test_classes, test_resources]
    if previous_jar:
        required.append(previous_jar)
    if not datapack:
        required.append(addon_jar)
    for path in required:
        if not path.exists():
            parser.error(f"Missing {path}. Build the addon candidate and prepare the gameTestServer run first.")

    directory.mkdir(parents=True, exist_ok=not datapack)
    (directory / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    port = args.port if args.network else 0
    (directory / "server.properties").write_text(
        f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n"
        "view-distance=3\nsimulation-distance=3\nlevel-type=minecraft:flat\n", encoding="utf-8")
    base_mods = [f"sablelife%%{main_jar.as_posix()}", f"sablelife_tests%%{test_classes.as_posix()}",
                 f"sablelife_tests%%{test_resources.as_posix()}"]
    addon = f"sablelife_example_addon%%{addon_jar.as_posix()}"
    fixture = ROOT / "examples/datapack-alarm"
    installed_mods = base_mods if datapack else [*base_mods, addon]
    properties = []
    if args.datapack:
        observations = directory / "observations"
        observations.mkdir()
        properties = [f"-Dsablelife.datapackAlarmFixture={fixture}", f"-Dsablelife.datapackAlarmObservations={observations}"]
    if args.full_catalog:
        observations = directory / "observations"
        observations.mkdir()
        install_full_catalog(directory, fixture)
        properties = ["-Dsablelife.datapackFullCatalog=true", f"-Dsablelife.datapackAlarmObservations={observations}"]
    if args.singleplayer:
        (directory / "options.txt").write_text("lang:en_us\nrenderDistance:3\nsimulationDistance:3\nmaxFps:30\npauseOnLostFocus:false\n",
                                               encoding="utf-8")
        command = java_command(args.java, "coreModuleAuditClient", directory, installed_mods, *properties,
                               "-Dsablelife.coreModuleAudit=false", "-Dsablelife.addonNetworkRole=singleplayer",
                               "-Dsablelife.addonNetworkServer=true")
        log = directory / "singleplayer.log"
        with log.open("w", encoding="utf-8") as output:
            result = subprocess.run([*command, "--username", "AddonSolo"], cwd=directory, stdout=output,
                                    stderr=subprocess.STDOUT, timeout=180)
        verify(log, "ADDON_MODULE_NETWORK_SERVER PASS", result.returncode)
        verify(log, "ADDON_MODULE_NETWORK_CLIENT singleplayer PASS", result.returncode)
        if args.datapack:
            verify(log, "DATAPACK_ALARM reload and restoration PASS", result.returncode)
        return
    if args.network:
        network(args.java, directory, installed_mods, port, properties)
        if args.datapack:
            verify(directory / "server.log", "DATAPACK_ALARM reload and restoration PASS", 0)
        if args.full_catalog:
            verify(directory / "server.log", "DATAPACK_CAPACITY login and reload PASS", 0)
        return

    stages = ("create", "upgraded", "missing", "restored") if previous_jar else ("create", "missing", "restored")
    for stage in stages:
        if args.datapack:
            pack = (directory / "world/datapacks/sablelife_alarm").resolve()
            removed = (directory / "uninstalled-sablelife-alarm").resolve()
            if not pack.is_relative_to(directory) or not removed.is_relative_to(directory):
                raise SystemExit("Datapack fixture path escaped the run directory")
            if stage == "create":
                shutil.copytree(fixture, pack)
            elif stage == "missing":
                pack.rename(removed)
            elif stage == "restored":
                removed.rename(pack)
        mods = base_mods if stage == "missing" else installed_mods
        if previous_jar and stage == "create":
            mods = [f"sablelife%%{previous_jar.as_posix()}", *base_mods[1:]]
        command = java_command(args.java, "gameTestServer", directory, mods, *properties,
                               "-Dsablelife.moduleAddonRestart=" + stage)
        log = directory / (stage + ".log")
        with log.open("w", encoding="utf-8") as output:
            result = subprocess.run([*command, "--nogui"], cwd=directory, stdout=output, stderr=subprocess.STDOUT, timeout=180)
        verify(log, f"ADDON_MODULE_RESTART {stage} PASS", result.returncode)


if __name__ == "__main__":
    main()
