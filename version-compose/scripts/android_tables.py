"""Real Android checks for moving/reopening editable tables, pictures and 2.5 migration."""
from pathlib import Path
import subprocess,time,re,xml.etree.ElementTree as ET,struct,zlib
APP='com.pdffacil.pro';OUT=Path('verification-android');OUT.mkdir(exist_ok=True)
def adb(*args,binary=False):return subprocess.check_output(['adb',*args],text=not binary,timeout=40)
def tree():
 adb('shell','uiautomator','dump','/sdcard/tablas-ui.xml');value=adb('exec-out','cat','/sdcard/tablas-ui.xml');(OUT/'tablas-last-ui.xml').write_text(value);return ET.fromstring(value)
def find(root,label,partial=False):
 return next((n for n in root.iter('node') if any(n.attrib.get(k)==label or (partial and label in n.attrib.get(k,'')) for k in ('text','content-desc'))),None)
def screen(name): (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',binary=True))
def wait(label,partial=False,seconds=60):
 until=time.monotonic()+seconds
 while time.monotonic()<until:
  try:
   n=find(tree(),label,partial)
   if n is not None:return n
  except (subprocess.CalledProcessError,subprocess.TimeoutExpired,ET.ParseError):pass
  time.sleep(1)
 screen('error-tablas');(OUT/'tablas-logcat.txt').write_text(adb('logcat','-d','-t','250'))
 raise AssertionError('No apareció en tablas: '+label)
def bounds(n):return list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
def tap(label):
 l,t,r,b=bounds(wait(label));adb('shell','input','tap',str((l+r)//2),str((t+b)//2));time.sleep(1)
def stage(data,name):
 adb('shell','run-as',APP,'mkdir','-p','cache/visor_demo')
 subprocess.run(['adb','shell','-T','run-as',APP,'dd','of=cache/visor_demo/'+name,'bs=4096'],input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
 adb('shell','am','start','-W','-n',APP+'/.MainActivity','-a','android.intent.action.VIEW','-t','application/pdf','-d','content://'+APP+'.files/visor_demo/'+name)
 wait(name)
def save_current(filename):
 tap('Guardar')
 until=time.monotonic()+80
 while time.monotonic()<until:
  try:
   root=tree();n=next((n for n in root.iter('node') if n.attrib.get('resource-id','').endswith(':id/action_menu_save')),None)
   if n is None:n=next((n for n in root.iter('node') if 'documentsui' in n.attrib.get('package','') and n.attrib.get('text') in ('SAVE','Save','GUARDAR','Guardar')),None)
   if n is not None:
    l,t,r,b=bounds(n);adb('shell','input','tap',str((l+r)//2),str((t+b)//2));break
  except (subprocess.CalledProcessError,subprocess.TimeoutExpired,ET.ParseError):pass
  time.sleep(1)
 wait('Cambios guardados')
 data=adb('exec-out','cat','/sdcard/Download/'+filename,binary=True);assert data.startswith(b'%PDF-');return data
fixture=Path('app/build/verification/tablas-editables.pdf');assert fixture.is_file()
stage(fixture.read_bytes(),'tablas-editables.pdf')
tap('Editar');tap('Tablas');wait('Tabla 1')
l,t,r,b=bounds(wait('Mover tabla'));x=(l+r)//2;y=(t+b)//2
adb('shell','input','swipe',str(x),str(y),str(x+30),str(y+85),'800');wait('Tabla movida o redimensionada',partial=True)
screen('16-tabla-movida')
l,t,r,b=bounds(wait('Cambiar ancho de tabla'));x=(l+r)//2;y=(t+b)//2
adb('shell','input','swipe',str(x),str(y),str(x-65),str(y),'800');wait('Tabla movida o redimensionada',partial=True)
# Capture the actual persisted draft, independently checked later by PDFBox.
name=next(n for n in adb('shell','run-as',APP,'ls','files/documentos').splitlines() if n.endswith('-tablas-editables.pdf'))
(OUT/'tabla-gesto.pdf').write_bytes(adb('exec-out','run-as',APP,'cat','files/documentos/'+name,binary=True))
screen('17-tabla-ancho-cambiado')
# Tap an actual cell on the PDF, then change text and a number.
l,t,r,b=bounds(wait('Tabla 1'))
adb('shell','input','tap',str(round(l+(r-l)*0.25)),str(round(t+(b-t)*0.25)));wait('Editar tabla')
for cell,value in [('Celda 2,1','Actualizado'),('Celda 2,2','9876')]:
 tap(cell);tap('Borrar contenido');tap('Contenido de la celda');adb('shell','input','text',value);adb('shell','input','keyevent','4');time.sleep(0.5)
# Stage a genuine PNG in Android's image collection for the native content picker.
def chunk(kind,payload):return struct.pack('>I',len(payload))+kind+payload+struct.pack('>I',zlib.crc32(kind+payload)&0xffffffff)
raw=b''.join(b'\0'+b'\x16\x9a\x70'*40 for _ in range(20))
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',40,20,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
photo=OUT/'celda-tabla.png';photo.write_bytes(png)
adb('shell','mkdir','-p','/sdcard/Pictures');adb('push',str(photo),'/sdcard/Pictures/celda-tabla.png');adb('shell','am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Pictures/celda-tabla.png');time.sleep(2)
tap('Celda 1,2');tap('Imagen en celda')
try:wait('celda-tabla.png',partial=True,seconds=15)
except AssertionError:
 root=tree()
 for label in ('Show roots','Mostrar raíces','Open navigation drawer'):
  if find(root,label) is not None:tap(label);break
 for label in ('Images','Imágenes'):
  if find(tree(),label) is not None:tap(label);break
 if find(tree(),'Pictures') is not None:tap('Pictures')
wait('celda-tabla.png',partial=True)
n=wait('celda-tabla.png',partial=True);l,t,r,b=bounds(n);adb('shell','input','tap',str((l+r)//2),str((t+b)//2));wait('Quitar imagen')
screen('18-celda-texto-numero-imagen');tap('Guardar tabla');wait('Celdas de tabla actualizadas',partial=True)
tap('Autoajustar');wait('Tabla autoajustada',partial=True);screen('19-tabla-autoajustada')
tap('Ver');exported=save_current('tablas-editables-editado.pdf');(OUT/'tablas-2.6-exportadas.pdf').write_bytes(exported)
# Read the exported PDF back into the app and verify the cells/images survived.
stage(exported,'tabla-reabierta.pdf');tap('Editar');tap('Tablas');tap('Editar celdas');wait('Actualizado');tap('Celda 1,2');wait('Quitar imagen');tap('Cancelar')
# Migrate a real legacy 2.5 table on the second page, preserving its other cells.
tap('Ver');tap('Siguiente');wait('Página 2 de 2',partial=True);tap('Editar');tap('Tablas');tap('Editar celdas');wait('Legado');tap('Celda 2,2');tap('Borrar contenido');tap('Contenido de la celda');adb('shell','input','text','77');adb('shell','input','keyevent','4');tap('Guardar tabla');wait('Celdas de tabla actualizadas',partial=True)
screen('20-tabla-2.5-recuperada');tap('Ver');final=save_current('tabla-reabierta-editado.pdf');(OUT/'tablas-2.6-final.pdf').write_bytes(final)
(OUT/'RESULTADO-TABLAS.txt').write_text('OK: mover tabla completa; cambiar ancho con dedo; editar celda al tocar PDF; texto y números; imagen mediante selector Android; autoajustar; guardar/reabrir con datos e imagen; recuperar y editar tabla creada con 2.5.\n')
print('TABLAS EDITABLES ANDROID VERIFICADAS')
