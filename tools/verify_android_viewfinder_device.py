"""Read-only A12 evidence capture. No installation, settings writes, START or cleanup."""
from __future__ import annotations
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/build/android_main_viewfinder_fix_20261115_v1/device'
ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
SERIAL='R58R346GZYW'
PACKAGE='com.example.alpr_v1'

def adb(*args):
    result=subprocess.run([str(ADB),'-s',SERIAL,*args],capture_output=True,timeout=120)
    if result.returncode: raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout
def sha(data): return hashlib.sha256(data).hexdigest()
def write_json(path,value): path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def tar_entries(data):
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        return {m.name:archive.extractfile(m).read() for m in archive.getmembers() if m.isfile()}
def screenshot(name):
    OUT.mkdir(parents=True,exist_ok=True)
    path=OUT/(name+'.png');assert not path.exists(),path
    data=adb('exec-out','screencap','-p');assert data.startswith(b'\x89PNG\r\n\x1a\n');path.write_bytes(data)
    print(path)
def layout(name):
    assert re.fullmatch('[A-Za-z0-9_-]+',name),name
    OUT.mkdir(parents=True,exist_ok=True)
    path=OUT/(name+'-layout.txt');assert not path.exists(),path
    data=adb('shell','dumpsys','activity','top');path.write_bytes(data)
    views={}
    ids=('main','top_app_bar','normal_camera_hud','camera_preview','control_panel','session_operation_panel','experiment_timer_status')
    for line in data.decode(errors='replace').splitlines():
        for id in ids:
            if line.rstrip().endswith('app:id/'+id+'}'):
                rect=re.search(r' (\d+),(\d+)-(\d+),(\d+) #',line)
                if rect:views[id]={'bounds':[int(v) for v in rect.groups()],'dump':line.strip()}
    pid=adb('shell','pidof',PACKAGE).decode().strip();assert re.fullmatch(r'\d+',pid),pid
    logs=adb('logcat','-d','--pid',pid,'-v','threadtime','-s','ALPR_CAMERA_SETUP:I','Camera2CameraImpl:D','AndroidRuntime:E','*:S')
    (OUT/(name+'-camera.log')).write_bytes(logs)
    ns={'pid':pid,'views':views}
    write_json(OUT/(name+'-layout.json'),ns);print(json.dumps(ns,ensure_ascii=False))
def snapshot(name):
    folder=OUT/name;folder.mkdir(parents=True,exist_ok=False)
    prefs=adb('exec-out','run-as',PACKAGE,'tar','-cf','-','shared_prefs')
    entries=tar_entries(prefs);assert entries and all(p.startswith('shared_prefs/') for p in entries)
    (folder/'shared-prefs.tar').write_bytes(prefs)
    prefs_values={}
    for path,data in entries.items():
        root=ET.fromstring(data)
        prefs_values[path]={e.attrib['name']:{'type':e.tag,'value':e.attrib.get('value',e.text or '')} for e in root}
    ids=adb('exec-out','run-as',PACKAGE,'ls','files/research/sessions').decode().split()
    assert ids and all(re.fullmatch(r'[A-Za-z0-9_.-]+',s) for s in ids),ids
    sessions={};counts={}
    for i in range(0,len(ids),40):
        paths=['files/research/sessions/'+s+'/session.json' for s in ids[i:i+40]]
        data=adb('exec-out','run-as',PACKAGE,'tar','-cf','-',*paths)
        batch=tar_entries(data);assert set(batch)==set(paths),(set(paths)-set(batch))
        (folder/('sessions-'+str(i//40)+'.tar')).write_bytes(data)
        for path,content in batch.items():
            sessions[path]=sha(content);state=json.loads(content).get('state','UNKNOWN');counts[state]=counts.get(state,0)+1
    pm=adb('shell','pm','path',PACKAGE).decode().strip();assert pm.startswith('package:')
    apk=pm.splitlines()[0].removeprefix('package:')
    apk_sha=adb('shell','sha256sum',apk).decode().split()[0];assert re.fullmatch('[0-9a-f]{64}',apk_sha)
    info={'serial':SERIAL,'apk_sha256':apk_sha,'sessions':sessions,'session_states':counts,
          'preferences':prefs_values,'preference_sha256':{p:sha(b) for p,b in entries.items()}}
    for label,args in {'size':('shell','wm','size'),'density':('shell','wm','density'),
                       'font_scale':('shell','settings','get','system','font_scale'),
                       'blue_light_filter':('shell','settings','get','system','blue_light_filter')}.items():
        info[label]=adb(*args).decode().strip()
    write_json(folder/'state.json',info);print(json.dumps({'folder':str(folder),'sessions':len(sessions),'states':counts,'apk_sha256':apk_sha}))
def compare():
    before=json.loads((OUT/'before/state.json').read_text(encoding='utf-8'))
    after=json.loads((OUT/'after/state.json').read_text(encoding='utf-8'))
    changes={}
    for file in before['preferences'].keys()|after['preferences'].keys():
        a=before['preferences'].get(file,{});b=after['preferences'].get(file,{})
        for key in a.keys()|b.keys():
            if a.get(key)!=b.get(key):changes[file+':'+key]={'before':a.get(key),'after':b.get(key)}
    result={'session_count_before':len(before['sessions']),'session_count_after':len(after['sessions']),
            'all_session_metadata_byte_identical':before['sessions']==after['sessions'],
            'preference_changes':changes,'display_settings_unchanged':all(before[k]==after[k] for k in ('size','density','font_scale','blue_light_filter'))}
    write_json(OUT/'preservation.json',result);print(json.dumps(result,ensure_ascii=False,indent=2))
    assert result['all_session_metadata_byte_identical'] and result['display_settings_unchanged']
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('command',choices=['snapshot','screenshot','layout','compare']);parser.add_argument('name',nargs='?');args=parser.parse_args()
    if args.command=='snapshot': snapshot(args.name)
    elif args.command=='screenshot': screenshot(args.name)
    elif args.command=='layout': layout(args.name)
    else: compare()
