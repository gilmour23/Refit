"""Recover the exact official model when cloning source; the delivery ZIP includes it."""
import hashlib
from pathlib import Path
import urllib.request
MODEL=Path(__file__).resolve().parents[1]/'vision/models/hand_landmarker.task'
EXPECTED='fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1'
def main():
 if MODEL.exists() and hashlib.sha256(MODEL.read_bytes()).hexdigest()==EXPECTED:return
 MODEL.parent.mkdir(exist_ok=True,parents=True)
 with urllib.request.urlopen('https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task',timeout=60) as response:
  data=response.read(12*1024*1024)
 if hashlib.sha256(data).hexdigest()!=EXPECTED:raise RuntimeError('Model checksum mismatch')
 MODEL.write_bytes(data)
if __name__=='__main__':main()
