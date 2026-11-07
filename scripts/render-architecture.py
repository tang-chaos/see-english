"""Render the current app architecture as an editable SVG and a shareable PNG."""
import argparse
from html import escape
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--font',type=Path,default=Path('C:/Windows/Fonts/msyh.ttc'))
parser.add_argument('--bold-font',type=Path,default=Path('C:/Windows/Fonts/msyhbd.ttc'))
args=parser.parse_args()
assert args.font.is_file() and args.bold_font.is_file(),'Supply CJK font paths.'
OUT=ROOT/'docs/diagrams';OUT.mkdir(exist_ok=True)
W,H=1080,1440
GREEN='#16634E';INK='#172C27';MUTED='#53695E';MINT='#E1EEE7';GOLD='#F5CE75'
LINE='#91B2A1';BORDER='#D7E2DA';CREAM='#F8F7F2'
image=Image.new('RGB',(W,H),CREAM);draw=ImageDraw.Draw(image)
svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="title desc">',
     '<title id="title">看见英语 0.8.5 技术架构</title>',
     '<desc id="desc">相机与相册输入分为 EfficientDet-Lite0 轻量检测和 Qwen3-VL-8B INT4 MNN CPU 模型识别，再通过 LearningStore 映射词汇、记录历史、收藏和系统离线朗读。</desc>',
     f'<rect width="{W}" height="{H}" fill="{CREAM}"/>']
def box(x,y,w,h,fill='#FFFFFF',stroke=BORDER,r=24):
    draw.rounded_rectangle((x,y,x+w,y+h),radius=r,fill=fill,outline=stroke,width=2)
    svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
def text(x,y,value,size=26,color=INK,bold=False,max_width=None):
    font=ImageFont.truetype(str(args.bold_font if bold else args.font),size)
    if max_width is not None:assert draw.textbbox((0,0),value,font=font)[2]<=max_width,(value,max_width)
    draw.text((x,y),value,font=font,fill=color)
    svg.append(f'<text x="{x}" y="{y}" dominant-baseline="text-before-edge" font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{color}">{escape(value)}</text>')
def line(points,arrow=False):
    draw.line(points,fill=LINE,width=4,joint='curve')
    svg.append(f'<polyline points="{" ".join(f"{x},{y}" for x,y in points)}" fill="none" stroke="{LINE}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>')
    if arrow:
        x,y=points[-1];p=[(x-8,y-12),(x+8,y-12),(x,y)]
        draw.polygon(p,fill=LINE)
        svg.append(f'<polygon points="{" ".join(f"{px},{py}" for px,py in p)}" fill="{LINE}"/>')

text(48,32,'看见英语｜技术怎么实现？',51,bold=True,max_width=984)
text(48,104,'Android 原生应用 · 双识别通道 · 本地学习闭环',27,MUTED,max_width=984)
box(48,158,984,44,MINT,MINT,r=18)
text(68,163,'Kotlin  /  Compose  /  CameraX  /  MediaPipe  /  MNN',24,GREEN,max_width=944)

box(64,238,952,116)
text(94,253,'图像输入',23,MUTED)
text(94,294,'CameraX 相机画面 / 拍照定格 / 相册图片',33,GREEN,True,max_width=892)
line([(540,354),(540,383),(292,383),(292,448)],True)
line([(540,383),(788,383),(788,448)],True)
text(90,401,'实时 / 定格',24,MUTED,max_width=410)
text(594,401,'用户按需触发',24,MUTED,max_width=410)

box(64,448,456,254)
box(88,470,224,44,MINT,MINT,r=15)
text(104,475,'01  轻量物体检测',25,GREEN,True,max_width=206)
text(88,535,'EfficientDet-Lite0',32,INK,True,max_width=408)
text(88,586,'MediaPipe · COCO 80 类',26,MUTED,max_width=408)
text(88,628,'输出：位置框 + 物品类别',26,GREEN,max_width=408)
text(88,668,'置信度筛选 / 去重 / 跨帧确认',22,MUTED,max_width=408)

box(560,448,456,254,'#FFF9EA')
box(584,470,224,44,GOLD,GOLD,r=15)
text(600,475,'02  照片模型识别',25,INK,True,max_width=206)
text(584,535,'Qwen3-VL-8B · INT4',29,INK,True,max_width=408)
text(584,586,'MNN CPU · 独立服务进程',26,MUTED,max_width=408)
text(584,628,'输出：英文 + 中文 JSON',26,GREEN,max_width=408)
text(584,668,'按需运行，不输出物体定位框',22,MUTED,max_width=408)

line([(292,702),(292,741),(540,741),(540,782)],True)
line([(788,702),(788,741),(540,741)])
box(64,782,952,202,GREEN,GREEN)
text(94,804,'LearningStore：状态与词汇映射',33,'#FFFFFF',True,max_width=892)
text(94,859,'内置词典 519 词条 + 自定义新词',27,'#E0EFE6',max_width=892)
text(94,902,'音标来自词典；未收录时明确提示',25,'#E0EFE6',max_width=892)
text(94,946,'词条数量 ≠ 检测模型支持的类别数量',21,'#D2E5D8',max_width=892)

line([(540,984),(540,1024),(210,1024),(210,1065)],True)
line([(540,1024),(540,1065)],True)
line([(540,1024),(870,1024),(870,1065)],True)
for x,title,detail,stack in [(64,'识别历史','自动记录 / 单词去重','SharedPreferences'),
                           (394,'生词本','手动收藏 / 自定义词','SharedPreferences'),
                           (724,'单词发音','系统离线英语语音','Android TTS')]:
    box(x,1065,292,150)
    text(x+22,1082,title,31,GREEN,True,max_width=248)
    text(x+22,1132,detail,23,MUTED,max_width=248)
    text(x+22,1174,stack,22,MUTED,max_width=248)

box(64,1255,952,122,'#FFF1D1','#FFF1D1')
text(88,1270,'本机完成识别 · 无需 API Key',29,INK,True,max_width=904)
text(88,1314,'APK 约 40MB ｜ 模型约 5.45GB，独立导入',25,MUTED,max_width=904)
text(64,1393,'当前 0.8.5：8B 识别较慢且可能误判；此图为源码架构说明。',22,MUTED,max_width=952)
svg.append('</svg>')
image.save(OUT/'see-english-architecture.png')
(OUT/'see-english-architecture.svg').write_text('\n'.join(svg),encoding='utf-8')
print('Created docs/diagrams/see-english-architecture.png and editable SVG (1080 x 1440).')
