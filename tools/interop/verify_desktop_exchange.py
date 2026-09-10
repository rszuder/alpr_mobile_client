"""Read Android-produced research archives with the actual Desktop reader/review.

Also validates crop-session semantics independently; the existing Desktop
report reader must reject that selective format until its separate adapter exists.
"""
import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import zipfile


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--desktop-root',type=Path,required=True)
    p.add_argument('--artifacts',type=Path,required=True)
    args=p.parse_args(); source=args.desktop_root.resolve(); artifacts=args.artifacts.resolve()
    schemas=Path(__file__).resolve().parents[2]/'docs'
    import jsonschema
    sys.path.insert(0,str(source))
    old=Path.cwd()
    result={}
    with tempfile.TemporaryDirectory(prefix='alpr-desktop-interop-') as temp:
        os.chdir(temp)
        try:
            from auto_annotation_tool.ranking.mobile_package_experiments import read_mobile_report_bundle
            from auto_annotation_tool.ranking.mobile_human_review import MobileReviewSession, normalize_registration
            for name,complete in [('research-complete',True),('research-partial',False),('processing-error',True)]:
                path=artifacts/(name+'.alprsession')
                before=hashlib.sha256(path.read_bytes()).hexdigest()
                bundle=read_mobile_report_bundle(path)
                assert bundle.validation.ok,bundle.validation.errors
                session=MobileReviewSession(bundle,sidecar_path=Path(temp)/(name+'.review.json'))
                assert bundle.collection_session['collection_complete']==complete
                rows=list(session.attempts.values())
                for row in rows:
                    assert row['raw_prediction']==row['prediction']
                    assert row['registration_key']==normalize_registration(row['prediction'])
                if name=='research-complete':
                    executed=[g for g in session.mt_invocations.values() if g.executed]
                    assert len(executed)==5
                    assert sorted(len(g.records) for g in executed)==[1,1,1,1,3]
                    assert any(r['mz_status']=='READ' and r['prediction']=='aaa123' for r in rows)
                    assert any(r['mz_status']=='NO_CHARACTERS' and r['prediction']=='' for r in rows)
                    assert sum(g.execution_failed for g in executed)==1
                    assert sum(g.cancelled for g in executed)==1
                    assert any(r['mt_status']=='NOT_RUN' and r['mt_detection_count']=='' for r in rows)
                    for r in rows:
                        if str(r['mt_executed']).lower()=='true': assert r['mt_input_evidence_entry'] in session.entry_names
                if name=='research-partial': assert any(r.get('mt_input_missing_evidence_reason') for r in rows)
                if name=='processing-error': assert all(not g.execution_failed for g in session.mt_invocations.values())
                assert hashlib.sha256(path.read_bytes()).hexdigest()==before
                result[name]={'desktop_reader':'passed','review_open':'passed','complete':complete,
                              'attempts':len(rows),'archive_sha256':before}
            crop=artifacts/'crop-session.zip'
            with zipfile.ZipFile(crop) as z:
                manifest=json.loads(z.read('session.json'))
                jsonschema.validate(manifest,json.loads((schemas/'alpr-crop-session-v1.schema.json').read_text(encoding='utf-8')))
                assert manifest['schema']=='alpr_crop_session_v1'
                assert manifest['normalization_policy']=='uppercase_alphanumeric.v1'
                assert manifest['capabilities']['full_mt_attempts'] is False
                assert len(manifest['crops'])==1
                group=manifest['crops'][0]
                assert group['text']==group['raw_prediction']=='aaa123'
                assert group['registration_key']=='AAA123'
                assert group['image'] in z.namelist()
                rows=group['observations']; assert len(rows)==3
                assert [r['raw_prediction'] for r in rows]==['aaa123','AAA123','AA A-123']
                assert [r['entity_id'] for r in rows]==[4,9,9]
                assert len({r['observation_id'] for r in rows})==3
                assert all(r['registration_key']==normalize_registration(r['raw_prediction']) for r in rows)
            incompatible=read_mobile_report_bundle(crop)
            assert not incompatible.validation.ok
            result['crop-session']={'reference_contract':'passed','desktop_report_adapter':'correctly_rejected',
                                    'desktop_crop_adapter':'not_present_in_inspected_desktop'}
            def bad_fixture(name,change,rehash=False):
                with zipfile.ZipFile(artifacts/'research-complete.alprsession') as z:
                    entries={n:z.read(n) for n in z.namelist()}
                change(entries)
                if rehash:
                    m=json.loads(entries['manifest.json'])
                    m['entry_sha256']={n:hashlib.sha256(b).hexdigest() for n,b in entries.items() if n!='manifest.json'}
                    entries['manifest.json']=json.dumps(m).encode()
                target=artifacts/(name+'.alprsession')
                with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
                    for n,b in entries.items(): z.writestr(n,b)
                return target
            damaged=bad_fixture('bad-hash',lambda e:e.update({'report.json':e['report.json']+b' '}))
            assert not read_mobile_report_bundle(damaged).validation.ok
            def contradict(entries):
                rows=list(csv.DictReader(io.StringIO(entries['samples/attempts.csv'].decode())))
                next(r for r in rows if r['mt_detection_count']=='3')['mt_detection_count']='2'
                out=io.StringIO(newline=''); w=csv.DictWriter(out,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
                entries['samples/attempts.csv']=out.getvalue().encode()
            contradicted=bad_fixture('bad-count',contradict,True)
            try:
                MobileReviewSession(read_mobile_report_bundle(contradicted),sidecar_path=Path(temp)/'bad.review.json')
                raise AssertionError('Contradictory invocation accepted')
            except ValueError: pass
            result['negative_fixtures']={'bad_hash':'rejected','contradictory_count':'rejected'}
        finally: os.chdir(old)
    (artifacts/'desktop-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=True,indent=2))


if __name__=='__main__': main()
