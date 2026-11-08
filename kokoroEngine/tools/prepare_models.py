#!/usr/bin/env python3
"""Extract only pinned local model data; no engine APK or model weights enter Git."""
import argparse,hashlib,json,shutil,zipfile
from pathlib import Path
m=Path(__file__).resolve().parents[1];lock=json.loads((m/'native-lock.json').read_text())
p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);a=p.parse_args()
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  while chunk:=f.read(1024*1024):h.update(chunk)
 return h.hexdigest()
assert sha(a.apk)==lock['publisher_apk_sha256'],'Unexpected publisher asset'
out=m/'.model-staging';out.mkdir(exist_ok=True);files=[]
prefix='assets/kokoro-multi-lang-v1_0/'
with zipfile.ZipFile(a.apk) as z:
 for info in z.infolist():
  if not info.filename.startswith(prefix) or info.is_dir():continue
  rel=info.filename[len(prefix):]
  if rel not in ('model.onnx','voices.bin','tokens.txt','lexicon-gb-en.txt','LICENSE') and not rel.startswith('espeak-ng-data/'):continue
  assert '..' not in Path(rel).parts and not Path(rel).is_absolute()
  target=out/rel;target.parent.mkdir(parents=True,exist_ok=True)
  with z.open(info) as src,target.open('wb') as dst:shutil.copyfileobj(src,dst)
  files.append({'path':rel,'bytes':target.stat().st_size,'sha256':sha(target)})
manifest={'model':'kokoro-multi-lang-v1_0','publisher_apk_sha256':lock['publisher_apk_sha256'],'speakers':{'george':26,'daniel':24,'lewis':27},'files':sorted(files,key=lambda x:x['path'])}
assets=m/'src/main/assets';assets.mkdir(parents=True,exist_ok=True)
(assets/'kokoro-files.json').write_text(json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(out/'resources.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
 for row in files:
  if row['path'] not in ('model.onnx','voices.bin'):z.write(out/row['path'],row['path'])
print('Prepared',len(files),'verified model files;',sum(x['bytes'] for x in files),'bytes; resource ZIP',(out/'resources.zip').stat().st_size,flush=True)
