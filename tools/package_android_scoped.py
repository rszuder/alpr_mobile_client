"""Package A/B against explicit dirty-worktree snapshots, never against HEAD."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAMES = {"a": "android_normal_cpu_readiness_copy_20261116_v1", "b": "android_dynamic_vehicle_identity_20261116_v1"}


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def read(path): return json.loads(path.read_text(encoding="utf-8-sig"))
def write(path, value): path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
def git(*args, cwd=ROOT):
    env = os.environ.copy()
    for k in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE"):
        env.pop(k, None)
    return subprocess.run(["git", *args], cwd=cwd, env=env, capture_output=True)


def package(phase, run):
    out = ROOT / "app/build" / NAMES[phase]
    audit = out / "audit"
    base = read(audit / "baseline.json")
    allowed = set(read(audit / "scope.json"))
    current = set(git("ls-files", "-z", "--cached", "--others", "--exclude-standard").stdout.decode().split("\0")) - {""}
    changes, unrelated = [], []
    for rel in sorted(current | set(base["files"])):
        path = ROOT / rel
        value = sha(path) if path.is_file() else None
        old = base["files"].get(rel, {}).get("sha256")
        if old != value:
            row = {"path": rel, "before": old, "after": value}
            (changes if rel in allowed else unrelated).append(row)
    protected_errors = [p for p, v in base["protected"].items() if not (ROOT/p).is_file()
                        or sha(ROOT/p) != v["sha256"] or (ROOT/p).stat().st_mtime_ns != v["mtime_ns"]]
    tested = read(out / run / "source-sha256.json")
    source_now = {p.relative_to(ROOT).as_posix(): sha(p) for p in (ROOT / "app/src").rglob("*") if p.is_file()}
    source_errors = [p for p in set(tested) | set(source_now) if tested.get(p) != source_now.get(p)]
    integrity = {"head": git("rev-parse", "HEAD").stdout.decode().strip(), "changes": changes,
                 "unrelated": unrelated, "protected_files": len(base["protected"]), "protected_errors": protected_errors,
                 "tested_source_files": len(tested), "tested_source_errors": source_errors,
                 "staged_paths": git("diff", "--cached", "--name-only").stdout.decode().strip()}
    write(audit / "source-integrity.json", integrity)
    assert not unrelated and not protected_errors and not source_errors, integrity
    assert integrity["head"] == base["head"] and not integrity["staged_paths"]
    tests = read(out / run / "tests-summary.json")
    assert tests["exit_code"] == 0 and tests["tests"] > 0 and not any(tests[k] for k in ("failures", "errors", "skipped")), tests
    previous = read(ROOT / "app/build/android_options_guidance_20261116_v1/host-final/gradle-arguments.json")
    arguments = read(out / run / "gradle-arguments.json")
    selectors = lambda a: {a[i+1] for i, v in enumerate(a) if v == "--tests"}
    assert selectors(previous) <= selectors(arguments), "A previous regression selector was dropped"
    tests["all_previous_193_selectors_retained"] = True
    write(out / "tests-summary.json", tests)
    files = [c["path"] for c in changes]
    write(out / "changed-files.json", changes)
    with zipfile.ZipFile(out / f"sources-{phase}.zip", "w", zipfile.ZIP_DEFLATED) as z:
        for rel in files: z.write(ROOT / rel, rel)
    work = Path(tempfile.mkdtemp(prefix="roundtrip-", dir=audit))
    before, after = work / "before", work / "after"
    before.mkdir(); after.mkdir()
    with zipfile.ZipFile(audit / "baseline-sources.zip") as z:
        for rel in files:
            p = after / rel; p.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(ROOT/rel, p)
            if rel in z.namelist():
                p = before / rel; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(z.read(rel))
    diff = git("-c", "core.autocrlf=false", "diff", "--no-index", "--binary", "--no-renames", "before", "after", cwd=work)
    assert diff.returncode in (0, 1)
    patch = []
    for line in diff.stdout.splitlines(keepends=True):
        if line.startswith((b"diff --git ", b"--- ", b"+++ ")):
            for a, b in ((b"a/before/", b"a/"), (b"b/before/", b"b/"), (b"a/after/", b"a/"), (b"b/after/", b"b/")): line = line.replace(a,b)
        patch.append(line)
    patch_path = out / f"patch-{phase}.patch"; patch_path.write_bytes(b"".join(patch))
    applied = work / "applied"; shutil.copytree(before, applied)
    template = work / "empty-template"; template.mkdir()
    assert git("init", "--quiet", "--template="+str(template), str(applied), cwd=work).returncode == 0
    assert Path(git("rev-parse", "--show-toplevel", cwd=applied).stdout.decode().strip()).resolve() == applied.resolve()
    result = git("-c", "core.autocrlf=false", "-c", "core.whitespace=cr-at-eol", "apply", str(patch_path), cwd=applied)
    assert result.returncode == 0, result.stderr.decode(errors="replace")
    assert all(sha(applied/p) == sha(ROOT/p) for p in files)
    write(audit / "patch-roundtrip.json", {"byte_identical": True, "files": len(files), "base": "preserved dirty worktree"})
    candidate = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    apk = out / f"alpr-{phase}-debug.apk"
    if apk.exists(): assert sha(apk) == sha(candidate), "Already packaged APK differs"
    else: shutil.copy2(candidate, apk)
    with zipfile.ZipFile(apk) as z: assert z.testzip() is None
    env = os.environ.copy(); env["JAVA_HOME"] = r"C:\Program Files\Android\Android Studio\jbr"
    sdk = Path(env["LOCALAPPDATA"]) / "Android/Sdk/build-tools/36.1.0"
    result = subprocess.run([str(sdk/"apksigner.bat"), "verify", "--print-certs", str(apk)], capture_output=True, env=env)
    (audit/"apk-signer.txt").write_bytes(result.stdout+result.stderr); assert result.returncode == 0
    certificate = re.search(rb"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", result.stdout).group(1).decode()
    badging = subprocess.check_output([str(sdk/"aapt.exe"), "dump", "badging", str(apk)]).decode()
    (audit/"apk-badging.txt").write_text(badging, encoding="utf-8")
    write(out/"apk-info.json", {"file": apk.name, "bytes": apk.stat().st_size, "sha256": sha(apk), "certificate_sha256": certificate,
                              "package": next(l for l in badging.splitlines() if l.startswith("package:"))})
    report = ROOT / "docs" / (NAMES[phase]+".md")
    shutil.copy2(report, out/"report.md")
    write(out/"READY.json", {"packaging_complete": True, "source_host_status": "DONE", "host_tests": tests["tests"], "device": read(out/"device/acceptance.json") if (out/"device/acceptance.json").exists() else {"status":"NOT_VERIFIED"},
                           "apk": apk.name, "apk_sha256":sha(apk), "git_commit":integrity["head"], "git_dirty":True, "experiment_FINAL176":"NOT_RUN", "diploma_go":"NOT_GRANTED_BY_HOST_TESTS"})
    # Keep test caches out of the deliverable checksum list; inputs, logs and results are included.
    sums=[]
    for p in sorted(out.rglob('*')):
        if p.is_file() and p.name != 'SHA256SUMS.txt' and not any(part in ('tmp','classes','binary') for part in p.relative_to(out).parts):
            sums.append(sha(p)+'  '+p.relative_to(out).as_posix())
    (out/'SHA256SUMS.txt').write_text('\n'.join(sums)+'\n',encoding='utf-8')
    print(json.dumps({"phase":phase,"files":len(files),"tests":tests['tests'],"apk_sha256":sha(apk),"protected":len(base['protected'])}))


if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('phase',choices=NAMES);p.add_argument('--run',required=True);a=p.parse_args();package(a.phase,a.run)
