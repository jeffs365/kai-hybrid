#!/usr/bin/env python3
"""Build pinned sherpa JNI with text logging compiled out. Binaries stay local/ignored."""
import argparse,hashlib,json,os,shutil,subprocess,tarfile,urllib.request,zipfile
from pathlib import Path

module=Path(__file__).resolve().parents[1]
lock=json.loads((module/'native-lock.json').read_text())
p=argparse.ArgumentParser();p.add_argument('--cache',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jobs',default='6');args=p.parse_args()
def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  while chunk:=f.read(1024*1024):h.update(chunk)
 return h.hexdigest()
def fetch(url,path,expected):
 if path.exists() and digest(path)==expected:return
 path.parent.mkdir(parents=True,exist_ok=True)
 with urllib.request.urlopen(url,timeout=60) as response,path.open('wb') as out:shutil.copyfileobj(response,out)
 assert digest(path)==expected,'Pinned download integrity failed: '+path.name
commit=lock['sherpa_commit'];archive=args.cache/('sherpa-onnx-'+commit+'.tar.gz')
fetch('https://codeload.github.com/k2-fsa/sherpa-onnx/tar.gz/'+commit,archive,lock['source_archive_sha256'])
work=module/'.native-build';work.mkdir(exist_ok=True)
source=work/('sherpa-onnx-'+commit)
if not (source/'CMakeLists.txt').exists():
 with tarfile.open(archive) as t:t.extractall(work,members=[x for x in t.getmembers() if x.isfile() or x.isdir()],filter='data')
macros=source/'sherpa-onnx/csrc/macros.h'
with tarfile.open(archive) as package:
 original=package.extractfile('sherpa-onnx-'+commit+'/sherpa-onnx/csrc/macros.h').read().decode()
needle='#if __ANDROID_API__ >= 8'
assert needle in original
replacement='#if defined(KAI_KOKORO_PRIVATE_LOGS)\n#if __ANDROID_API__ >= 8\n#include "android/log.h"\n#undef __android_log_print\n#define __android_log_print(...) 0\n#endif\n// Kai offline engine: never format or emit speech text in native logs.\n#define SHERPA_ONNX_LOGE(...) do {} while (0)\n#elif __ANDROID_API__ >= 8'
patched=original.replace(needle,replacement,1)
if macros.read_text()!=patched:macros.write_text(patched)
# Restore exact pinned inputs before applying the checked-in exception/privacy patch.
with tarfile.open(archive) as package:
 for rel in ['sherpa-onnx/jni/common.h','sherpa-onnx/jni/offline-tts.cc']:
  (source/rel).write_bytes(package.extractfile('sherpa-onnx-'+commit+'/'+rel).read())
subprocess.run(['patch','--batch','-p1','-d',str(source),'-i',str(module/'tools/native-safety.patch')],check=True)
headers=work/'ort-headers';headers.mkdir(exist_ok=True)
for name,sha in lock['onnxruntime_headers'].items():
 fetch('https://raw.githubusercontent.com/microsoft/onnxruntime/v1.17.1/include/onnxruntime/core/session/'+name,headers/name,sha)
apk=args.cache/lock['publisher_apk'];assert digest(apk)==lock['publisher_apk_sha256'],'Publisher asset mismatch'
ort=work/'ort-lib';ort.mkdir(exist_ok=True)
with zipfile.ZipFile(apk) as z:(ort/'libonnxruntime.so').write_bytes(z.read('lib/arm64-v8a/libonnxruntime.so'))
build=work/'arm64';cmake=args.sdk/'cmake/3.22.1/bin/cmake';ninja=args.sdk/'cmake/3.22.1/bin/ninja';ndk=args.sdk/'ndk'/lock['ndk_version']
env=os.environ.copy();env['SHERPA_ONNXRUNTIME_INCLUDE_DIR']=str(headers);env['SHERPA_ONNXRUNTIME_LIB_DIR']=str(ort)
flags=[str(cmake),'-S',str(source),'-B',str(build),'-G','Ninja','-DCMAKE_MAKE_PROGRAM='+str(ninja),'-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),'-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-26','-DANDROID_STL=c++_static','-DCMAKE_BUILD_TYPE=Release','-DCMAKE_CXX_FLAGS=-DKAI_KOKORO_PRIVATE_LOGS=1','-DBUILD_SHARED_LIBS=OFF','-DSHERPA_ONNX_ENABLE_JNI=ON','-DSHERPA_ONNX_ENABLE_TTS=ON','-DSHERPA_ONNX_ENABLE_C_API=OFF','-DSHERPA_ONNX_ENABLE_BINARY=OFF','-DSHERPA_ONNX_ENABLE_TESTS=OFF','-DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF','-DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF','-DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF','-DBUILD_PIPER_PHONMIZE_EXE=OFF','-DBUILD_PIPER_PHONMIZE_TESTS=OFF','-DBUILD_ESPEAK_NG_EXE=OFF','-DBUILD_ESPEAK_NG_TESTS=OFF']
subprocess.run(flags,env=env,check=True)
subprocess.run([str(cmake),'--build',str(build),'--target','sherpa-onnx-jni','-j',args.jobs],env=env,check=True)
libs=list(build.rglob('libsherpa-onnx-jni.so'));assert len(libs)==1,'Unexpected JNI output'
out=module/'.native/arm64-v8a';out.mkdir(parents=True,exist_ok=True)
shutil.copy2(libs[0],out/libs[0].name);shutil.copy2(ort/'libonnxruntime.so',out/'libonnxruntime.so')
subprocess.run([str(ndk/'toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip'),'--strip-unneeded',str(out/libs[0].name)],check=True)
# Preserve the upstream JNI class layout and license; only formatting may change in the fork.
vendor=module/'src/main/java/com/k2fsa/sherpa/onnx';vendor.mkdir(parents=True,exist_ok=True)
if not (vendor/'Tts.kt').exists():shutil.copy2(source/'sherpa-onnx/kotlin-api/Tts.kt',vendor/'Tts.kt')
licenses=module/'licenses';licenses.mkdir(exist_ok=True);shutil.copy2(source/'LICENSE',licenses/'sherpa-onnx-Apache-2.0.txt')
record={'sherpa_commit':commit,'source_archive_sha256':digest(archive),'privacy_patch':'KAI_KOKORO_PRIVATE_LOGS compiles SHERPA_ONNX_LOGE to no-op; engine process also discards native stdout/stderr','native_safety_patch_sha256':digest(module/'tools/native-safety.patch'),'libraries':{x.name:{'sha256':digest(x),'bytes':x.stat().st_size} for x in out.glob('*.so')}}
(module/'.native/provenance.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps(record),flush=True)
