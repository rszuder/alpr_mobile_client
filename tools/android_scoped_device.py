"""ADB evidence helper: read-only snapshot; installation is a separate explicit invocation."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import tarfile
import uuid
import xml.etree.ElementTree as ET
import time

ROOT=Path(__file__).resolve().parents[1]
ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
SERIAL='R58R346GZYW'
PACKAGE='com.example.alpr_v1'


def adb(*args, check=True):
    r=subprocess.run([str(ADB),'-s',SERIAL,*args],capture_output=True)
    if check and r.returncode: raise RuntimeError(r.stderr.decode(errors='replace'))
    return r.stdout


def snapshot(directory, apk=False):
    directory.mkdir(parents=True,exist_ok=False)
    state={}
    for name, command in {
        'device': 'getprop ro.product.model; getprop ro.build.version.release; wm size; wm density; settings get system font_scale',
        'pid': 'pidof '+PACKAGE,
        'package': 'dumpsys package '+PACKAGE,
        'activity': 'dumpsys activity top',
        'session-sha256': "run-as "+PACKAGE+" sh -c 'find files/research -name session.json -exec sha256sum {} \\;'",
        'operation-files': "run-as "+PACKAGE+" find files/research/operations -type f",
        'preferences-files': 'run-as '+PACKAGE+' find shared_prefs -type f',
    }.items():
        data=adb('shell',command,check=False);(directory/(name+'.txt')).write_bytes(data)
        if name in ('device','pid'):state[name]=data.decode(errors='replace').strip()
    sessions=adb('shell','run-as '+PACKAGE+' find files/research -name session.json',check=False).decode().splitlines()
    sessions=[p.strip() for p in sessions if re.fullmatch(r'[A-Za-z0-9_./-]+',p.strip()) and p.endswith('/session.json')]
    with (directory/'preferences-and-session-metadata.tar').open('wb') as output:
        r=subprocess.run([str(ADB),'-s',SERIAL,'exec-out','run-as',PACKAGE,'tar','-cf','-','shared_prefs',*sessions],stdout=output,stderr=subprocess.PIPE)
        assert r.returncode==0,r.stderr
    with tarfile.open(directory/'preferences-and-session-metadata.tar') as archive:
        prefs={m.name:archive.extractfile(m).read().decode('utf-8') for m in archive if m.isfile() and m.name.startswith('shared_prefs/')}
        (directory/'preferences.json').write_text(json.dumps(prefs,indent=2),encoding='utf-8')
    state['session_count']=len(sessions)
    if apk:
        installed=adb('shell','pm path '+PACKAGE).decode().strip().removeprefix('package:')
        assert installed.startswith('/data/app/') and installed.endswith('/base.apk'),installed
        r=subprocess.run([str(ADB),'-s',SERIAL,'pull',installed,str(directory/'installed-before.apk')],capture_output=True)
        assert r.returncode==0,r.stderr
        state['apk_sha256']=hashlib.sha256((directory/'installed-before.apk').read_bytes()).hexdigest()
    (directory/'state.json').write_text(json.dumps(state,indent=2),encoding='utf-8')
    print(json.dumps(state))


def ui(directory,name):
    assert re.fullmatch(r'[A-Za-z0-9_-]+',name),name
    directory.mkdir(parents=True,exist_ok=True)
    image=directory/(name+'.png');layout=directory/(name+'.xml')
    assert not image.exists() and not layout.exists(),name
    remote='/data/local/tmp/alpr-scoped-'+uuid.uuid4().hex+'.xml'
    for attempt in range(3):
        result=adb('shell','uiautomator','dump',remote,check=False)
        raw=adb('exec-out','cat',remote,check=False)
        if raw.lstrip().startswith(b'<?xml'):break
        (directory/(name+'-dump-attempt-'+str(attempt)+'.txt')).write_bytes(result+raw)
        time.sleep(1)
    assert raw.lstrip().startswith(b'<?xml'),raw[:200]
    layout.write_bytes(raw)
    data=adb('exec-out','screencap','-p');assert data.startswith(b'\x89PNG');image.write_bytes(data)
    # This exact temporary evidence file was created above by this invocation.
    assert remote.startswith('/data/local/tmp/alpr-scoped-') and remote.endswith('.xml')
    adb('shell','rm',remote,check=False)
    nodes=[dict(n.attrib) for n in ET.fromstring(raw).iter('node')]
    (directory/(name+'-activity.txt')).write_bytes(adb('shell','dumpsys','activity','top'))
    print(json.dumps([{k:n.get(k,'') for k in ('text','resource-id','content-desc','bounds','checked')} for n in nodes if n.get('text') or n.get('content-desc') or n.get('resource-id')],ensure_ascii=True))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('directory');p.add_argument('--apk',action='store_true');p.add_argument('--ui');a=p.parse_args()
    if a.ui:ui(ROOT/a.directory,a.ui)
    else:snapshot(ROOT/a.directory,a.apk)
