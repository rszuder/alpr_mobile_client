"""Seal this Android delivery after offline tests/build/signature checks. Never edits session ZIPs."""
from pathlib import Path
import argparse, datetime, difflib, hashlib, json, os, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET


def sha(path):
    digest=hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b""):
            digest.update(chunk)
    return digest.hexdigest()


def console_text(path):
    data=path.read_bytes()
    return data.decode("utf-16" if data.startswith((b"\xff\xfe",b"\xfe\xff")) else "utf-8-sig",errors="replace")


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("delivery",type=Path)
    parser.add_argument("--report",type=Path,required=True)
    args=parser.parse_args()
    root=Path.cwd(); delivery=args.delivery.resolve(); audit=delivery/"audit"
    assert not (delivery/"READY.json").exists(), "A sealed delivery must never be overwritten"
    stats=dict(tests=0,failures=0,errors=0,skipped=0)
    cases=[]
    for xml in sorted((audit/"final_run").glob("TEST-*.xml")):
        suite=ET.parse(xml).getroot()
        for key in stats:
            stats[key]+=int(suite.get(key,0))
        for case in suite.findall("testcase"):
            cases.append(suite.get("name")+"."+case.get("name"))
    assert stats["tests"]>1383 and not any(stats[k] for k in ("failures","errors","skipped")), stats
    assert len(cases)==len(set(cases)), "Do not sum overlapping test runs"
    build=console_text(audit/"full-regression-2.log")
    assert "BUILD SUCCESSFUL" in build and "compileDebugAndroidTestJavaWithJavac" in build and "assembleDebug" in build
    final_stats=dict(tests=0,failures=0,errors=0,skipped=0)
    for xml in (audit/"final_targeted_run").glob("TEST-*.xml"):
        suite=ET.parse(xml).getroot()
        for key in final_stats:final_stats[key]+=int(suite.get(key,0))
    assert final_stats["tests"]>20 and not any(final_stats[k] for k in ("failures","errors","skipped")),final_stats
    final_build=console_text(audit/"final-targeted-and-build.log")
    assert "BUILD SUCCESSFUL" in final_build and "compileDebugAndroidTestJavaWithJavac" in final_build and "assembleDebug" in final_build
    identity=json.loads((audit/"apk-identity.json").read_text(encoding="utf-8"))
    assert identity["signature_matches_installed_base"] and identity["new_signature_verified"]
    apk=delivery/"alpr-f6-state-export-recovery-debug.apk"
    assert sha(apk)==identity["sha256"]
    with zipfile.ZipFile(apk) as archive:
        assert archive.testzip() is None
        assert any(b"alpr.research_operation_receipt.v1" in archive.read(name) for name in archive.namelist() if name.endswith(".dex")), "APK must include the new implementation"
        assert any(identity["built_at_utc"].encode() in archive.read(name) for name in archive.namelist() if name.endswith(".dex")), "Build identity must belong to this APK"
    baseline=json.loads((audit/"source-before-sha256.json").read_text())
    protected=json.loads((audit/"protected-deliveries-before.json").read_text())
    violations=[p for p,v in protected.items() if not (root/p).is_file() or (root/p).stat().st_size!=v["size"] or sha(root/p)!=v["sha256"]]
    assert not violations,violations
    inputs=json.loads((audit/"build-input-hashes.json").read_text())
    assert all((root/p).is_file() and sha(root/p)==value for p,value in inputs.items()), "Sources changed after build-input freeze"
    assets=[p for p in baseline if p.startswith(("app/src/main/assets/","app/libs/"))]
    assert all(sha(root/p)==baseline[p] for p in assets)
    checks=dict(checked=len(protected),violations=violations,model_asset_files_checked=len(assets),model_asset_changes=[])
    (audit/"protected-deliveries-check.json").write_text(json.dumps(checks,indent=2))
    real=json.loads((delivery/"device_readonly/exports-verification.json").read_text(encoding="utf-8"))
    assert len(real)==2 and all(r["stable"] and not r["crc_bad_entry"] and not r["manifest_bad_entries"] for r in real)
    assert all(sha(delivery/"device_readonly/public_exports"/r["name"])==r["sha256"] for r in real)
    raw=json.loads((delivery/"device_readonly/raw-files-phone-after-copy-sha256.json").read_text())
    assert len(raw["files"])==670 and not raw["mismatches"]
    assert all(sha(delivery/"device_readonly/private_original"/p)==value for p,value in raw["files"].items())
    fixture=delivery/"producer_regression/synthetic-20min-120scenes-mt-mz-PARTIAL.alprsession"
    with zipfile.ZipFile(fixture) as archive:
        assert archive.testzip() is None
        meta=json.loads(archive.read("session.json")); assert meta["state"]=="PARTIAL"
        event_count={}
        for line in archive.read("samples/f6_execution.jsonl").splitlines():
            event=json.loads(line)["event"];event_count[event]=event_count.get(event,0)+1
        assert event_count.get("MT_STARTED")==120 and event_count.get("MZ_STARTED",0)>60
    producer_paths=[
        "app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionStore.java",
        "app/src/main/java/com/example/alpr_v1/experiment/Final180Evidence.java",
        "app/src/main/java/com/example/alpr_v1/experiment/F5aEvidence.java",
        "app/src/main/java/com/example/alpr_v1/experiment/TvBboxEvidence.java",
        "app/src/main/java/com/example/alpr_v1/metrics/ResearchArchive.java",
        "app/src/test/java/com/example/alpr_v1/pipeline/TvBboxPipelineTest.java",
        "tools/package_f6_state_recovery.py",
    ]
    producer_hashes={p:sha(root/p) for p in producer_paths}
    (audit/"producer-source-sha256.json").write_text(json.dumps(producer_hashes,indent=2))
    note=("F6 Android state/export regression; TEST_ONLY / SYNTHETIC; DEVICE_RUN=NOT_RUN.\n"
          "Schema unchanged. Existing engine with synthetic backends -> session writer -> finalization -> exporter.\n"
          "120 MT starts; MZ starts="+str(event_count["MZ_STARTED"])+". Virtual 20 min; no thermal/device claim.\n"
          "PARTIAL is intentional; cancellation/missing evidence stays visible.\n"
          "Android native producer validation PASS/PARTIAL; Desktop acceptance NOT_VERIFIED.\n"
          "Report: "+str(args.report.resolve())+"\nAPK SHA-256: "+identity["sha256"]+"\n"
          "Archive SHA-256: "+sha(fixture)+"\n")
    package=delivery/"producer_regression_package.zip"
    assert not package.exists()
    with zipfile.ZipFile(package,"x",compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(fixture,"fixtures/"+fixture.name)
        archive.writestr("README.txt",note)
        archive.writestr("producer-source-sha256.json",json.dumps(producer_hashes,indent=2))
    with zipfile.ZipFile(package) as archive: assert archive.testzip() is None
    replacements={
        "{{TEST_RESULTS}}":f"Szeroka regresja JVM: **{stats['tests']}/{stats['tests']} PASS**, 0 FAIL/ERROR/SKIP (`audit/final_run/`, `audit/full-regression-2.log`). Po ostatnim zachowaniu ochrony okna SAF podczas pomiaru: **{final_stats['tests']}/{final_stats['tests']} PASS**, 0 FAIL/ERROR/SKIP (`audit/final_targeted_run/`). Ten ostatni przebieg sprawdza finalne źródła MainActivity/VM, kopie rzeczywistej sesji i oba przypadki blokady dokumentu. Wyników nie sumuje się. Końcowa kompilacja instrumentacji i budowa debug offline: **PASS** (`audit/final-targeted-and-build.log`). Instrumentacja na urządzeniu: **NOT_RUN**.",
        "{{SYNTHETIC_BYTES}}":str(fixture.stat().st_size),"{{SYNTHETIC_SHA}}":sha(fixture),
        "{{PACKAGE_SHA}}":sha(package),"{{APK_PATH}}":str(apk),"{{APK_SHA}}":identity["sha256"],
        "{{BUILD_UTC}}":identity["built_at_utc"],"{{SIGNER_SHA}}":identity["signer_certificate_sha256"],
        "{{SIGNATURE_RESULT}}":"PASS — ten sam certyfikat; bez instalacji",
        "{{FINAL_STATUSES}}":"| Status | Wynik |\n|---|---|\n| HOST_TESTS | PASS — końcowa regresja, kompilacja instrumentacji, debug offline |\n| REAL_FILES_RECOVERY | PASS — dwie stabilne kopie, CRC/SHA, surowe 670 plików; sesja nadal PARTIAL |\n| DEVICE_UI_LIFECYCLE | NOT_VERIFIED — nowej APK nie instalowano |\n| DEVICE_EXPORT_VISIBILITY | Nowa APK NOT_VERIFIED; istniejąca rozbieżność Windows MTP potwierdzona, przyczyna NIEUSTALONA |\n| EXPERIMENT_START | NO_GO do rzeczywistego odbioru cyklu A12 i wspólnego odbioru stanowiska |",
    }
    report=args.report.read_text(encoding="utf-8")
    for key,value in replacements.items():
        assert key in report,key
        report=report.replace(key,value)
    report=report.replace("**DRAFT — trwa końcowa regresja. Dostawa jest ukończona dopiero po utworzeniu `READY.json`.**",
                          "**Dostawa hostowa ukończona. Potwierdzenie: `READY.json`. Nowa APK nie została zainstalowana; odbiór A12 pozostaje NOT_VERIFIED.**")
    assert "{{" not in report
    args.report.write_text(report,encoding="utf-8",newline="\n")
    shutil.copy2(args.report,delivery/"report.md")
    tracked=subprocess.check_output(["git","ls-files","-z"]).decode().split("\0")
    untracked=subprocess.check_output(["git","ls-files","--others","--exclude-standard","-z"]).decode().split("\0")
    scope=[];patch=[]
    for name in sorted(set(tracked+untracked)):
        path=root/name
        if not name or name.startswith("_patch_backups/") or not path.is_file():continue
        current=sha(path)
        if baseline.get(name)==current:continue
        scope.append(dict(path=name,before=baseline.get(name),after=current))
        before=audit/"source-before"/name
        old=before.read_text(encoding="utf-8-sig").splitlines(True) if before.exists() else []
        try:new=path.read_text(encoding="utf-8-sig").splitlines(True)
        except UnicodeError:continue
        patch.extend(difflib.unified_diff(old,new,fromfile="a/"+name,tofile="b/"+name))
    (audit/"changed-files.json").write_text(json.dumps(scope,indent=2))
    (audit/"task-only.patch").write_text("".join(patch),encoding="utf-8",newline="\n")
    (audit/"test-results-summary.json").write_text(json.dumps(dict(primary_run="full-regression-2",stats=stats,unique_cases=len(cases),final_targeted=final_stats,not_summed=True,producer_events=event_count),indent=2))
    files={str(p.relative_to(delivery)).replace("\\","/"):sha(p) for p in sorted(delivery.rglob("*")) if p.is_file() and p.name not in ("READY.json","READY.json.tmp","SHA256SUMS.txt")}
    files["../../../docs/"+args.report.name]=sha(args.report)
    (delivery/"SHA256SUMS.txt").write_text("".join(value+"  "+name+"\n" for name,value in sorted(files.items())),encoding="utf-8",newline="\n")
    assert all(sha((delivery/name).resolve())==value for name,value in files.items())
    ready=dict(status="HOST_VERIFIED_OPERATOR_UPDATE_REQUIRED",completed_at_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
               apk=str(apk),apk_sha256=sha(apk),report=str(args.report.resolve()),report_sha256=sha(args.report),
               package=str(package),package_sha256=sha(package),sha256sums=sha(delivery/"SHA256SUMS.txt"),
               tests=stats,final_targeted_tests=final_stats,source_scope_files=len(scope),protected_delivery_files=len(protected),installed=False,
               device_ui_lifecycle="NOT_VERIFIED",device_export_visibility="NOT_VERIFIED_NEW_APK_EXISTING_MTP_DISCREPANCY",experiment_start="NO_GO")
    temporary=delivery/"READY.json.tmp"
    with temporary.open("x",encoding="utf-8") as stream:
        json.dump(ready,stream,indent=2);stream.flush();os.fsync(stream.fileno())
    temporary.rename(delivery/"READY.json")
    print(json.dumps(ready,ensure_ascii=False,indent=2))


if __name__=="__main__":main()
