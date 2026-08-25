"""Find or start an Android emulator for the on-device e2e run.

An emulator the operator already has running is reused as-is and never torn
down; one this module starts is torn down again. That asymmetry matters on a
developer machine, where killing somebody else's emulator loses their state.
"""
from __future__ import annotations

import contextlib
import os
import subprocess
import time
from pathlib import Path

BOOT_TIMEOUT = 600.0


def sdk_root() -> Path:
    for key in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        value = os.environ.get(key, "").strip()
        if value:
            return Path(value)
    return Path.home() / "Android" / "Sdk"


def adb_path() -> Path:
    return sdk_root() / "platform-tools" / "adb"


def emulator_path() -> Path:
    return sdk_root() / "emulator" / "emulator"


def adb(*args: str, timeout: float = 60.0) -> subprocess.CompletedProcess:
    return subprocess.run(
        [str(adb_path()), *args],
        capture_output=True, text=True, timeout=timeout, check=False,
    )


def booted_devices() -> list[str]:
    """Serials that answer ``getprop sys.boot_completed`` with 1."""
    listed = adb("devices")
    serials = [
        line.split("\t")[0]
        for line in listed.stdout.splitlines()[1:]
        if line.strip().endswith("\tdevice")
    ]
    ready = []
    for serial in serials:
        probe = adb("-s", serial, "shell", "getprop", "sys.boot_completed", timeout=30)
        if probe.stdout.strip() == "1":
            ready.append(serial)
    return ready


def missing_tooling() -> str | None:
    if not adb_path().exists():
        return f"adb not found at {adb_path()}"
    if not emulator_path().exists():
        return f"emulator not found at {emulator_path()}"
    return None


def list_avds() -> list[str]:
    listed = subprocess.run(
        [str(emulator_path()), "-list-avds"],
        capture_output=True, text=True, timeout=60, check=False,
    )
    return [line.strip() for line in listed.stdout.splitlines() if line.strip()]


def _await_boot(deadline: float) -> str:
    while time.monotonic() < deadline:
        ready = booted_devices()
        if ready:
            return ready[0]
        time.sleep(3)
    raise RuntimeError("no emulator reported sys.boot_completed in time")


@contextlib.contextmanager
def running_emulator(log_dir: Path):
    """Yield a booted device serial, starting an emulator only if needed.

    ``-read-only`` keeps the run off the AVD's persistent userdata image, so a
    reused developer AVD comes back unchanged.
    """
    reason = missing_tooling()
    if reason is not None:
        raise RuntimeError(f"android tooling unavailable — {reason}")
    adb("start-server", timeout=120)
    existing = booted_devices()
    if existing:
        yield existing[0]
        return

    avds = list_avds()
    if not avds:
        raise RuntimeError(
            "no emulator is running and no AVD exists to start — create one in "
            "Android Studio, or start an emulator by hand before running this."
        )
    log_dir.mkdir(parents=True, exist_ok=True)
    handle = (log_dir / "emulator.log").open("wb")
    proc = subprocess.Popen(  # noqa: S603 — shell=False, fixed argv
        [str(emulator_path()), "-avd", avds[0], "-read-only", "-no-window",
         "-no-audio", "-no-boot-anim", "-no-snapshot-save",
         "-gpu", "swiftshader_indirect"],
        stdout=handle, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
        start_new_session=True,
    )
    handle.close()
    try:
        yield _await_boot(time.monotonic() + BOOT_TIMEOUT)
    finally:
        proc.terminate()
        with contextlib.suppress(subprocess.TimeoutExpired):
            proc.wait(timeout=60)
        if proc.poll() is None:
            proc.kill()
