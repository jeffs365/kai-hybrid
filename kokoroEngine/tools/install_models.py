#!/usr/bin/env python3
"""Resumable, checksum-verified model setup in small USB transfers. No app data is read."""
import argparse,hashlib,json,subprocess,tempfile,zipfile
from pathlib import Path
m=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);p.add_argument('--serial',required=True);args=p.parse_args()
adb=[args.adb,'-s',args.serial];pkg='com.inspiredandroid.kai.kokoro';root='files/kokoro-v1'
manifest=json.loads((m/'src/main/assets/kokoro-files.json').read_text());rows={x['path']:x for x in manifest['files']}
def call(*a):return subprocess.run(adb+list(a),capture_output=True,text=True,check=True).stdout.strip()
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  while block:=f.read(1024*1024):h.update(block)
 return h.hexdigest()
def remote_sha(path):
 r=subprocess.run(adb+['shell','run-as',pkg,'sha256sum',path],capture_output=True,text=True)
 return r.stdout.split()[0] if r.returncode==0 else None
call('shell','run-as',pkg,'mkdir','-p',root)
for name in ('model.onnx','voices.bin'):
 row=rows[name];source=m/'.model-staging'/name
 assert sha(source)==row['sha256']
 final=root+'/'+name;part=final+'.part'
 if remote_sha(final)==row['sha256']:print(name,'already verified',flush=True);continue
 size=subprocess.run(adb+['shell','run-as',pkg,'stat','-c','%s',part],capture_output=True,text=True)
 offset=int(size.stdout.strip()) if size.returncode==0 else 0
 chunk_size=8*1024*1024
 # Interrupted append: roll back to last complete piece before resuming.
 if offset>row['bytes']:raise SystemExit('Unexpected partial file size; no truncation performed')
 if offset % chunk_size and offset!=row['bytes']:
  offset=(offset//chunk_size)*chunk_size;call('shell','run-as',pkg,'truncate','-s',str(offset),part)
 if offset:
  h=hashlib.sha256()
  with source.open('rb') as f:
   remaining=offset
   while remaining:
    block=f.read(min(1024*1024,remaining));h.update(block);remaining-=len(block)
  if remote_sha(part)!=h.hexdigest():raise SystemExit('Partial file integrity differs; no append performed')
 with source.open('rb') as f,tempfile.TemporaryDirectory(prefix='kai-kokoro-model-') as temp:
  f.seek(offset)
  while offset<row['bytes']:
   data=f.read(chunk_size);piece=Path(temp)/'piece.bin';piece.write_bytes(data)
   expected=hashlib.sha256(data).hexdigest()
   call('push',str(piece),'/data/local/tmp/kai-kokoro-piece.bin')
   actual=call('shell','sha256sum','/data/local/tmp/kai-kokoro-piece.bin').split()[0]
   assert actual==expected,'USB piece checksum mismatch'
   call('shell','run-as',pkg,'sh','-c',"'cat /data/local/tmp/kai-kokoro-piece.bin >> "+part+"'")
   offset+=len(data)
   print(name,str(offset)+'/'+str(row['bytes']),flush=True)
 assert remote_sha(part)==row['sha256'],'Complete model checksum mismatch'
 call('shell','run-as',pkg,'mv',part,final)
resources=m/'.model-staging/resources.zip'
call('push',str(resources),'/data/local/tmp/kai-kokoro-resources.zip')
call('shell','run-as',pkg,'unzip','-o','/data/local/tmp/kai-kokoro-resources.zip','-d',root)
call('shell','rm','-f','/data/local/tmp/kai-kokoro-piece.bin','/data/local/tmp/kai-kokoro-resources.zip')
print('Model bytes transferred and primary weights verified. Open engine setup and Verify offline voice files for complete per-file checks.',flush=True)
