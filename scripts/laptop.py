"""Single laptop server + the same HTTPS tunnel workflow used at ELOG Homecoming."""
from __future__ import annotations
import argparse, base64, hashlib, html, json, os, platform, re, secrets, socket
import subprocess, sys, time, urllib.request, venv, webbrowser
from string import Template
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=ROOT/'.demo-private'
ENV=ROOT/'.venv-demo'
TOOLS=ROOT/'.demo-tools'
VERSION='2026.10.3'
ASSETS={
 ('Windows','AMD64'):('cloudflared-windows-amd64.exe','76b2805253d0d6ffacb092a7acfbee99e7af6917835c0c5410e23f3ef5052da2'),
 ('Linux','x86_64'):('cloudflared-linux-amd64','053b80b0a23031a038fdbf3c073f59f82b8f1f152aa67971d7383ffafc0dbab0'),
}
def python_path(): return ENV/('Scripts/python.exe' if os.name=='nt' else 'bin/python')
def tunnel_path(): return TOOLS/('cloudflared.exe' if os.name=='nt' else 'cloudflared')
def credentials(directory=PRIVATE):
 directory.mkdir(exist_ok=True,mode=0o700)
 path=directory/'credentials.json'
 if not path.exists():
  data={'upload':secrets.token_urlsafe(48),'admin':secrets.token_urlsafe(48)}
  try:
   with os.fdopen(os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w',encoding='utf8') as f:json.dump(data,f)
  except FileExistsError:pass
 data=json.loads(path.read_text('utf8'))
 if any(not re.fullmatch('[A-Za-z0-9_-]{32,256}',data.get(k,'')) for k in ('upload','admin')) or data['upload']==data['admin']:raise RuntimeError('연결키 설정 파일이 올바르지 않습니다. 운영자에게 문의하세요.')
 return data
def child_environment(keys):
 env=os.environ.copy()
 for k in list(env):
  if k.startswith(('TUNNEL_','REFIT_VISION_')):env.pop(k)
 env.update(REFIT_VISION_TOKEN=keys['upload'],REFIT_VISION_ADMIN_TOKEN=keys['admin'],REFIT_VISION_KEEP_ORIGINALS='0',PYTHONUTF8='1')
 return env
def install_tunnel():
 if (platform.system(),platform.machine()) not in ASSETS:raise RuntimeError('자동 설치는 Windows x64 / Linux x64를 지원합니다.')
 name,digest=ASSETS[(platform.system(),platform.machine())];target=tunnel_path();TOOLS.mkdir(exist_ok=True)
 if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()==digest:return
 temp=target.with_suffix('.download')
 try:
  with urllib.request.urlopen(f'https://github.com/cloudflare/cloudflared/releases/download/{VERSION}/{name}',timeout=60) as response,temp.open('wb') as f:
   while part:=response.read(1024*1024):f.write(part)
  if hashlib.sha256(temp.read_bytes()).hexdigest()!=digest:raise RuntimeError('Cloudflare 실행 파일 무결성 검사 실패. 실행하지 않습니다.')
  temp.chmod(0o700);temp.replace(target)
 finally:temp.unlink(missing_ok=True)
def setup():
 if sys.version_info[:2] not in ((3,12),(3,13)):raise RuntimeError('64비트 Python 3.12 또는 3.13을 설치하세요.')
 if not (ROOT/'vision/models/hand_landmarker.task').is_file():raise RuntimeError('모델 파일이 없습니다. ZIP 전체를 다시 풀어주세요.')
 if not python_path().is_file():venv.EnvBuilder(with_pip=True).create(ENV)
 subprocess.run([str(python_path()),'-m','pip','install','-r',str(ROOT/'vision/requirements.txt')],check=True)
 install_tunnel();credentials();doctor()
 print('설치 완료. START_DEMO.cmd를 실행하세요.',flush=True)
def doctor():
 # Native-library failures are discovered before a public address is advertised.
 command="from measure import MODEL; from mediapipe.tasks.python import BaseOptions, vision; from brace_report import font; h=vision.HandLandmarker.create_from_options(vision.HandLandmarkerOptions(base_options=BaseOptions(model_asset_buffer=MODEL.read_bytes()),num_hands=1));h.close();p=getattr(font(20),'path',None);from pathlib import Path;assert isinstance(p,(str,bytes)) and Path(p).is_file(),'Korean font missing';print('Model and font OK')"
 subprocess.run([str(python_path()),'-c',command],cwd=ROOT/'vision',check=True,timeout=90)
def stop(proc):
 if proc and proc.poll() is None:
  proc.terminate()
  try:proc.wait(8)
  except subprocess.TimeoutExpired:proc.kill();proc.wait(5)
def health(url):
 try:
  with urllib.request.urlopen(url+'/health',timeout=3) as r:return json.loads(r.read(8192)).get('ok') is True
 except Exception:return False
def join_page(url,upload):
 import qrcode
 from io import BytesIO
 link=url+'/#key='+upload;data=BytesIO();qrcode.make(link).save(data,format='PNG')
 template=Path(__file__).with_name('join.html').read_text('utf8')
 guide=Path(__file__).resolve().parents[1]/'vision/web/guide-top.webp'
 content=Template(template).substitute(
  JOIN_LINK=html.escape(link,quote=True),
  QR_DATA='data:image/png;base64,'+base64.b64encode(data.getvalue()).decode(),
  GUIDE_DATA='data:image/webp;base64,'+base64.b64encode(guide.read_bytes()).decode(),
  SERVER_URL=html.escape(url,quote=True),UPLOAD_KEY=html.escape(upload,quote=True),
  SENSOR_LINK=html.escape(url+'/demo#key='+upload,quote=True))
 path=ROOT/'DEMO_JOIN.html';path.write_text(content,encoding='utf8');path.chmod(0o600)
 text=ROOT/'DEMO_CONNECTION.txt';text.write_text(f'HTTPS 주소: {url}\n연결키: {upload}\n갤럭시 Chrome / iPhone·iPad Bluefy\n실행 창을 닫으면 접속이 종료됩니다.\n',encoding='utf8');text.chmod(0o600)
 return path
def operator_page(url, admin):
 content=f'''<!doctype html><meta charset="utf-8"><meta name="referrer" content="no-referrer"><title>ReFit 운영자 시작</title><style>body{{font:18px system-ui;max-width:760px;margin:40px auto;padding:20px;line-height:1.8}}code{{overflow-wrap:anywhere}}</style><h1>Re:Fit 운영자 화면</h1><p><a href="{html.escape(url)}/operator" target="_blank" rel="noopener noreferrer">사용자 목록과 측정 결과 열기</a></p><p>아래 관리자 키를 복사해 운영자 화면에 입력하세요.</p><p><code>{html.escape(admin)}</code></p><p>이 파일과 관리자 키는 운영자만 보관하세요. 참가자에게는 DEMO_JOIN.html의 QR을 보여 주세요.</p><p>접수된 원본 사진과 이름·치수는 vision/out/server/participants에 저장됩니다. 운영자 화면에서 삭제할 수 있습니다.</p>'''
 path=ROOT/'DEMO_OPERATOR.html';path.write_text(content,encoding='utf8');path.chmod(0o600)
 return path
def run(local=False):
 if not python_path().exists():raise RuntimeError('INSTALL_FIRST.cmd를 먼저 실행하세요.')
 if not local:
  _,digest=ASSETS[(platform.system(),platform.machine())]
  if not tunnel_path().is_file() or hashlib.sha256(tunnel_path().read_bytes()).hexdigest()!=digest:raise RuntimeError('Cloudflare 파일을 확인할 수 없습니다. INSTALL_FIRST.cmd를 다시 실행하세요.')
 # Reject a duplicate launch before changing an active session's address files.
 port=8765
 with socket.socket() as sock:
  if sock.connect_ex(('127.0.0.1',port))==0:raise RuntimeError('이미 시연 서버가 실행 중입니다. 기존 실행 창을 확인하세요.')
 doctor();keys=credentials();env=child_environment(keys)
 logs=ROOT/'logs';logs.mkdir(exist_ok=True);server=tunnel=None
 for name in ('DEMO_CONNECTION.txt','DEMO_JOIN.html','DEMO_OPERATOR.html'):(ROOT/name).unlink(missing_ok=True)
 try:
  with (logs/'server.log').open('ab') as slog,(logs/'tunnel.log').open('w',encoding='utf8') as tlog:
   server=subprocess.Popen([str(python_path()),'-m','uvicorn','server:app','--host','127.0.0.1','--port',str(port),'--workers','1','--no-access-log','--no-proxy-headers'],cwd=ROOT/'vision',env=env,stdout=slog,stderr=subprocess.STDOUT)
   local_url=f'http://127.0.0.1:{port}'
   for _ in range(120):
    if server.poll() is not None:raise RuntimeError('측정 서버 시작 실패. logs/server.log를 확인하세요.')
    if health(local_url):break
    time.sleep(.25)
   else:raise RuntimeError('측정 서버 응답 대기 시간 초과')
   url=local_url
   if not local:
    config=TOOLS/'quick.yml';config.write_text('{}\n',encoding='utf8')
    tunnel=subprocess.Popen([str(tunnel_path()),'tunnel','--config',str(config),'--no-autoupdate','--protocol','http2','--url',local_url],cwd=ROOT,env=env,stdout=tlog,stderr=subprocess.STDOUT)
    for _ in range(150):
     if tunnel.poll() is not None or server.poll() is not None:raise RuntimeError('HTTPS 터널 연결 실패. logs/tunnel.log를 확인하세요.')
     match=re.search(r'https://[a-z0-9-]+\.trycloudflare\.com',(logs/'tunnel.log').read_text('utf8',errors='replace'))
     if match:url=match.group();break
     time.sleep(.5)
    else:raise RuntimeError('HTTPS 주소 발급 시간 초과')
   page=join_page(url,keys['upload']);operator=operator_page(url,keys['admin']);webbrowser.open(page.as_uri());webbrowser.open(operator.as_uri())
   print(f'시연 주소: {url}\n참가자 QR: DEMO_JOIN.html / 운영자 키와 화면: DEMO_OPERATOR.html\n외부 접속은 휴대폰 LTE/5G에서 직접 확인하세요.\n이 창을 닫으면 시연이 종료됩니다.',flush=True)
   if os.name=='nt':
    import ctypes
    ctypes.windll.kernel32.SetThreadExecutionState(0x80000001)
   while server.poll() is None and (local or tunnel.poll() is None):time.sleep(1)
   raise RuntimeError('서버 또는 터널이 종료됐습니다. 로그를 확인하고 다시 실행하세요.')
 finally:
  stop(tunnel);stop(server)
  for name in ('DEMO_CONNECTION.txt','DEMO_JOIN.html','DEMO_OPERATOR.html'):(ROOT/name).unlink(missing_ok=True)
  if os.name=='nt':
   import ctypes
   ctypes.windll.kernel32.SetThreadExecutionState(0x80000000)
def main():
 if hasattr(sys.stdout,'reconfigure'):sys.stdout.reconfigure(encoding='utf8')
 p=argparse.ArgumentParser();p.add_argument('action',choices=['setup','run','doctor']);p.add_argument('--local-only',action='store_true');args=p.parse_args()
 try:
  if args.action=='setup':setup()
  elif args.action=='doctor':doctor()
  else:run(args.local_only)
 except KeyboardInterrupt:print('서버와 HTTPS 터널을 종료했습니다.')
 except Exception as e:print(f'실행 중단: {e}',file=sys.stderr);return 1
 return 0
if __name__=='__main__':raise SystemExit(main())
