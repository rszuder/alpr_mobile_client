"""Use the real Desktop archive exporter with controlled model artifacts.

ONNX graphs are executable FP32/QDQ contract probes, not trained ALPR models.
TFLite/NCNN bytes are import-contract stubs and must not be used for inference.
Run from an isolated work directory; no desktop project/session is modified.
"""
import argparse
import json
import os
from pathlib import Path
import sys
import tempfile
from unittest.mock import patch
import zipfile


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--desktop-root', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    desktop, out = args.desktop_root.resolve(), args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    sys.path.insert(0, str(desktop))
    import numpy as np
    import onnx
    from onnx import helper as h, TensorProto as T, numpy_helper
    import onnxruntime as ort
    original = Path.cwd()
    with tempfile.TemporaryDirectory(prefix='alpr-interop-') as temporary:
        os.chdir(temporary)
        try:
            from auto_annotation_tool.exporters import mobile_model_exporter as m
            def export_role(role):
                checkpoint = Path(temporary) / (role + '.pt')
                checkpoint.write_bytes(('CONTRACT FIXTURE ' + role).encode())
                req = m.MobileExportRequest(checkpoint=checkpoint, destination=out/(role+'.alprmodel'),
                    role=role, formats=('onnx','litert','ncnn'), image_size=8,
                    format_quantizations={'onnx':('fp32','int8'),'litert':('fp32',),'ncnn':('fp32',)},
                    metadata={'fixture':{'kind':'contract_probe','trained_model':False}})
                exporter = m.MobileModelExporter()
                count = 4 if role == 'plate' else 0
                info = dict(labels=[{'plate':'plate','character':'A','vehicle':'vehicle'}[role]],
                            class_count=1,keypoint_count=count,keypoint_dimensions=3 if count else 0)
                def convert(model, *, runtime, precision, package_root, **kwargs):
                    folder = package_root / 'variants' / (runtime+'_'+precision)
                    folder.mkdir(parents=True)
                    spec = dict(width=8,height=8,channels=3,layout='NHWC' if runtime=='tflite' else 'NCHW',
                                color='RGB',data_type='FLOAT32',scale=1/255,offset=0)
                    output = dict(decoder='ultralytics_pose_raw_v1' if count else 'ultralytics_detect_raw_v1',
                                  output_format='raw_yolo',box_format='xywh',nms_required=True,
                                  class_count=1,keypoint_count=count,keypoint_dimensions=3 if count else 0,
                                  has_objectness=False,tensor_layout='channels_first',normalized_coordinates=False,
                                  nms_in_graph=False,confidence_threshold=.25,iou_threshold=.45)
                    if runtime == 'onnx':
                        values=[4,4,6,2,.9]+([1,3,1,7,3,1,7,5,1,1,5,1] if count else [])
                        attributes=len(values)
                        nodes=[]; initializers=[]; source='input'
                        if precision == 'int8':
                            initializers += [numpy_helper.from_array(np.array(.01,dtype=np.float32),'scale'),
                                             numpy_helper.from_array(np.array(0,dtype=np.int8),'zero')]
                            nodes += [h.make_node('QuantizeLinear',['input','scale','zero'],['q']),
                                      h.make_node('DequantizeLinear',['q','scale','zero'],['dq'])]
                            source='dq'
                        initializers += [numpy_helper.from_array(np.array(0,dtype=np.float32),'nil'),
                                         numpy_helper.from_array(np.array(values,dtype=np.float32).reshape(1,attributes,1),'boxes')]
                        nodes += [h.make_node('ReduceMean',[source],['mean'],keepdims=0),
                                  h.make_node('Mul',['mean','nil'],['offset']),h.make_node('Add',['boxes','offset'],['output'])]
                        graph=h.make_graph(nodes,'ALPR contract probe',[h.make_tensor_value_info('input',T.FLOAT,[1,3,8,8])],
                                           [h.make_tensor_value_info('output',T.FLOAT,[1,attributes,1])],initializers)
                        model=h.make_model(graph,opset_imports=[h.make_opsetid('',13)],ir_version=8)
                        onnx.checker.check_model(model)
                        path=folder/'model.onnx'; onnx.save(model,path)
                        result=ort.InferenceSession(str(path),providers=['CPUExecutionProvider']).run(None,{'input':np.zeros((1,3,8,8),np.float32)})[0]
                        assert np.allclose(result.flatten(),values)
                        files=(path,)
                    else:
                        files=tuple(folder/name for name in (('model.tflite',) if runtime=='tflite' else ('model.param','model.bin')))
                        for f in files: f.write_bytes(b'IMPORT CONTRACT STUB - NOT AN EXECUTABLE MODEL')
                    return m.ExportedVariant(id=runtime+'-'+precision,runtime=runtime,precision=precision,files=files,
                        relative_files=tuple(f.relative_to(package_root).as_posix() for f in files),input_spec=spec,output_spec=output)
                with patch.object(exporter,'preflight',return_value=[]),patch.object(exporter,'_load_yolo_model',return_value=object()),\
                        patch.object(exporter,'_inspect_yolo_checkpoint',return_value=info),patch.object(exporter,'_export_variant',side_effect=convert):
                    return exporter.export(req)
            models={role:export_role(role) for role in ('plate','character','vehicle')}
            for name, vehicle in [('mt-mz',None),('mp-mt-mz',models['vehicle'])]:
                m.MobileAlprPackageExporter().export(m.MobileAlprPackageRequest(destination=out/(name+'.alprmodel'),
                    plate_package=models['plate'],character_package=models['character'],vehicle_package=vehicle,
                    package_id='interop-'+name,metadata={'fixture':{'kind':'contract_probe'}}))
            def corrupt(source,name,change):
                with zipfile.ZipFile(out/source) as z: entries={n:z.read(n) for n in z.namelist()}
                change(entries)
                with zipfile.ZipFile(out/name,'w',zipfile.ZIP_DEFLATED) as z:
                    for n,b in entries.items(): z.writestr(n,b)
            def wrong_hash(e):
                n=next(n for n in e if n.endswith('.onnx')); e[n]+=b'changed'
            corrupt('plate.alprmodel','bad-hash.alprmodel',wrong_hash)
            corrupt('plate.alprmodel','traversal.alprmodel',lambda e:e.update({'../outside.txt':b'bad'}))
            corrupt('plate.alprmodel','missing-file.alprmodel',lambda e:e.pop(next(n for n in e if n.endswith('.tflite'))))
            for role in ('plate','character'):
                def remove(e,role=role):
                    manifest=json.loads(e['manifest.json']); manifest['models'].pop(role)
                    e['manifest.json']=json.dumps(manifest).encode()
                corrupt('mt-mz.alprmodel','missing-'+role+'.alprmodel',remove)
            def wrong_role(e):
                manifest=json.loads(e['manifest.json']); manifest['models']['plate']['role']='vehicle'
                e['manifest.json']=json.dumps(manifest).encode()
            corrupt('mt-mz.alprmodel','wrong-role.alprmodel',wrong_role)
            with zipfile.ZipFile(out/'plate.alprmodel') as src, zipfile.ZipFile(out/'duplicate.alprmodel','w') as z:
                for n in src.namelist(): z.writestr(n,src.read(n))
                z.writestr('manifest.json',src.read('manifest.json'))
            (out/'README.txt').write_text(__doc__,encoding='utf-8')
        finally: os.chdir(original)
    print('Desktop-exported contract packages:',out)


if __name__ == '__main__': main()
