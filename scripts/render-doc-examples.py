"""Lay out genuine model-result replays rendered by the production Compose UI."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--font',type=Path,default=Path('C:/Windows/Fonts/msyh.ttc'))
parser.add_argument('--bold-font',type=Path,default=Path('C:/Windows/Fonts/msyhbd.ttc'))
args=parser.parse_args()
assert args.font.is_file() and args.bold_font.is_file(),'Supply paths to CJK fonts with --font and --bold-font.'
OUT=ROOT/'docs/screenshots'
OUT.mkdir(exist_ok=True)
records=[]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
for scene,title in [('desk','桌面物品，直接学英语'),('kitchen','厨房食材，直接学英语')]:
    source=ROOT/'docs/media'/f'{scene}-result.json'
    record=json.loads(source.read_text(encoding='utf-8'))
    terms=json.loads(record['output'])['objects']
    raw=ROOT/'app/build/verification'
    selected=json.loads((raw/f'doc-{scene}-selection.json').read_text(encoding='utf-8'))
    canvas=Image.new('RGB',(1080,1440),'#F8F7F2')
    draw=ImageDraw.Draw(canvas)
    def text(x,y,value,size=26,color='#172C27',bold=False):
        draw.text((x,y),value,font=ImageFont.truetype(str(args.bold_font if bold else args.font),size),fill=color)
    text(48,34,title,52,bold=True)
    text(48,104,f'识别照片 → 从识别历史打开 {selected["english"]} 词卡',27,'#53695E')
    draw.rounded_rectangle((48,154,1032,198),radius=18,fill='#E1EEE7')
    text(68,160,'真实模型输出 · APP 原生界面回放 · 非真机截图',24,'#16634E')
    for x,kind in [(48,'camera'),(562,'word')]:
        image=Image.open(raw/f'doc-{scene}-{kind}-replay.png').convert('RGB')
        image=image.resize((470,1018),Image.Resampling.LANCZOS)
        canvas.paste(image,(x,224))
        draw.rounded_rectangle((x-1,223,x+470,1242),radius=2,outline='#C8D5CC',width=2)
    text(48,1266,f'模型实际返回 {len(terms)} 类；右侧示例为 {selected["english"]}。',29,'#16634E',True)
    text(48,1312,f'电脑 CPU：加载 {record["load_seconds"]:.1f} 秒 + 推理 {record["inference_seconds"]:.1f} 秒',26,'#53695E')
    text(48,1354,'音标来自内置词典；回放不代表手机实测速度或总体准确率。',23,'#53695E')
    path=OUT/f'{scene}-app-example.png'
    canvas.save(path)
    records.append({'scene':scene,'image':path.relative_to(ROOT).as_posix(),'sha256':sha(path),
        'source_image':record['source_image'],'source_image_sha256':sha(ROOT/record['source_image']),
        'result_record':source.relative_to(ROOT).as_posix(),'result_record_sha256':sha(source),
        'returned_terms':len(terms),'selected_word':selected,'model_revision':record['model_revision'],
        'production_ui_rendered':True,'physical_device_screenshot':False,
        'renderer':'Robolectric native graphics; desktop inference replay through LearningStore callback'})
(OUT/'provenance.json').write_text(json.dumps(records,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps([{'image':r['image'],'returned_terms':r['returned_terms']} for r in records]))
