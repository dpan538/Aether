"""Copy explicitly supplied audio without re-encoding; generate the ordered album manifest."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess
p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];dest=root/'app/src/main/assets/albums/before-the-rise';dest.mkdir(parents=True,exist_ok=True)
tracks=[
('Before_the_First_Light.mp3','Before the First Light','大锣 · 小锣 · 英国管','大锣深沉温润的低频共鸣如夜空般辽阔，小锣如繁星点点，英国管悠扬的木管音色穿梭其间，勾勒出晴夜的温暖与静谧。'),
('Glass_Garden_Portraits.mp3','Glass Garden Portraits','大提琴 · 长笛 · 粒度合成','大提琴与长笛的古典骨架交织着粒度合成与延音音效，如同一座被迷幻雾气包裹的夜间玻璃花园，沉静而深邃。'),
('Where_Water_Meets_Stone.mp3','Where Water Meets Stone','牛铃 · 铃鼓 · 木鱼','铃鼓的高频抖动如微光浮水，木鱼与牛铃错落敲击，纯粹利用三种打击乐器无缝模拟出溪水流淌的有机迷幻质感。'),
('The_Slowest_Tide.mp3','The Slowest Tide','电吉他 · 贝斯 · 中阮 · 京胡 · 法国号','模拟半梦半醒的浅睡状态，低音贝斯与中阮铺陈梦境基底，吉他延迟与远处的法国号、京胡碎片交织出模模糊糊的悬浮迷幻感。'),
('The_Sleeper’s_Lament.mp3','The Sleeper’s Lament','胡弓 · 大和琴 · 能管 · 太鼓','能剧神乐式的暗黑氛围，从深邃的梦境留白，到鬼魅显现时的太鼓狂暴爆发，再到以为惊醒却发现仍陷梦中的冷冽凝固，极具戏剧张力。'),
('Pattern_of_the_Iron_Canopy.mp3','Pattern of the Iron Canopy','军鼓 · 葫芦丝','紧凑、冰冷的军鼓节奏提供了硬核的工业推进力，与带迷幻调制的葫芦丝空灵旋律形成强烈的戏剧张力。'),
('Three_Minutes_of_Sharpened_Iron.mp3','Three Minutes of Sharpened Iron','三角铁 · 音叉','极简的金属打击乐限制，利用音叉的高频不和谐泛音与 150 BPM 的快节奏三角铁滚奏，带来极具冲击力的噪声摇滚体验。'),
('The_Last_Glowing_Knot.mp3','The Last Glowing Knot','三味线 · 嗵嗵鼓','三味线尖锐的拨弦如同火星迸发与柴火噼啪声，结合嗵嗵鼓深沉的动能，将火焰起舞徜徉的姿态表现得酣畅淋漓。'),
('November_s_Glass.mp3',"November’s Glass",'日本羽铃 · 电子琴','摒弃温柔感，以 150 BPM 的高速电子琴琶音与密集的羽铃碰撞，模拟山风掠过松针的冰冷与离别时的仓促悲伤。'),
('The_Clearing_Vision.mp3','The Clearing Vision','大和琴 · 太鼓 · 马头琴 · 阮 · 军鼓 · 大鼓 · 小鼓 · 三角铁','开篇即高潮，大和琴扫弦与欢快的太鼓律动交织，马头琴悠扬的线条描绘出梦中母亲年轻面容由模糊到清晰的温暖与史诗感。'),
('Morning’s_Sharp_Truth.mp3','Morning’s Sharp Truth','东（Tonkori）· 玄鹤琴 · 小号','梦境的终曲。东与玄鹤琴的余韵在小号嘹亮而澄澈的音色中逐渐解构、蒸发，所有幻象与潜意识都在刺眼的日光下归于平静与绝对的真实。')]
result={'schemaVersion':1,'title':'Before the Rise','subtitleZh':'幻昼·万象归寂录','conceptTitle':'Phantasmagoria: The Absolute Dissolution','artist':'Dai Pan (潘岱)','production':'使用 Google AI 辅助生成 / Created with Google AI assistance','descriptionSource':'Track descriptions and conceptual grounding supplied by Dai Pan; sequence arranged for this 11-track edition. Instrumentation and BPM are artist descriptions, not measured audio analysis.','conceptZh':'万象生于梦境，亦归于日光。由晴夜的温暖与玻璃花园，涉入浅睡、神乐梦魇与虚假觉醒；穿过金属、火焰和冰冷的离别，至梦中母亲年轻容颜的记忆回照，最终在小号刺破的晨光里归于寂静与真实。','conceptEn':'From the warmth of a clear night and a glass garden, through hypnagogic drift, ritual haunting and false awakening; across metal, fire and cold departure, toward the radiant memory of a mother’s youthful face. At last, the trumpet breaks through: hauntings and memories dissolve in daylight, returning to stillness and undeniable reality.','tracks':[]}
for i,(source,title,instruments,description) in enumerate(tracks,1):
 file=f'{i:02}-'+''.join(c.lower() if c.isalnum() else '-' for c in title).strip('-')+'.mp3'
 src=a.source/source;data=src.read_bytes();shutil.copyfile(src,dest/file)
 probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_format','-show_streams','-of','json',str(src)]));stream=next(s for s in probe['streams'] if s['codec_type']=='audio')
 result['tracks'].append({'number':i,'title':title,'file':file,'sourceFile':source,'durationMs':round(float(probe['format']['duration'])*1000),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'codec':stream['codec_name'],'sampleRate':int(stream['sample_rate']),'channels':stream['channels'],'bitrate':int(probe['format']['bit_rate']),'instruments':instruments,'description':description})
result['durationMs']=sum(t['durationMs'] for t in result['tracks']);result['audioBytes']=sum(t['bytes'] for t in result['tracks'])
result.update(json.loads((root/'album-art/before-the-rise/notes.json').read_text()))
cover=root.parent/'branding/icons/2026-10-25-symmetric-gradient/exports/J3-01.png'
shutil.copyfile(cover,dest/'cover.png')
result['cover']={'file':'cover.png','source':'branding/icons/2026-10-25-symmetric-gradient/exports/J3-01.png','sha256':hashlib.sha256(cover.read_bytes()).hexdigest(),'method':'Existing project artwork used byte-for-byte; no redrawing or recoloring.'}
(dest/'manifest.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'tracks':len(tracks),'durationMs':result['durationMs'],'bytes':result['audioBytes']}))
